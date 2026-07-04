# Verification Fixes Log

## [2026-04-13] Round 1 (from opsx-apply auto-verify)

### opsx-arch-verifier

- Fixed: Timing-attack vulnerability in `Sha256OtpHasher.verify()` - replaced
  `String.equals()` with constant-time `MessageDigest.isEqual()` in
  `infrastructure/security/Sha256OtpHasher.java:29-34`

### opsx-uiux-verifier

- Fixed: Hardcoded Material color references in `ResetPasswordFragment.java` -
  replaced `design_default_color_primary` and `material_on_surface_disabled`
  with theme-resolved colors via `TypedValue.resolveAttribute()` using
  `android.R.attr.colorPrimary` and `android.R.attr.textColorSecondary`
- Fixed: No accessibility announcement for resend timer - added
  `android:accessibilityLiveRegion="polite"` to `tvResend` in
  `fragment_reset_password.xml` and added `announceForAccessibility()` call when
  timer reaches 0
- Fixed: `tvResend` touch target too small - added `android:minWidth` and
  `android:paddingHorizontal` to the view
- Fixed: Snackbar shown before navigation in `ForgotPasswordFragment` - moved
  success message to `ResetPasswordFragment.onViewCreated()` using
  `showCodeSentMessage` bundle argument
- Fixed: No `accessibilityLiveRegion` on error fields - added
  `android:accessibilityLiveRegion="polite"` to `tilEmail` (forgot password),
  `tilOtp`, `tilNewPassword`, and `tilConfirmPassword` (reset password)
- Fixed: OTP error codes not translated in `resolveValidationMessage()` - added
  cases for `OTP_EXPIRED`, `OTP_INVALID`, `OTP_ALREADY_USED`, `OTP_LOCKED`,
  `RATE_LIMIT_EXCEEDED`, `GOOGLE_ONLY_ACCOUNT`
- Fixed: Duplicate/dead string resources - removed `reset_password_resend_timer`
  and `reset_password_success` from both `values/strings.xml` and
  `values-vi/strings.xml`

### general (completeness/correctness)

- Fixed: OTP field not cleared on OTP errors - added `edtOtp.setText("")` in
  `handleResetError()` when error code is OTP-related
- Fixed: No navigation to forgot password when OTP locked - added navigation to
  `navigateUp()` when error code is `OTP_LOCKED`

## [2026-04-13] Round 2 (from opsx-verify)

### opsx-uiux-verifier

- Fixed: `tvBackToLogin` missing `minWidth` in `fragment_forgot_password.xml` -
  added `android:minWidth="@dimen/touch_target_min"` and
  `android:paddingHorizontal="@dimen/spacing_sm"` for proper 48dp touch target
- Fixed: `android.R.attr.colorPrimary` wrong namespace for Material 3 - replaced
  with `com.google.android.material.R.attr.colorPrimary` in
  `ResetPasswordFragment.java:188`
- Fixed: `showCodeSentMessage` bundle argument not declared in nav graph - added
  `<argument android:name="showCodeSentMessage" app:argType="boolean" android:defaultValue="false"/>`
  to `resetPasswordFragment` in `nav_graph_auth.xml`

### opsx-arch-verifier

- Fixed: Magic number `5` hardcoded in JPQL query instead of using
  `PasswordResetToken.MAX_ATTEMPTS` - changed `findValidByUserId()` in
  `PasswordResetTokenJpaRepository.java` to accept `maxAttempts` parameter;
  updated `PasswordResetTokenRepositoryAdapter` to pass
  `PasswordResetToken.MAX_ATTEMPTS` constant
- Fixed: Query filtered by `expiresAt > :now` which prevented returning specific
  `OTP_EXPIRED` error - renamed to `findLatestUnusedByUserId()` and removed
  expiry filter; use case now handles expiry check to return correct error code

### opsx-test-verifier

- Added: Unit tests for `PasswordResetToken` domain model -
  `PasswordResetTokenTest.java` (15 tests covering isExpired, isUsed, isLocked,
  isValid, incrementAttempts, markUsed, issue factory)
- Added: Unit tests for `RequestPasswordResetUseCase` -
  `RequestPasswordResetUseCaseTest.java` (13 tests covering happy path,
  Google-only account, email not found, rate limit exceeded)
- Added: Unit tests for `ResetPasswordUseCase` - `ResetPasswordUseCaseTest.java`
  (15 tests covering valid OTP, invalid OTP, expired OTP, already used OTP,
  locked OTP, Google-only account, user not found)
- Added: Integration tests for password reset endpoints -
  `AuthControllerIntegrationTest.java` (9 new tests in nested classes
  `ForgotPasswordEndpoint` and `ResetPasswordEndpoint`)
- Added: Unit tests for `SendPasswordResetEmailUseCase` -
  `SendPasswordResetEmailUseCaseTest.java` (2 tests)
- Added: Unit tests for `PasswordResetEmailRenderer` -
  `PasswordResetEmailRendererTest.java` (10 tests covering subject, OTP display,
  expiry notice, warning, XSS escaping, default name)
