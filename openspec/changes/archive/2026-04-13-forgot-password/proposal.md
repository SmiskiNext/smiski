# Why

The Android app's "Forgot Password" link currently shows "Coming Soon" — users
who forget their password have no way to recover their account. This is a
critical gap in the authentication flow that blocks users from accessing the
app.

## What Changes

- Add password reset flow via 6-digit OTP sent to email
- New API endpoints: `POST /auth/forgot-password` and
  `POST /auth/reset-password`
- New Android screens: ForgotPasswordFragment and ResetPasswordFragment
- Email template for OTP delivery via notification service
- Security measures: rate limiting (5/email/hr, 20/IP/hr), OTP expiry (15 min),
  single-use tokens, session revocation

## Capabilities

### New Capabilities

- `password-reset`: Password reset flow using email-based OTP verification.
  Covers OTP generation, storage, verification, rate limiting, and session
  invalidation after successful reset.

### Modified Capabilities

None. This is a new capability that integrates with existing auth infrastructure
but does not modify existing requirements.

## Impact

**Backend (user-management)**:

- New domain model: `PasswordResetToken` aggregate
- New tables: `password_reset_tokens`, `password_reset_attempts`
- New use cases: `RequestPasswordResetUseCase`, `ResetPasswordUseCase`
- Extended `AuthController` with 2 endpoints
- Extended `AuthError` with new error types

**Backend (notification)**:

- New use case: `SendPasswordResetEmailUseCase`
- New Kafka consumer for password reset events
- New email template for OTP

**Android App**:

- New fragments: `ForgotPasswordFragment`, `ResetPasswordFragment`
- New navigation actions in auth graph
- Extended `AuthRepository` with 2 methods

**Dependencies**:

- Existing: Resend API (email), Kafka (events), PostgreSQL
- No new external dependencies required
