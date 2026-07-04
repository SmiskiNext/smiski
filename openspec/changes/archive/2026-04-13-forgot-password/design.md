# Context

The Android app currently displays "Coming Soon" when users tap "Forgot
Password" on the login screen (`LoginFragment.java:156-158`). The backend has no
password reset endpoints or domain models.

**Current state:**

- Authentication: email/password login, Google Sign-In, refresh tokens
- Email infrastructure: notification service with Resend API, Kafka event-driven
- User model: supports `authProvider` ("EMAIL", "GOOGLE", "BOTH"),
  `hashedPassword` (nullable for Google-only)

**Constraints:**

- OTP-based flow (not magic link) — user enters 6-digit code in app
- Must prevent user enumeration attacks
- Must integrate with existing notification service pattern (Kafka events)
- Follow Clean Architecture patterns established in codebase

## Goals / Non-Goals

**Goals:**

- Enable password recovery for users with email/password accounts
- Secure OTP flow: 6-digit code, 15-minute expiry, single-use, rate-limited
- Consistent UX with existing auth screens (Material 3, same patterns)
- Revoke all sessions after successful password reset

**Non-Goals:**

- Password reset for Google-only accounts (they use Google Sign-In)
- Web frontend support (Android app only for this change)
- SMS-based OTP (email only)
- Admin-initiated password reset
- "Remember this device" or trusted device management

## Decisions

### 1. OTP Storage: Database with PasswordResetToken aggregate

**Decision:** Store OTP hash in PostgreSQL with a new `PasswordResetToken`
aggregate root.

**Alternatives considered:**

- **Stateless signed OTP (HMAC):** No DB writes, but cannot enforce single-use
  or revoke tokens
- **Redis with TTL:** Fast, but adds infrastructure dependency not currently in
  project

**Rationale:** Database approach matches existing `RefreshToken` pattern,
enables single-use enforcement, and requires no new infrastructure.

### 2. Rate Limiting: Database-based counting

**Decision:** Track attempts in `password_reset_attempts` table, enforce
5/email/hr + 20/IP/hr.

**Alternatives considered:**

- **Bucket4j/Resilience4j:** Requires new library, in-memory only (per-instance)
- **Redis:** Distributed but adds dependency

**Rationale:** Password reset is low-volume; DB-based counting is simple,
auditable, and sufficient. Query uses indexed columns for performance.

### 3. OTP Format: 6-digit numeric code

**Decision:** Generate 6-digit numeric OTP (000000-999999).

**Alternatives considered:**

- **8 digits:** More secure but harder to type
- **Alphanumeric:** Error-prone (0/O, 1/l confusion)

**Rationale:** 6 digits is industry standard (Google, GitHub), balances security
with usability. With 15-min expiry and 5 attempt limit, brute force is
infeasible.

### 4. Event-Driven Email: Kafka event to notification service

**Decision:** `RequestPasswordResetUseCase` publishes
`PasswordResetRequestedEvent` to Kafka via the Outbox pattern; notification
service consumes and sends email.

**Implementation note:** Inject
`org.springframework.context.ApplicationEventPublisher` (Spring's built-in) and
call `publishEvent(new PasswordResetRequestedEvent(...))`. The existing
`OutboxEventListener` (`@TransactionalEventListener`) will catch events
implementing `PublishableEvent` and persist to outbox table, then
`OutboxEventPublisher` polls and sends to Kafka. Do NOT use the domain
`EventPublisher` port directly.

**Alternatives considered:**

- **Synchronous email call:** Simpler but couples services, blocks request

**Rationale:** Follows existing pattern (MeetingInvitationsSent,
MeetingCancelled events). Decouples user-management from email delivery.

### 5. Security Response: Same response for valid/invalid emails

**Decision:** Return 200 OK with generic message regardless of whether email
exists.

**Rationale:** Prevents email enumeration attacks. Attacker cannot determine
which emails are registered.

### 6. Session Invalidation: Revoke all refresh tokens

**Decision:** After successful password reset, delete all `RefreshToken` records
for the user.

**Rationale:** Security best practice — if password was compromised, attacker's
sessions should be terminated.

### 7. Android Navigation: New fragments in auth nav graph

**Decision:** Add `ForgotPasswordFragment` and `ResetPasswordFragment` to
existing `nav_graph_auth.xml`.

**Alternatives considered:**

- **BottomSheet/Dialog:** Less screen real estate for OTP + password fields
- **Separate activity:** Unnecessary complexity

**Rationale:** Consistent with existing LoginFragment → RegisterFragment
pattern.

## Risks / Trade-offs

| Risk                                | Impact | Mitigation                                                    |
| ----------------------------------- | ------ | ------------------------------------------------------------- |
| OTP brute force                     | Medium | 5 attempt limit per OTP, 15-min expiry, rate limiting         |
| Email delivery delay                | Low    | Show "Check spam folder" hint; user can resend after 60s      |
| DB rate limit queries under load    | Low    | Indexed queries, low volume (password reset is rare)          |
| Google-only users confused          | Low    | Clear error message: "Account uses Google Sign-In"            |
| OTP interception (email compromise) | Medium | 15-min expiry limits window; session revocation limits damage |

## Database Schema

```sql
-- OTP tokens (single-use)
CREATE TABLE password_reset_tokens (
    id              UUID PRIMARY KEY,
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    otp_hash        VARCHAR(64) NOT NULL,  -- SHA-256
    expires_at      TIMESTAMPTZ NOT NULL,
    used_at         TIMESTAMPTZ,
    attempts        INT NOT NULL DEFAULT 0,  -- Wrong OTP attempts
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_prt_user_expires ON password_reset_tokens(user_id, expires_at DESC)
    WHERE used_at IS NULL;

-- Rate limiting
CREATE TABLE password_reset_attempts (
    id              BIGSERIAL PRIMARY KEY,
    email           VARCHAR(255) NOT NULL,
    ip_address      VARCHAR(45),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_pra_email_time ON password_reset_attempts(email, created_at DESC);
CREATE INDEX idx_pra_ip_time ON password_reset_attempts(ip_address, created_at DESC);
```

## API Design

```
POST /api/v1/auth/forgot-password
Request:  { "email": "user@example.com" }
Response: { "status": "success", "data": { "message": "If account exists, OTP sent" } }
          OR 429 if rate limited

POST /api/v1/auth/reset-password
Request:  { "email": "...", "otp": "482951", "newPassword": "..." }
Response: { "status": "success", "data": { "message": "Password reset successful" } }
          OR 400 with error details
```

## Component Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              ANDROID APP                                    │
├─────────────────────────────────────────────────────────────────────────────┤
│  LoginFragment ──▶ ForgotPasswordFragment ──▶ ResetPasswordFragment        │
│       │                    │                         │                      │
│       │              ViewModel                 ViewModel                    │
│       │                    │                         │                      │
│       └────────────────────┴─────────────────────────┘                      │
│                            │                                                │
│                   AuthRepository.forgotPassword()                           │
│                   AuthRepository.resetPassword()                            │
└────────────────────────────┬────────────────────────────────────────────────┘
                             │ HTTP
┌────────────────────────────▼────────────────────────────────────────────────┐
│                         USER-MANAGEMENT SERVICE                             │
├─────────────────────────────────────────────────────────────────────────────┤
│  AuthController                                                             │
│       │                                                                     │
│       ├── RequestPasswordResetUseCase                                       │
│       │       ├── RateLimiter.isAllowed()                                   │
│       │       ├── UserRepository.findByEmail()                              │
│       │       ├── OtpGenerator.generate()                                   │
│       │       ├── PasswordResetTokenRepository.save()                       │
│       │       └── EventPublisher.publish(PasswordResetRequestedEvent)       │
│       │                                                                     │
│       └── ResetPasswordUseCase                                              │
│               ├── PasswordResetTokenRepository.findValidByEmail()           │
│               ├── Verify OTP hash + attempts                                │
│               ├── PasswordHasher.hash(newPassword)                          │
│               ├── UserRepository.save() [update password]                   │
│               ├── RefreshTokenRepository.revokeAllByUserId()                │
│               └── PasswordResetTokenRepository.markUsed()                   │
└─────────────────────────────┬───────────────────────────────────────────────┘
                              │ Kafka
┌─────────────────────────────▼───────────────────────────────────────────────┐
│                         NOTIFICATION SERVICE                                │
├─────────────────────────────────────────────────────────────────────────────┤
│  PasswordResetRequestedConsumer                                             │
│       │                                                                     │
│       └── SendPasswordResetEmailUseCase                                     │
│               ├── PasswordResetEmailRenderer.render()                       │
│               └── EmailSender.send() [Resend API]                           │
└─────────────────────────────────────────────────────────────────────────────┘
```
