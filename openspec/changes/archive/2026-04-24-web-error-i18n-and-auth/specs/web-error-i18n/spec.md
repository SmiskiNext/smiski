# ADDED Requirements

## Requirement: Error code message keys in i18n files

The system SHALL define translated message keys for all API error codes in
`src/messages/en.json` and `src/messages/vi.json`. Keys SHALL mirror the 13
top-level codes and 5 violation codes from the Android `AndroidErrorTranslator`
CODE_MAP, plus general error keys and client-side validation keys.

Required top-level error keys: `INVALID_CREDENTIALS`, `EMAIL_ALREADY_EXISTS`,
`USERNAME_ALREADY_EXISTS`, `USER_DELETED`, `INVALID_FIREBASE_TOKEN`,
`FIREBASE_AUTH_ERROR`, `VALIDATION_ERROR`, `OTP_EXPIRED`, `OTP_INVALID`,
`OTP_ALREADY_USED`, `OTP_LOCKED`, `RATE_LIMIT_EXCEEDED`, `GOOGLE_ONLY_ACCOUNT`.

Required violation keys: `REQUIRED`, `INVALID_FORMAT`, `TOO_SHORT`, `TOO_LONG`,
`INVALID_VALUE`.

Required general keys: `error_server`, `error_network`, `error_unknown`,
`error_google_signin_failed`.

Required client-side validation keys: `validation_required`,
`validation_invalid_format`, `validation_passwords_mismatch`,
`validation_too_short`, `validation_too_long`, `validation_invalid_value`.

All keys SHALL be grouped under an `errors` namespace in the JSON structure.

### Scenario: English messages resolve all error codes

- **WHEN** the `WebErrorTranslator` is called with any of the 13 top-level error
  codes and locale `en`
- **THEN** a non-empty English string is returned

### Scenario: Vietnamese messages resolve all error codes

- **WHEN** the `WebErrorTranslator` is called with any of the 13 top-level error
  codes and locale `vi`
- **THEN** a non-empty Vietnamese string is returned

### Scenario: Missing error code falls back to default message

- **WHEN** the `WebErrorTranslator` is called with an unrecognised error code
- **THEN** the `defaultMessage` parameter is returned unchanged

## Requirement: Static WebErrorTranslator

The system SHALL provide a `WebErrorTranslator` module at
`src/lib/api/error-translator.ts` that implements the `ErrorTranslator` type
`(code: string, defaultMessage: string) => string`. It SHALL load message JSON
files at module initialisation time and perform a direct key lookup without
using any React hooks. It SHALL support English and Vietnamese locales. The
active locale SHALL be determined by reading the `NEXT_LOCALE` cookie or
defaulting to `en`.

### Scenario: Translator resolves top-level error code

- **WHEN** `webErrorTranslator("INVALID_CREDENTIALS", "default")` is called
- **THEN** the localised message for `INVALID_CREDENTIALS` is returned

### Scenario: Translator resolves violation code

- **WHEN** `webErrorTranslator("REQUIRED", "Field is required")` is called
- **THEN** the localised message for `REQUIRED` is returned

### Scenario: Translator used outside React component tree

- **WHEN** the JSend middleware throws an `ApiFailError` and the translator is
  invoked
- **THEN** no React hook violation occurs and a translated message is produced

## Requirement: API client initialised with translator at startup

The system SHALL call `configureApiClient(baseUrl, webErrorTranslator)` exactly
once during application startup, before any API calls are made. The base URL
SHALL be read from the `NEXT_PUBLIC_API_BASE_URL` environment variable.

### Scenario: API client configured on app load

- **WHEN** the Next.js root layout renders for the first time
- **THEN** `configureApiClient` has been called with a valid base URL and the
  `WebErrorTranslator`

### Scenario: Error response produces translated message

- **WHEN** an API call returns a JSend fail response with code
  `RATE_LIMIT_EXCEEDED`
- **THEN** the thrown `ApiFailError` carries the localised translation of
  `RATE_LIMIT_EXCEEDED`
