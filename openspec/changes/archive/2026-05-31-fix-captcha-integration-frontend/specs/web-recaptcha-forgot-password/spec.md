## ADDED Requirements

### Requirement: Auto-retry with reCAPTCHA v3 on CAPTCHA_REQUIRED

The web forgot-password hook SHALL automatically execute a reCAPTCHA v3
invisible challenge and retry the failed request when the backend returns error
code `CAPTCHA_REQUIRED`. The user MUST NOT be required to take any action for v3
resolution.

#### Scenario: First request triggers CAPTCHA_REQUIRED

- **WHEN** `submitEmail` or `resendOtp` receives an `ApiFailError` with code
  `CAPTCHA_REQUIRED`
- **THEN** the hook executes reCAPTCHA v3 with action `forgot_password`
- **AND** retries the same `forgotPassword` API call with the obtained
  `captchaToken` in the request body
- **AND** the user sees only a loading state during this process

#### Scenario: V3 auto-retry succeeds

- **WHEN** the retry with v3 token returns success
- **THEN** the flow continues normally (transitions to OTP screen or resets
  cooldown for resend)
- **AND** captcha state resets to `none`

#### Scenario: V3 auto-retry returns CAPTCHA_INVALID

- **WHEN** the retry with v3 token returns `ApiFailError` with code
  `CAPTCHA_INVALID`
- **THEN** the hook sets captcha state to `v2-required`
- **AND** the modal renders a visible reCAPTCHA v2 checkbox widget

### Requirement: reCAPTCHA v2 fallback on CAPTCHA_INVALID

The web forgot-password modal SHALL display a reCAPTCHA v2 checkbox widget when
v3 verification fails. The user MUST solve the checkbox challenge before the
request can be retried.

#### Scenario: V2 checkbox displayed after v3 failure

- **WHEN** captcha state is `v2-required`
- **THEN** a reCAPTCHA v2 checkbox widget renders inside the email screen of the
  forgot-password modal
- **AND** the submit button is disabled until the user completes the checkbox

#### Scenario: User solves v2 checkbox

- **WHEN** the user completes the reCAPTCHA v2 checkbox challenge
- **THEN** the hook receives the v2 token
- **AND** automatically retries the `forgotPassword` request with the v2 token

#### Scenario: V2 retry succeeds

- **WHEN** the retry with v2 token returns success
- **THEN** the flow continues normally
- **AND** the v2 widget is removed from the UI
- **AND** captcha state resets to `none`

### Requirement: reCAPTCHA script lazy loading

The reCAPTCHA JavaScript library SHALL be loaded dynamically on first need, not
on page load.

#### Scenario: Script not loaded until captcha needed

- **WHEN** the forgot-password modal opens and no captcha is needed
- **THEN** no reCAPTCHA script is loaded

#### Scenario: Script loaded on first CAPTCHA_REQUIRED

- **WHEN** the hook receives `CAPTCHA_REQUIRED` for the first time
- **THEN** the reCAPTCHA script (`https://www.google.com/recaptcha/api.js`) is
  injected into the document
- **AND** subsequent captcha executions reuse the loaded script

#### Scenario: Script fails to load

- **WHEN** the reCAPTCHA script fails to load (blocked, network error)
- **THEN** the hook displays a user-friendly error message indicating captcha is
  unavailable
- **AND** does NOT retry the request without a valid token

### Requirement: Site key configuration via environment variable

The web app SHALL read the reCAPTCHA site key from the
`NEXT_PUBLIC_RECAPTCHA_SITE_KEY` environment variable.

#### Scenario: Site key available

- **WHEN** `NEXT_PUBLIC_RECAPTCHA_SITE_KEY` is set in the environment
- **THEN** the `useRecaptcha` hook uses this value for all reCAPTCHA operations

#### Scenario: Site key missing

- **WHEN** `NEXT_PUBLIC_RECAPTCHA_SITE_KEY` is not set
- **THEN** captcha operations are skipped (no script loaded, no retry attempted)
- **AND** the original `CAPTCHA_REQUIRED` error message is displayed to the user

### Requirement: Captcha error message translations

The web app SHALL display localized error messages for `CAPTCHA_REQUIRED` and
`CAPTCHA_INVALID` error codes.

#### Scenario: CAPTCHA_REQUIRED error displayed (when v3 unavailable)

- **WHEN** captcha auto-retry cannot proceed (site key missing or script
  blocked)
- **AND** the error code is `CAPTCHA_REQUIRED`
- **THEN** the translated message for `CAPTCHA_REQUIRED` is shown

#### Scenario: CAPTCHA_INVALID error displayed (when v2 also fails)

- **WHEN** the v2 retry also returns `CAPTCHA_INVALID`
- **THEN** the translated message for `CAPTCHA_INVALID` is shown
