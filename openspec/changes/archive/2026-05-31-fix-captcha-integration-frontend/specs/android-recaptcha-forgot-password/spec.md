## ADDED Requirements

### Requirement: Auto-retry with reCAPTCHA on CAPTCHA_REQUIRED

The Android forgot-password ViewModel SHALL automatically trigger a reCAPTCHA
challenge and retry the request when the backend returns error code
`CAPTCHA_REQUIRED`. The user MUST NOT be required to take manual action for
invisible challenge resolution.

#### Scenario: ForgotPasswordViewModel detects CAPTCHA_REQUIRED

- **WHEN** `requestPasswordReset` receives an `ApiFailException` with code
  `CAPTCHA_REQUIRED`
- **THEN** the ViewModel posts a captcha execution event to the Fragment
- **AND** the UI remains in loading state

#### Scenario: Fragment executes SafetyNet reCAPTCHA

- **WHEN** the Fragment receives the captcha execution event
- **THEN** it calls `SafetyNet.getClient(activity).verifyWithRecaptcha(siteKey)`
- **AND** returns the token to the ViewModel on success

#### Scenario: ViewModel retries with captcha token

- **WHEN** the ViewModel receives a valid captcha token from the Fragment
- **THEN** it retries `requestPasswordReset(email, captchaToken)`
- **AND** on success, transitions to the reset password screen

#### Scenario: SafetyNet shows interactive challenge

- **WHEN** SafetyNet determines the user needs interactive verification
- **THEN** it automatically displays a challenge dialog (managed by Google Play
  Services)
- **AND** the token returned after solving is used for retry

#### Scenario: reCAPTCHA execution fails

- **WHEN** SafetyNet reCAPTCHA execution fails (Play Services unavailable,
  network error)
- **THEN** the ViewModel posts an error state with a user-friendly message
- **AND** does NOT retry without a valid token

### Requirement: Captcha token parameter in AuthRepository

The `AuthRepository.forgotPassword` method SHALL accept an optional
`captchaToken` parameter to pass to the backend API.

#### Scenario: Request without captcha token (normal flow)

- **WHEN** `forgotPassword` is called with `captchaToken = null`
- **THEN** the request is sent without the `captchaToken` field (or with null)

#### Scenario: Request with captcha token (retry flow)

- **WHEN** `forgotPassword` is called with a non-null `captchaToken`
- **THEN** the request includes `captchaToken` in the
  `UserManagementForgotPasswordRequest` body

### Requirement: RequestPasswordResetUseCase accepts captcha token

The `RequestPasswordResetUseCase.execute` method SHALL accept an optional
`captchaToken` parameter and pass it through to the repository.

#### Scenario: Execute with captcha token

- **WHEN** `execute(email, captchaToken)` is called with a non-null token
- **THEN** the token is forwarded to
  `authRepository.forgotPassword(email, captchaToken)`

#### Scenario: Execute without captcha token (backward compatible)

- **WHEN** `execute(email, null)` or `execute(email)` is called
- **THEN** the request proceeds without captcha token (existing behavior)

### Requirement: reCAPTCHA site key configuration

The Android app SHALL read the reCAPTCHA site key from BuildConfig or a resource
string.

#### Scenario: Site key available at build time

- **WHEN** the reCAPTCHA site key is configured in `build.gradle.kts` or
  `strings.xml`
- **THEN** the Fragment uses this value for SafetyNet reCAPTCHA calls

#### Scenario: Site key empty or missing

- **WHEN** the reCAPTCHA site key is empty or not configured
- **THEN** captcha execution is skipped
- **AND** the original `CAPTCHA_REQUIRED` error message is displayed
