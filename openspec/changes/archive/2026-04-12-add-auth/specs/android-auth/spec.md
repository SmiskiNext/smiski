# ADDED Requirements

## Requirement: Email/password login

The Android app SHALL allow users to log in with email and password. The login
screen SHALL present email and password input fields and a "Sign In" button.
When the user submits valid credentials, the app SHALL call
`POST /api/v1/auth/login` with `{ email, password }`, store the returned
`accessToken` and `refreshToken` in `EncryptedSharedPreferences`, and navigate
to `DashboardActivity`.

### Scenario: Successful email/password login

- **WHEN** the user enters a valid email and password and taps "Sign In"
- **THEN** the app SHALL show a loading indicator on the button, call the login
  API, store `accessToken` and `refreshToken` via `TokenManager`, and navigate
  to `DashboardActivity` with
  `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK`

### Scenario: Login with invalid credentials

- **WHEN** the user enters an incorrect email or password and taps "Sign In"
- **THEN** the app SHALL display the error message "Invalid email or password"
  below the Sign In button (general error area) and SHALL NOT navigate away

### Scenario: Login with empty email field

- **WHEN** the user leaves the email field empty and taps "Sign In"
- **THEN** the app SHALL display "Email is required" as an inline error on the
  email `TextInputLayout` via `setError()` and SHALL NOT call the API

### Scenario: Login with empty password field

- **WHEN** the user leaves the password field empty and taps "Sign In"
- **THEN** the app SHALL display "Password is required" as an inline error on
  the password `TextInputLayout` via `setError()` and SHALL NOT call the API

### Scenario: Login with invalid email format

- **WHEN** the user enters a malformed email (not matching
  `Patterns.EMAIL_ADDRESS`) and taps "Sign In"
- **THEN** the app SHALL display "Invalid email format" as an inline error on
  the email `TextInputLayout` and SHALL NOT call the API

### Scenario: Login for a deleted account

- **WHEN** the backend returns `USER_DELETED` (HTTP 401)
- **THEN** the app SHALL display "This account has been deleted" as a general
  error below the Sign In button

### Scenario: Login with no network connection

- **WHEN** the device has no network connectivity and the user taps "Sign In"
- **THEN** the app SHALL display "No internet connection. Please check your
  network and try again." as a general error below the Sign In button

### Scenario: Login during server outage

- **WHEN** the backend returns an HTTP 5xx error
- **THEN** the app SHALL display "Something went wrong. Please try again later."
  as a general error below the Sign In button

### Scenario: Double-tap prevention during login

- **WHEN** the user taps "Sign In" while a login request is already in progress
- **THEN** the Sign In button SHALL be disabled and show a circular
  `ProgressBar` replacing the button text until the request completes

## Requirement: Email/password registration

The Android app SHALL allow users to create a new account with full name,
username, email, password, and password confirmation. The register screen SHALL
present these input fields, a terms checkbox, and a "Create Account" button.
When the user submits valid data, the app SHALL call
`POST /api/v1/auth/register` with `{ fullName, username, email, password }` and
navigate to the login screen.

### Scenario: Successful registration

- **WHEN** the user fills all fields correctly, checks the terms checkbox, and
  taps "Create Account"
- **THEN** the app SHALL show a loading indicator, call the register API, and
  upon success navigate to `LoginFragment` within the same `AuthActivity`

### Scenario: Registration with existing email

- **WHEN** the backend returns `EMAIL_ALREADY_EXISTS` (HTTP 409)
- **THEN** the app SHALL display "Email address is already in use" as an inline
  error on the email `TextInputLayout`

### Scenario: Registration with existing username

- **WHEN** the backend returns `USERNAME_ALREADY_EXISTS` (HTTP 409)
- **THEN** the app SHALL display "Username is already taken" as an inline error
  on the username `TextInputLayout`

### Scenario: Registration with password mismatch

- **WHEN** the confirm password field does not match the password field
- **THEN** the app SHALL display "Passwords do not match" as an inline error on
  the confirm password `TextInputLayout` and SHALL NOT call the API

### Scenario: Registration with empty required fields

- **WHEN** any required field (fullName, username, email, password,
  confirmPassword) is empty and the user taps "Create Account"
- **THEN** the app SHALL display "[Field name] is required" as inline errors on
  each empty field's `TextInputLayout` and SHALL NOT call the API

### Scenario: Registration without accepting terms

- **WHEN** the user taps "Create Account" without checking the terms checkbox
- **THEN** the app SHALL display "You must agree to the Terms of Service and
  Privacy Policy" as a general error and SHALL NOT call the API

### Scenario: Registration with no network connection

- **WHEN** the device has no network connectivity and the user taps "Create
  Account"
- **THEN** the app SHALL display "No internet connection. Please check your
  network and try again." as a general error

## Requirement: Google Sign-In

The Android app SHALL allow users to sign in with their Google account via the
login screen. The login screen SHALL display a "Google" button in the "OR
CONTINUE WITH" section. Tapping it SHALL launch Credential Manager to select a
Google account, authenticate via Firebase Auth SDK to obtain a Firebase ID
token, then call `POST /api/v1/auth/google-login` with `{ idToken }`, store the
returned tokens, and navigate to Dashboard.

### Scenario: Successful Google Sign-In (new user)

- **WHEN** the user taps "Google", selects a Google account, and the backend
  creates a new user
- **THEN** the app SHALL store `accessToken` and `refreshToken` via
  `TokenManager` and navigate to `DashboardActivity` with
  `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK`

### Scenario: Successful Google Sign-In (existing user)

- **WHEN** the user taps "Google", selects a Google account linked to an
  existing user
- **THEN** the app SHALL store the new tokens via `TokenManager` and navigate to
  `DashboardActivity`

### Scenario: User cancels Google Sign-In

- **WHEN** the user dismisses the Google account picker without selecting an
  account
- **THEN** the app SHALL silently return to the login screen without showing any
  error

### Scenario: Invalid Firebase token

- **WHEN** the backend returns `INVALID_FIREBASE_TOKEN` (HTTP 401)
- **THEN** the app SHALL display "Google sign-in failed. Please try again." as a
  general error below the social login buttons

### Scenario: Firebase service unavailable

- **WHEN** the backend returns `FIREBASE_AUTH_ERROR` (HTTP 503)
- **THEN** the app SHALL display "Service unavailable. Please try again later."
  as a general error

### Scenario: Google Sign-In with no network

- **WHEN** the device has no network and the user taps "Google"
- **THEN** the app SHALL display "No internet connection. Please check your
  network and try again." as a general error

## Requirement: Token persistence

The Android app SHALL persist authentication tokens using
`EncryptedSharedPreferences`. A `TokenManager` class SHALL provide methods to
save, read, and clear `accessToken` and `refreshToken`.

### Scenario: Tokens saved after successful login

- **WHEN** a login (email/password or Google) succeeds and the API returns
  `accessToken` and `refreshToken`
- **THEN** `TokenManager` SHALL store both tokens in
  `EncryptedSharedPreferences` using AES256-GCM encryption

### Scenario: Tokens cleared on logout

- **WHEN** a logout operation is triggered
- **THEN** `TokenManager` SHALL remove both `accessToken` and `refreshToken`
  from storage

### Scenario: Token retrieval

- **WHEN** any component needs the current access token (e.g., future auth
  interceptor)
- **THEN** `TokenManager.getAccessToken()` SHALL return the stored token or
  `null` if none exists

## Requirement: Fragment-based auth navigation

The Android app SHALL use Navigation Component for the authentication flow. An
`AuthActivity` SHALL host a `NavHostFragment` with a navigation graph
(`nav_graph_auth.xml`) containing `LoginFragment` (start destination) and
`RegisterFragment`.

### Scenario: Navigate from Welcome to Login

- **WHEN** the user taps "Sign In" on `WelcomeActivity`
- **THEN** the app SHALL launch `AuthActivity` which displays `LoginFragment` as
  the start destination

### Scenario: Navigate from Welcome to Register

- **WHEN** the user taps "Create Account" on `WelcomeActivity`
- **THEN** the app SHALL launch `AuthActivity` with a nav argument that
  navigates to `RegisterFragment`

### Scenario: Navigate from Login to Register

- **WHEN** the user taps "Don't have an account? Sign up" on `LoginFragment`
- **THEN** the app SHALL navigate to `RegisterFragment` within the same
  `AuthActivity` using Navigation Component

### Scenario: Navigate from Register to Login

- **WHEN** the user taps "Already have an account? Sign In" on
  `RegisterFragment`
- **THEN** the app SHALL navigate back to `LoginFragment` (popBackStack) within
  `AuthActivity`

### Scenario: Back navigation from Login

- **WHEN** the user presses the back button or taps the back arrow on
  `LoginFragment` (start destination)
- **THEN** the app SHALL finish `AuthActivity` and return to `WelcomeActivity`

## Requirement: Remove Apple Sign-In

The Android app SHALL NOT display an Apple Sign-In button on the login screen.

### Scenario: Login screen layout without Apple button

- **WHEN** the login screen is displayed
- **THEN** the Google button SHALL span the full width of the social login
  section and no Apple button SHALL be present

## Requirement: Add library dependencies

The Android app SHALL declare the following dependencies in
`gradle/libs.versions.toml` and `app/build.gradle.kts`.

### Scenario: Navigation dependencies available

- **WHEN** the project is built
- **THEN** `androidx.navigation:navigation-fragment` and
  `androidx.navigation:navigation-ui` SHALL be resolved as implementation
  dependencies

### Scenario: Glide dependency available

- **WHEN** the project is built
- **THEN** `com.github.bumptech.glide:glide` SHALL be resolved as an
  implementation dependency (not used in any screen yet)

### Scenario: Lottie dependency available

- **WHEN** the project is built
- **THEN** `com.airbnb.android:lottie` SHALL be resolved as an implementation
  dependency (not used in any screen yet)

### Scenario: Firebase Auth dependency available

- **WHEN** the project is built with `google-services.json` in `app/`
- **THEN** `com.google.firebase:firebase-auth` SHALL be resolved and the
  `google-services` plugin SHALL process the config

### Scenario: Security Crypto dependency available

- **WHEN** the project is built
- **THEN** `androidx.security:security-crypto` SHALL be resolved for
  `EncryptedSharedPreferences`

### Scenario: Credential Manager dependency available

- **WHEN** the project is built
- **THEN** `androidx.credentials:credentials` and
  `androidx.credentials:credentials-play-services-auth` SHALL be resolved for
  Google Sign-In via Credential Manager
