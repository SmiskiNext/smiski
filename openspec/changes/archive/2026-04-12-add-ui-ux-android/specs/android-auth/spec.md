# MODIFIED Requirements

## Requirement: FieldError Record

The `FieldError` record SHALL support construction with only `field` and `code`
(no message) for client-side validation errors. When `message` is `null`, the UI
layer SHALL resolve a localized message from the `code`.

### Scenario: Code-only construction

- **WHEN** a ViewModel creates a client-side validation error
- **THEN** it SHALL use `new FieldError("email", "REQUIRED")` (2-arg
  constructor) which sets `message = null`

### Scenario: Full construction (backend)

- **WHEN** a ViewModel maps an `ApiFailException.Violation` to `FieldError`
- **THEN** it SHALL use `new FieldError(field, translatedMessage, code)` (3-arg
  constructor) preserving the already-translated message from the interceptor

### Scenario: Fragment resolves null message

- **WHEN** a Fragment receives a `FieldError` with `message == null`
- **THEN** it SHALL call a local `resolveValidationMessage(code)` method that
  maps the code to `getString(R.string.*)` (e.g., `"REQUIRED"` →
  `R.string.validation_required`)

### Scenario: Fragment uses non-null message directly

- **WHEN** a Fragment receives a `FieldError` with `message != null`
- **THEN** it SHALL display the message directly (already translated by
  `ErrorTranslator`)

## Requirement: ViewModel Error Message Handling

`LoginViewModel` and `RegisterViewModel` SHALL NOT contain hardcoded English
error message strings. Client-side validation SHALL use code-only `FieldError`.
Backend error mapping (`mapApiFail()` switch) SHALL be removed since
`ErrorTranslator` now handles translation at the interceptor level.

### Scenario: Client-side validation in LoginViewModel

- **WHEN** the user submits login with an empty email
- **THEN** `LoginViewModel` SHALL post a `UiState.Error` containing
  `UiError.Fail("VALIDATION", null, [FieldError("email", "REQUIRED")])` — no
  English message string in ViewModel code

### Scenario: Backend error pass-through in LoginViewModel

- **WHEN** `LoginUseCase` throws `ApiFailException` with code
  `INVALID_CREDENTIALS`
- **THEN** `LoginViewModel` SHALL construct
  `UiError.Fail(e.getCode(), e.getMessage(), fieldErrors)` directly — no switch
  statement to override the message (it is already translated)

### Scenario: ServerError/NetworkError/Unknown in Fragment

- **WHEN** `LoginViewModel` catches `IOException` and posts
  `UiState.Error(new UiError.NetworkError(...))`
- **THEN** `LoginFragment` SHALL display `getString(R.string.error_network)`
  regardless of the message field in `UiError.NetworkError`

### Scenario: Client-side validation in RegisterViewModel

- **WHEN** the user submits registration with mismatched passwords
- **THEN** `RegisterViewModel` SHALL post a
  `FieldError("confirmPassword", "MISMATCH")` — no English message string

### Scenario: UiError.Fail message for client-side validation

- **WHEN** client-side validation fails in a ViewModel
- **THEN** the `UiError.Fail` `message` field SHALL be `null` (Fragment resolves
  a general validation message from `R.string.error_validation` if needed for
  the general error area)
