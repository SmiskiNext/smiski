## Context

The current forgot password implementation uses a single-step OTP flow with
basic rate limiting. Security research (OWASP 2025, NIST 2025) identified
several vulnerabilities: weak rate limits enabling brute force, no CAPTCHA
protection, hard account locks creating DoS vectors, and short OTP expiry
causing user friction. The Android UI uses a single-screen approach with basic
text inputs, lacking modern UX patterns. The Web platform has no forgot password
UI.

This redesign addresses these gaps across the full stack while maintaining
backward compatibility for existing password reset tokens during the transition
period.

**Current State:**

- Backend: OTP 6-digit, 15-minute expiry, 5 req/hour email limit, 20 req/hour IP
  limit, hard lock after 5 failed OTP attempts
- Android: Single `ResetPasswordFragment` with OTP + password fields on one
  screen
- Web: No forgot password UI (placeholder link only)

**Constraints:**

- Must maintain JSend response format for API consistency
- Must follow Clean Architecture (Hexagonal) for backend
- Must follow MVVM + Clean Architecture for Android
- Must use existing Firebase/Resend infrastructure for email delivery
- Must regenerate OpenAPI spec and client SDKs after backend changes
- Cannot break existing in-flight password reset tokens (grace period required)

**Stakeholders:**

- End users: Improved security and UX
- Security team: OWASP/NIST compliance
- Mobile team: Modern Android UI patterns
- Web team: New forgot password modal
- Backend team: API contract changes

## Goals / Non-Goals

**Goals:**

- Harden password reset security to OWASP 2025 and NIST 2025 standards
- Deliver consistent, modern UX across Android and Web platforms
- Split OTP verification and password setting into separate API calls for better
  security
- Add CAPTCHA protection after 2 failed attempts
- Replace hard locks with progressive delays to prevent DoS
- Implement 2-step UI flow on Android with visual feedback (OTP boxes,
  countdown, password strength)
- Create modal-based forgot password flow for Web
- Maintain backward compatibility during transition

**Non-Goals:**

- MFA integration (future enhancement)
- Magic link alternative to OTP (keeping OTP-only)
- Email confirmation after successful reset (keeping current behavior)
- "I didn't request this" link in emails (future enhancement)
- Biometric prompt after reset on Android (future enhancement)
- SMS OTP fallback (email-only)
- Host header validation hardening (separate security review)

## Decisions

### 1. Two-Step API Flow with Temporary Token

**Decision:** Split password reset into two endpoints:
`POST /v1/auth/verify-otp` (verify OTP, return temporary token) and
`POST /v1/auth/reset-password` (consume token + new password).

**Rationale:**

- **Security:** Separates authentication (OTP verification) from authorization
  (password change). Temporary token has short TTL (5 minutes), limiting attack
  window.
- **UX:** Enables 2-step UI flow on Android/Web without coupling OTP
  verification to password entry.
- **Auditability:** Clear separation of "user verified identity" vs "user
  changed password" events.

**Alternatives Considered:**

- **A. Keep single endpoint:** Simpler but couples verification and password
  change, preventing 2-step UI flow and reducing security granularity.
- **B. Use session cookies:** More stateful, harder to implement in mobile apps,
  doesn't align with existing JWT-based auth.

**Implementation:**

- Temporary token: JWT with 5-minute expiry, signed with same secret as access
  tokens, contains `userId` and `purpose: "password-reset"` claims
- Token stored in-memory (not persisted) to avoid database overhead
- `ResetPasswordUseCase` validates token signature, expiry, and purpose before
  allowing password change
- Old `POST /v1/auth/reset-password` endpoint accepts both OTP (legacy) and
  temporary token (new) for 30-day grace period

### 2. Progressive Delays Instead of Hard Locks

**Decision:** Replace hard account lock after 5 failed OTP attempts with
progressive delays (1s → 2s → 4s → 8s → 16s).

**Rationale:**

- **OWASP 2025 Recommendation:** Hard locks enable DoS attacks (attacker can
  lock legitimate users out). Progressive delays slow brute force without
  blocking legitimate users.
- **User Experience:** Legitimate users who mistype OTP aren't permanently
  locked out.
- **Security:** Exponential backoff makes brute force impractical (16s delay
  after 5 attempts = 31s total for 5 attempts, vs instant for no delay).

**Alternatives Considered:**

- **A. Keep hard lock:** Simpler but creates DoS vector and poor UX for
  legitimate users.
- **B. CAPTCHA-only:** Doesn't slow automated attacks enough; CAPTCHA is
  additional layer, not replacement.

**Implementation:**

- Store attempt count and last attempt timestamp in `PasswordResetToken` entity
- `ResetPasswordUseCase` calculates delay:
  `Math.min(Math.pow(2, attempts - 1), 16)` seconds
- Return `429 Too Many Requests` with `Retry-After` header if delay not elapsed
- Reset attempt count on successful verification or token expiry

### 3. CAPTCHA After 2 Failed Attempts

**Decision:** Require CAPTCHA verification after 2 failed password reset
requests from same email/IP within 1 hour.

**Rationale:**

- **OWASP 2025 Best Practice:** CAPTCHA prevents automated abuse while allowing
  legitimate users to proceed.
- **Balance:** 2 attempts allows for typos without friction; 3+ attempts likely
  indicates automation or attack.
- **Cost:** CAPTCHA adds latency and UX friction, so only trigger when
  necessary.

**Alternatives Considered:**

- **A. CAPTCHA on first attempt:** Too much friction for legitimate users.
- **B. CAPTCHA after 3+ attempts:** Allows more automated attempts before
  protection kicks in.
- **C. No CAPTCHA:** Relies solely on rate limiting, which can be bypassed with
  distributed IPs.

**Implementation:**

- Use Google reCAPTCHA v3 (invisible, score-based) for minimal UX impact
- `RequestPasswordResetUseCase` checks attempt count from
  `PasswordResetAttemptJpaRepository`
- If ≥2 attempts in past hour, require `captchaToken` in request
- Verify token with Google reCAPTCHA API (score threshold: 0.5)
- Return `CAPTCHA_REQUIRED` error code if token missing or invalid

### 4. Android 2-Step State Machine in Single Fragment

**Decision:** Implement 2-step flow (Step 1: Verify OTP → Step 2: Set Password)
as state machine within `ResetPasswordFragment`, not separate fragments.

**Rationale:**

- **UX:** Smooth animated transition between steps without navigation overhead.
- **State Management:** Single ViewModel manages entire flow, avoiding state
  loss on fragment transitions.
- **Simplicity:** One fragment with conditional rendering is simpler than
  coordinating two fragments.

**Alternatives Considered:**

- **A. Two separate fragments:** More modular but adds navigation complexity and
  state management overhead.
- **B. Single screen (current):** Simpler but doesn't provide clear visual
  separation between verification and password entry.

**Implementation:**

- `ResetPasswordViewModel` has `currentStep: MutableLiveData<Step>` where `Step`
  is enum `VERIFY_OTP | SET_PASSWORD`
- `ResetPasswordFragment` observes `currentStep` and shows/hides views
  accordingly
- Step indicator (● ○ / ○ ●) updates based on `currentStep`
- Auto-transition from Step 1 to Step 2 on successful OTP verification (store
  temp token in ViewModel)

### 5. Web Modal Overlay Instead of Separate Pages

**Decision:** Implement forgot password flow as modal dialog overlaying login
page, not separate routes.

**Rationale:**

- **Modern SPA Pattern:** Keeps user in context, reduces perceived navigation
  overhead.
- **State Preservation:** Login form state preserved when modal closes.
- **Mobile-Friendly:** Modal works well on mobile without full-page transitions.

**Alternatives Considered:**

- **A. Separate pages (`/forgot-password`, `/reset-password`):** More
  traditional but adds navigation overhead and loses login context.
- **B. Inline expansion:** Expands forgot password form within login page, but
  clutters UI and harder to manage state.

**Implementation:**

- Modal component with 3 internal screens (email → OTP → password)
- Triggered by "Forgot Password?" link in login form
- Uses Radix UI Dialog primitive for accessibility (focus trap, ESC to close,
  ARIA attributes)
- State managed by `useForgotPassword` hook (email, OTP, temp token, current
  screen)
- Modal closes on successful password reset, shows success message in login form

### 6. OTP Expiry Extension to 20 Minutes

**Decision:** Increase OTP expiry from 15 to 20 minutes.

**Rationale:**

- **OWASP 2025 Optimal Balance:** 20 minutes balances security (limits token
  leakage window) with UX (accounts for email delivery delays).
- **User Feedback:** 15 minutes too short for users checking email on mobile or
  dealing with slow email delivery.
- **Security:** 5-minute extension has minimal security impact given other
  hardening measures (CAPTCHA, progressive delays, rate limiting).

**Alternatives Considered:**

- **A. Keep 15 minutes:** Simpler but causes user friction.
- **B. Extend to 30 minutes:** Better UX but increases token leakage risk.

**Implementation:**

- Change `OTP_VALIDITY` constant in `RequestPasswordResetUseCase` from
  `Duration.ofMinutes(15)` to `Duration.ofMinutes(20)`

### 7. Rate Limit Tightening

**Decision:** Reduce rate limits from 5 req/hour per email and 20 req/hour per
IP to 3 req/hour per email and 10 req/hour per IP.

**Rationale:**

- **OWASP 2025 Recommendation:** Tighter limits reduce brute force attack
  surface.
- **Legitimate Use:** 3 requests/hour sufficient for legitimate users (1
  initial + 2 retries for typos).
- **Distributed Attacks:** Lower IP limit makes distributed attacks more
  expensive.

**Alternatives Considered:**

- **A. Keep current limits:** Simpler but allows more automated attempts.
- **B. Even tighter limits (1-2 req/hour):** Too restrictive for legitimate
  users with typos.

**Implementation:**

- Change `MAX_REQUESTS_PER_EMAIL_PER_HOUR` from 5 to 3 in
  `DatabasePasswordResetRateLimiter`
- Change `MAX_REQUESTS_PER_IP_PER_HOUR` from 20 to 10 in
  `DatabasePasswordResetRateLimiter`

### 8. Server-Side Resend Cooldown Enforcement

**Decision:** Enforce 120-second cooldown between resend requests on backend,
not just client-side.

**Rationale:**

- **Security:** Client-side enforcement can be bypassed; server-side enforcement
  is authoritative.
- **Consistency:** Ensures all clients (Android, Web, API consumers) respect
  cooldown.

**Alternatives Considered:**

- **A. Client-side only:** Simpler but bypassable.
- **B. No cooldown:** Allows rapid resend spam.

**Implementation:**

- `RequestPasswordResetUseCase` checks `createdAt` of most recent token for user
- If < 120 seconds ago, return `RESEND_TOO_SOON` error with `retryAfter` seconds
- Client displays countdown timer based on `retryAfter` value

## Risks / Trade-offs

### Risk: Temporary Token Leakage

**Description:** Temporary token transmitted in HTTP response could be
intercepted. **Mitigation:**

- Use HTTPS only (already enforced)
- Short TTL (5 minutes) limits attack window
- Token single-use (invalidated after password reset)
- Token purpose-scoped (can only be used for password reset, not general auth)

### Risk: CAPTCHA Bypass

**Description:** Attackers could bypass CAPTCHA using solving services or score
manipulation. **Mitigation:**

- Use reCAPTCHA v3 score-based detection (harder to bypass than v2 checkbox)
- Set conservative score threshold (0.5)
- Combine with rate limiting and progressive delays (defense in depth)
- Monitor CAPTCHA solve rates and adjust threshold if needed

### Risk: Progressive Delay Bypass

**Description:** Attackers could reset delay by switching IPs or emails.
**Mitigation:**

- Delays tied to token ID, not just email/IP (attacker must request new token
  for each attempt)
- Rate limiting prevents rapid token generation
- CAPTCHA adds additional friction

### Risk: Android State Machine Complexity

**Description:** 2-step state machine in single fragment could be harder to
maintain than separate fragments. **Mitigation:**

- Clear state enum (`VERIFY_OTP | SET_PASSWORD`) with explicit transitions
- Comprehensive unit tests for ViewModel state transitions
- UI tests for step transitions and back button handling

### Risk: Web Modal Accessibility

**Description:** Modal could trap keyboard focus or be unusable with screen
readers. **Mitigation:**

- Use Radix UI Dialog primitive (built-in accessibility)
- Explicit focus management (auto-focus first input, return focus on close)
- ARIA labels and live regions for dynamic content
- Keyboard navigation (Tab, Shift+Tab, ESC)
- Test with NVDA/JAWS screen readers

### Risk: API Breaking Change

**Description:** Changing `POST /v1/auth/reset-password` request schema breaks
existing clients. **Mitigation:**

- 30-day grace period: endpoint accepts both OTP (legacy) and temporary token
  (new)
- Deprecation notice in API docs and response headers
- Monitor usage of legacy OTP parameter, remove after 30 days
- Version bump in OpenAPI spec (1.0 → 1.1)

### Trade-off: UX vs Security

**Description:** CAPTCHA, progressive delays, and tighter rate limits add
friction for legitimate users. **Mitigation:**

- CAPTCHA only after 2 failed attempts (most users never see it)
- Progressive delays start small (1s) and only escalate on repeated failures
- Rate limits allow 3 attempts/hour (sufficient for legitimate use)
- Clear error messages guide users through recovery

### Trade-off: Implementation Complexity

**Description:** 2-step flow, temporary tokens, and CAPTCHA add implementation
complexity. **Mitigation:**

- Comprehensive specs and design docs (this document)
- Phased rollout: backend → Android → Web
- Extensive testing at each phase
- Rollback plan: revert to single-step flow if critical issues found

## Migration Plan

### Phase 1: Backend (Week 1)

1. Add temporary token generation and validation logic
2. Create `POST /v1/auth/verify-otp` endpoint
3. Update `POST /v1/auth/reset-password` to accept both OTP (legacy) and token
   (new)
4. Add CAPTCHA verification to `POST /v1/auth/forgot-password`
5. Implement progressive delays in `ResetPasswordUseCase`
6. Update rate limits in `DatabasePasswordResetRateLimiter`
7. Add resend cooldown enforcement
8. Update OTP expiry to 20 minutes
9. Run integration tests
10. Deploy to staging, smoke test
11. Deploy to production with feature flag (disabled)

### Phase 2: Android (Week 2)

1. Regenerate API client from updated OpenAPI spec
2. Add `VerifyOtpUseCase` in domain layer
3. Update `ResetPasswordViewModel` with 2-step state machine
4. Redesign `fragment_reset_password.xml` for 2-step UI
5. Implement OTP input boxes, countdown timer, password strength meter
6. Update `ForgotPasswordFragment` icon
7. Add unit tests for ViewModel state transitions
8. Add UI tests for 2-step flow
9. Deploy to internal testing track
10. Collect feedback, iterate
11. Deploy to production

### Phase 3: Web (Week 3)

1. Regenerate API SDK from updated OpenAPI spec
2. Create `forgot-password-modal.tsx` component
3. Create `otp-input.tsx` and `password-strength-meter.tsx` components
4. Implement `useForgotPassword` hook
5. Integrate modal trigger in login form
6. Add unit tests for hook and components
7. Add E2E tests for modal flow
8. Deploy to staging
9. Deploy to production

### Phase 4: Cleanup (Week 4)

1. Monitor legacy OTP usage in `POST /v1/auth/reset-password`
2. After 30 days, remove OTP parameter support
3. Remove feature flag, enable new flow for all users
4. Archive old code paths

### Rollback Strategy

- **Backend:** Disable feature flag, revert to single-step flow
- **Android:** Revert to previous APK version via Play Console
- **Web:** Revert deployment via Vercel rollback
- **Database:** No schema changes, rollback is non-destructive

### Monitoring

- Track CAPTCHA solve rates and false positive rate
- Monitor progressive delay trigger frequency
- Track OTP expiry rate (should decrease with 20-minute window)
- Monitor API error rates for new endpoints
- Track user completion rate for 2-step flow vs old flow
