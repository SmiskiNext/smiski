## Why

The current forgot password implementation has security gaps identified in OWASP
2025 and NIST 2025 guidelines (weak rate limiting, no CAPTCHA protection, hard
account locks enabling DoS attacks, short OTP expiry causing user friction).
Additionally, the Android UI lacks modern UX patterns (no separate OTP input
boxes, no password strength feedback, no expiry countdown), and the Web platform
has no forgot password UI at all. This redesign addresses these security
vulnerabilities while delivering a consistent, user-friendly experience across
all platforms.

## What Changes

- **Backend Security Hardening**:
    - Increase OTP expiry from 15 to 20 minutes (OWASP optimal balance)
    - Strengthen rate limiting: 5→3 requests/hour per email, 20→10 per IP
    - Add CAPTCHA verification after 2 failed attempts
    - Replace hard account locks with progressive delays (1s → 2s → 4s → 8s) to
      prevent DoS
    - Enforce 120-second resend cooldown server-side
    - Split password reset into two API endpoints: `POST /v1/auth/verify-otp`
      (returns temporary token) and `POST /v1/auth/reset-password` (consumes
      token + new password)

- **Android UI/UX Redesign**:
    - Redesign `ResetPasswordFragment` with 2-step state machine (Step 1: Verify
      OTP → Step 2: Set Password)
    - Implement 6 separate OTP input boxes with auto-focus
    - Add live countdown timer showing OTP expiry ("Expires in: MM:SS")
    - Add password strength meter (Weak/Fair/Good/Strong with color coding)
    - Add step indicator (● ○ / ○ ●)
    - Replace placeholder icon with proper mail icon in `ForgotPasswordFragment`

- **Web Implementation**:
    - Create modal-based forgot password flow (email → OTP → password)
      overlaying login page
    - Implement reusable OTP input component (6 boxes)
    - Implement password strength meter component
    - Add state management hook for forgot password flow
    - Regenerate API SDK after backend changes

- **Cross-Platform Consistency**:
    - Unified error handling (OTP expired, invalid, locked, rate limited)
    - Consistent component states (Idle, Typing, Error, Success, Loading)
    - Accessibility compliance (WCAG 2.1 AA: ARIA labels, keyboard navigation,
      screen reader support)

## Capabilities

### New Capabilities

- `password-reset-captcha`: CAPTCHA verification for password reset requests to
  prevent automated abuse
- `password-reset-progressive-delays`: Progressive delay mechanism for OTP
  verification failures instead of hard locks
- `password-reset-two-step-verification`: Split OTP verification and password
  setting into separate API calls with temporary token exchange
- `forgot-password-web-ui`: Complete forgot password modal flow for web platform
- `forgot-password-android-redesign`: Enhanced Android UI with 2-step state
  machine, OTP boxes, countdown timer, and password strength meter

### Modified Capabilities

- `password-reset-rate-limiting`: Tightened rate limits (3 req/hour per email,
  10 per IP) and added server-side resend cooldown enforcement
- `password-reset-otp-expiry`: Extended OTP validity from 15 to 20 minutes

## Impact

**Backend**:

- `../../../../services/user-management/src/main/java/io/github/smiskinext/usermanagement/application/usecase/RequestPasswordResetUseCase.java` -
  OTP expiry change
- `../../../../services/user-management/src/main/java/io/github/smiskinext/usermanagement/application/usecase/ResetPasswordUseCase.java` -
  Progressive delays logic
- `../../../../services/user-management/src/main/java/io/github/smiskinext/usermanagement/infrastructure/security/DatabasePasswordResetRateLimiter.java` -
  Rate limit values
- `../../../../services/user-management/src/main/java/io/github/smiskinext/usermanagement/presentation/AuthController.java` -
  New verify-otp endpoint + CAPTCHA verification
- Domain/infrastructure layers for temporary token management and CAPTCHA
  integration

**Android**:

- `frontends/android-app/app/src/main/res/layout/fragment_forgot_password.xml` -
  Icon update
- `frontends/android-app/app/src/main/res/layout/fragment_reset_password.xml` -
  Complete redesign for 2-step UI
- `frontends/android-app/app/src/main/java/io/github/phunguy65/zms/presentation/auth/forgotpassword/ResetPasswordFragment.java` -
  State machine logic
- `frontends/android-app/app/src/main/java/io/github/phunguy65/zms/presentation/auth/forgotpassword/ResetPasswordViewModel.java` -
  Two separate API calls
- Domain/data layers for new API integration

**Web**:

- `frontends/web/src/components/auth/forgot-password-modal.tsx` - New modal
  component
- `frontends/web/src/components/auth/otp-input.tsx` - New OTP input component
- `frontends/web/src/components/auth/password-strength-meter.tsx` - New strength
  meter component
- `frontends/web/src/hooks/use-forgot-password.ts` - New state management hook
- `frontends/web/src/components/auth/form.tsx` - Modal trigger integration
- `frontends/web/src/generated/` - Regenerated API SDK

**API Changes**:

- **BREAKING**: `POST /v1/auth/reset-password` request schema changes (now
  requires temporary token instead of OTP)
- **NEW**: `POST /v1/auth/verify-otp` endpoint for OTP verification

**Testing**:

- New unit tests for rate limiting, progressive delays, CAPTCHA verification
- New integration tests for 2-step flow on Android and Web
- Updated E2E tests for full forgot password flow across platforms
