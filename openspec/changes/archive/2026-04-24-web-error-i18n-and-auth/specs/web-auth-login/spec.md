# ADDED Requirements

## Requirement: Email and password login

The system SHALL allow users to sign in with an email address and password via
the login form in `AuthScreen`. On form submission the system SHALL call the
generated SDK `login()` function with `{email, password}`. On success the system
SHALL store the `accessToken` in a cookie named `access_token` and the
`refreshToken` in a cookie named `refresh_token`, both with `SameSite=Lax` and
`Secure` conditional on `NODE_ENV === 'production'`. After successful storage
the system SHALL navigate the user to `/workspace`.

### Scenario: Successful email login

- **WHEN** a user submits the login form with valid credentials
- **THEN** the access token and refresh token cookies are set and the user is
  redirected to `/workspace`

### Scenario: Invalid credentials error displayed inline

- **WHEN** the backend returns an `ApiFailError` with code `INVALID_CREDENTIALS`
- **THEN** a translated error message is displayed beneath the email or password
  field without page navigation

### Scenario: Validation violation mapped to field error

- **WHEN** the backend returns an `ApiFailError` with a violation on the `email`
  field
- **THEN** the translated violation message is displayed beneath the email input
  field

### Scenario: Server error displayed as banner

- **WHEN** the backend returns an `ApiError` (HTTP 5xx) or a network error
  occurs
- **THEN** a translated general error message is displayed as a form-level
  banner above the submit button

### Scenario: Submit button disabled during request

- **WHEN** a login request is in flight
- **THEN** the submit button is disabled and shows a loading indicator

## Requirement: Google Sign-In

The system SHALL allow users to sign in with their Google account via the "Sign
in with Google" button in `AuthScreen`. On button click the system SHALL call
`signInWithPopup(auth, googleProvider)` from the Firebase JS SDK. On successful
Firebase authentication the system SHALL call `getIdToken()` on the resulting
user, then call the generated SDK `googleLogin({idToken})` backend endpoint. On
success the system SHALL store tokens and navigate to `/workspace` using the
same mechanism as email login.

### Scenario: Successful Google login

- **WHEN** a user clicks "Sign in with Google" and completes the Google OAuth
  popup
- **THEN** the Firebase ID token is sent to the backend, tokens are stored in
  cookies, and the user is redirected to `/workspace`

### Scenario: Firebase popup dismissed or blocked

- **WHEN** the user closes the Google OAuth popup or the browser blocks it
- **THEN** no navigation occurs and the login page remains visible without an
  error banner (silent failure)

### Scenario: Backend rejects Firebase token

- **WHEN** the backend returns `INVALID_FIREBASE_TOKEN` or `FIREBASE_AUTH_ERROR`
- **THEN** a translated error message is displayed as a form-level banner

### Scenario: Google Sign-In SDK failure

- **WHEN** `signInWithPopup` throws an unexpected error
- **THEN** the translated `error_google_signin_failed` message is displayed as a
  form-level banner

## Requirement: Route protection via Next.js middleware

The system SHALL protect all routes matching `/workspace/*` with a Next.js
middleware check. If the `access_token` cookie is absent, the middleware SHALL
redirect the request to `/login` preserving the current locale prefix.
Authenticated requests SHALL pass through without modification. The middleware
SHALL also preserve the existing `next-intl` locale routing behaviour.

### Scenario: Unauthenticated access to workspace route

- **WHEN** a request reaches `/workspace/meetings` without an `access_token`
  cookie
- **THEN** the middleware redirects to `/[locale]/login`

### Scenario: Authenticated access to workspace route

- **WHEN** a request reaches `/workspace/meetings` with a valid `access_token`
  cookie present
- **THEN** the middleware allows the request to proceed normally

### Scenario: Login page accessible without authentication

- **WHEN** a request reaches `/login` without an `access_token` cookie
- **THEN** the middleware does not redirect, the login page renders normally

## Requirement: Firebase initialisation

The system SHALL initialise the Firebase JS SDK once per application lifecycle
in `src/lib/firebase.ts`. Firebase configuration SHALL be read from
`NEXT_PUBLIC_FIREBASE_API_KEY`, `NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN`,
`NEXT_PUBLIC_FIREBASE_PROJECT_ID`, and `NEXT_PUBLIC_FIREBASE_APP_ID` environment
variables. The module SHALL export a singleton `auth` instance and a
`GoogleAuthProvider` instance for use by the login component.

### Scenario: Firebase initialised once

- **WHEN** the Firebase module is imported multiple times across the application
- **THEN** only one Firebase app instance is created (using `getApps()` guard)

### Scenario: Missing environment variable

- **WHEN** a required `NEXT_PUBLIC_FIREBASE_*` variable is undefined at build
  time
- **THEN** the application fails fast with a clear error message identifying the
  missing variable
