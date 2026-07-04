# ADDED Requirements

## Requirement: English String Resources for Auth Flow

All user-visible text in the auth flow (Login and Register screens) SHALL be
defined as named string resources in `res/values/strings.xml`. No XML layout or
Java source file in the auth flow SHALL contain hardcoded English strings for
user-visible text (labels, hints, button text, error messages, content
descriptions).

### Scenario: Login screen strings extracted

- **WHEN** `fragment_login.xml` is inspected
- **THEN** every `android:text`, `android:hint`, and
  `android:contentDescription` attribute SHALL reference a `@string/` resource
  (e.g., `@string/login_title`, `@string/login_hint_email`,
  `@string/navigate_up`)

### Scenario: Register screen strings extracted

- **WHEN** `fragment_register.xml` is inspected
- **THEN** every `android:text`, `android:hint`, and
  `android:contentDescription` attribute SHALL reference a `@string/` resource

### Scenario: Fragment Java code strings extracted

- **WHEN** `LoginFragment.java` and `RegisterFragment.java` are inspected
- **THEN** all `setText()`, `Toast.makeText()`, and `showGeneralError()` calls
  SHALL use `getString(R.string.*)` or `R.string.*` instead of hardcoded strings

## Requirement: Vietnamese Translation

A Vietnamese string resource file (`res/values-vi/strings.xml`) SHALL provide
translations for all string resources defined for the auth flow.

### Scenario: Vietnamese locale active

- **WHEN** the device locale is set to Vietnamese (`vi`)
- **THEN** all auth flow text (login title, register title, button labels,
  hints, error messages, validation messages, backend error translations) SHALL
  display in Vietnamese

### Scenario: Fallback to English

- **WHEN** the device locale is set to a language without a translation file
  (e.g., French)
- **THEN** the app SHALL fall back to the default English strings in
  `res/values/strings.xml`

## Requirement: AndroidErrorTranslator Implementation

An `AndroidErrorTranslator` class SHALL implement the `ErrorTranslator`
interface and translate machine-readable error codes to locale-specific messages
using Android string resources.

### Scenario: Top-level error code translation

- **WHEN** the backend returns a JSend fail response with code
  `INVALID_CREDENTIALS`
- **THEN**
  `AndroidErrorTranslator.translate("INVALID_CREDENTIALS", "Invalid credentials")`
  SHALL return the localized string from `R.string.error_invalid_credentials`
  (e.g., "Email or password is incorrect" in English, "Email hoac mat khau khong
  dung" in Vietnamese)

### Scenario: Violation code translation

- **WHEN** the backend returns a field violation with code `REQUIRED`
- **THEN** `AndroidErrorTranslator.translate("REQUIRED", "must not be blank")`
  SHALL return the localized string from `R.string.validation_required`

### Scenario: Unknown code fallback

- **WHEN** `translate()` is called with a code not in the map (e.g., a new
  backend code)
- **THEN** the method SHALL return `defaultMessage` unchanged

### Scenario: Supported error codes

- **WHEN** `AndroidErrorTranslator` is initialized
- **THEN** the code map SHALL include at minimum: `INVALID_CREDENTIALS`,
  `EMAIL_ALREADY_EXISTS`, `USERNAME_ALREADY_EXISTS`, `USER_DELETED`,
  `INVALID_FIREBASE_TOKEN`, `FIREBASE_AUTH_ERROR`, `VALIDATION_ERROR`,
  `REQUIRED`, `INVALID_FORMAT`, `TOO_SHORT`, `TOO_LONG`, `INVALID_VALUE`

## Requirement: Interceptor Violation Translation

`JsendUnwrapInterceptor.handleFail()` SHALL translate field-level violation
messages through the `ErrorTranslator`, not only the top-level error code
message.

### Scenario: Violation message translated at interceptor

- **WHEN** the backend returns a fail response with violations
  `[{field: "email", message: "must not be blank", code: "REQUIRED"}]`
- **THEN** the `ApiFailException` thrown SHALL contain a `Violation` with
  `message` equal to `translator.translate("REQUIRED", "must not be blank")`
  (the locale-specific translation)

### Scenario: Unknown violation code falls back

- **WHEN** a violation has an unrecognized code (e.g., `CUSTOM_RULE`)
- **THEN** the violation message SHALL be the backend's original message
  (returned by `translator.translate("CUSTOM_RULE", originalMessage)`)

## Requirement: NetworkModule Provides AndroidErrorTranslator

`NetworkModule` SHALL provide `AndroidErrorTranslator` as the `ErrorTranslator`
implementation instead of `ErrorTranslator.DEFAULT`.

### Scenario: Translator wired via Hilt

- **WHEN** the app starts and Hilt initializes `NetworkModule`
- **THEN** the `ErrorTranslator` provided to `JsendUnwrapInterceptor` SHALL be
  an instance of `AndroidErrorTranslator` (not the pass-through default)
