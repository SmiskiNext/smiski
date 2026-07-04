# Why

The Android app currently has login and register screens that are non-functional
stubs — the login button hardcodes a skip to Dashboard, and all auth
ViewModels/Repositories are empty shells. Users cannot authenticate. This change
wires the full authentication flow (email/password + Google Sign-In via Firebase
Auth SDK), refactors the auth screens to fragment-based navigation, adds
required library dependencies, and removes the non-applicable Apple Sign-In
button.

## What Changes

- **Wire email/password login**: `LoginFragment` -> `LoginViewModel` ->
  `LoginUseCase` -> `AuthRepository` -> Retrofit `AuthApi`
  (`POST /api/v1/auth/login`) -> store tokens -> navigate to Dashboard
- **Wire email/password register**: `RegisterFragment` -> `RegisterViewModel` ->
  `RegisterUseCase` -> `AuthRepository` -> Retrofit `AuthApi`
  (`POST /api/v1/auth/register`) -> navigate to Login
- **Add username field** to Register UI (backend `RegisterRequest` requires
  `email`, `password`, `fullName`, `username`)
- **Implement Google Sign-In** via Firebase Auth SDK + Credential Manager -> get
  Firebase ID token -> `POST /api/v1/auth/google-login` -> store tokens ->
  navigate to Dashboard
- **Refactor auth navigation** from Activity-based (`LoginActivity`,
  `RegisterActivity`) to Fragment-based (`AuthActivity` + `LoginFragment` +
  `RegisterFragment` + `nav_graph_auth.xml`). **BREAKING**: Delete
  `LoginActivity` and `RegisterActivity`; update `WelcomeActivity` to launch
  `AuthActivity`; update `AndroidManifest.xml`
- **Add `TokenManager`** wrapping `EncryptedSharedPreferences` to persist
  `accessToken` + `refreshToken`
- **Remove Apple Sign-In button** from `activity_login.xml` layout and all
  related Java references (`btnApple`)
- **Add dependencies** to `libs.versions.toml` and `app/build.gradle.kts`:
    - `androidx.navigation:navigation-fragment` + `navigation-ui`
    - Glide (add dependency only, not used yet)
    - Lottie (add dependency only, not used yet)
    - `firebase-auth` + `google-services` plugin
    - `androidx.security:security-crypto` (for `EncryptedSharedPreferences`)
    - `androidx.credentials` (for Credential Manager)

## Capabilities

### New Capabilities

- `android-auth`: Full authentication flow on Android — email/password login,
  email/password registration, Google Sign-In via Firebase Auth SDK, token
  persistence, fragment-based auth navigation, and error handling UX

### Modified Capabilities

(none — no existing specs)

## Impact

- **Code**: `frontends/android-app/` — presentation layer (new AuthActivity,
  LoginFragment, RegisterFragment), domain layer (AuthRepository methods,
  UseCase logic), data layer (AuthRepositoryImpl wiring to Retrofit AuthApi,
  TokenManager)
- **Deleted code**: `LoginActivity.java`, `RegisterActivity.java` and their
  Activity declarations in `AndroidManifest.xml`
- **Build scripts**: `gradle/libs.versions.toml` (new version entries + library
  entries), `app/build.gradle.kts` (new plugin `google-services`, new
  dependencies), `build.gradle.kts` root (declare `google-services` plugin)
- **Layout XML**: `activity_login.xml` deleted (replaced by
  `fragment_login.xml`), `activity_register.xml` deleted (replaced by
  `fragment_register.xml`), new `activity_auth.xml` + `nav_graph_auth.xml`
- **Dependencies**: Firebase Auth SDK, AndroidX Navigation, AndroidX Security
  Crypto, AndroidX Credentials, Glide, Lottie
- **Prerequisites**: `google-services.json` must be placed in `app/` by the
  developer (not committed to git)
