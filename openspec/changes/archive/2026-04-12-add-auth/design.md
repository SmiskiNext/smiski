# Context

The Android app (`frontends/android-app/`) follows MVVM + Clean Architecture
with Hilt DI. The auth flow currently has stub Activities (`LoginActivity`,
`RegisterActivity`) with empty ViewModels, empty UseCase shells, and an empty
`AuthRepository` interface/impl. `NetworkModule` is fully wired (OkHttp +
JsendUnwrapInterceptor + Retrofit + all API interfaces including `AuthApi`). The
backend provides REST endpoints for email/password login, registration, Google
login (accepts Firebase ID tokens via `FirebaseTokenVerifier`), token refresh,
and logout. All responses use JSend envelope format, which is already handled by
`JsendUnwrapInterceptor` on the Android side.

**Current state of auth layer:**

- `AuthRepository.java` — empty interface (no methods)
- `AuthRepositoryImpl.java` — empty implementation
- `LoginUseCase.java` / `RegisterUseCase.java` — inject `AuthRepository` but
  have no methods
- `LoginViewModel.java` / `RegisterViewModel.java` — empty, no `LiveData`
- `LoginActivity.java` — hardcodes skip to Dashboard; references `btnApple`
- `RegisterActivity.java` — calls `viewModel.registerUser()` but that method is
  empty; missing `username` field
- `UiState` sealed interface (Idle|Loading|Success|Error) and `UiError`
  hierarchy (Fail|ServerError|NetworkError|Unknown) — already defined and ready
  to use

## Goals / Non-Goals

**Goals:**

- Wire complete email/password login flow end-to-end (Fragment -> ViewModel ->
  UseCase -> AuthRepository -> Retrofit -> API -> token storage -> navigate
  Dashboard)
- Wire complete email/password registration flow end-to-end (Fragment ->
  ViewModel -> UseCase -> AuthRepository -> Retrofit -> API -> navigate Login)
- Implement Google Sign-In via Firebase Auth SDK using Credential Manager API ->
  obtain Firebase ID token -> send to backend `POST /api/v1/auth/google-login`
  -> token storage -> navigate Dashboard
- Refactor auth navigation from Activity-based to Fragment-based
  (`AuthActivity` + `LoginFragment` + `RegisterFragment` + Navigation Component)
- Add `TokenManager` using `EncryptedSharedPreferences` to persist `accessToken`
  and `refreshToken`
- Remove Apple Sign-In button from layout and code
- Add required dependencies: Navigation, Firebase Auth, Credential Manager,
  Security Crypto, Glide (add only), Lottie (add only)

**Non-Goals:**

- Token refresh interceptor (auto-refresh expired access tokens) — future change
- Session management on Splash screen (check stored token validity and skip
  login) — future change
- Forgot password flow — UI stub exists, not wired
- Logout API integration — `ProfileViewModel.logOut()` exists as stub, not in
  scope
- Using Glide or Lottie in any screen — dependencies added for future use only
- Unit/integration tests — no existing test infrastructure in the Android app
- Web frontend auth changes — web app is not in scope

## Decisions

### D1: Firebase Auth SDK for Google Sign-In (not standalone Credential Manager)

**Choice:** Use `firebase-auth` SDK + Credential Manager to sign in with Google,
then call `FirebaseAuth.getInstance().currentUser.getIdToken()` to get a
Firebase ID token.

**Why:** The backend's `FirebaseTokenVerifier` calls
`firebaseAuth.verifyIdToken(idToken)` which only accepts Firebase ID tokens.
Using Credential Manager alone would produce a Google ID token that the backend
cannot verify without changes.

**Alternatives considered:**

- Credential Manager only (Google ID token) — would require backend change to
  `GoogleAuthVerifier` to accept Google ID tokens instead of Firebase tokens.
  Rejected because it changes the backend contract.
- Legacy Google Sign-In SDK — deprecated by Google since 2023. Rejected.

### D2: Fragment-based auth navigation with Navigation Component

**Choice:** Create `AuthActivity` as a NavHost container with `LoginFragment`
and `RegisterFragment`. Delete old `LoginActivity` and `RegisterActivity`.

**Why:** Enables shared ViewModel scope across auth fragments (useful for
passing data between login/register), smoother transitions (fragment
animations), and prepares the architecture for future auth-related screens
(forgot password, email verification). Navigation Component provides type-safe
navigation via nav_graph.

**Alternatives considered:**

- Keep Activity-based navigation — simpler but doesn't scale, no shared scope,
  harder to animate transitions. Rejected.
- Keep old Activities alongside new Fragments — code duplication, confusing.
  Rejected.

### D3: EncryptedSharedPreferences for token storage

**Choice:** Use `androidx.security:security-crypto` to create
`EncryptedSharedPreferences` wrapped in a `TokenManager` class provided as
`@Singleton` via Hilt.

**Why:** Tokens at rest are encrypted using AES256-GCM (key) and AES256-SIV
(value). Standard `SharedPreferences` stores tokens in plaintext XML. DataStore
is async-first which adds complexity for synchronous token reads needed by
future auth interceptors.

**Alternatives considered:**

- Plain `SharedPreferences` — no encryption at rest. Rejected for security.
- DataStore — modern but async-first; token reads in OkHttp interceptors need
  synchronous access. Rejected for this use case.

### D4: Client-side validation before API calls

**Choice:** Validate inputs in ViewModel before calling UseCase/Repository.
Display field-level errors via `TextInputLayout.setError()` and general errors
below the submit button.

**Why:** Reduces unnecessary network calls. Backend will still validate (defense
in depth), but client-side catches obvious issues (empty fields, email format,
password mismatch) before making a request.

### D5: UiState flow for auth operations

**Choice:** Each ViewModel exposes `LiveData<UiState<T>>` where `T` is the
success result type. Fragments observe this LiveData and react to state changes.

**Why:** Follows existing project convention (`UiState` sealed interface is
already defined). Survives configuration changes via ViewModel lifecycle.

**State machine:**

```
  ┌──────┐    user action    ┌─────────┐   API success   ┌─────────┐
  │ Idle │──────────────────▶│ Loading │──────────────────▶│ Success │
  └──────┘                   └────┬────┘                  └─────────┘
                                  │
                              API failure
                                  │
                                  ▼
                             ┌─────────┐    user retry    ┌─────────┐
                             │  Error  │──────────────────▶│ Loading │
                             └─────────┘                  └─────────┘
```

### D6: Login success result type

**Choice:** Define a `LoginResult` record in the domain layer containing
`accessToken`, `refreshToken`, and `expiresIn`. `AuthRepositoryImpl` maps the
Retrofit response DTO to this domain type. `LoginViewModel` uses `TokenManager`
to store tokens upon `UiState.Success`.

**Why:** Keeps the domain layer independent of network DTOs. TokenManager is
injected into ViewModel (not Repository) to keep the data layer focused on API
communication.

### D7: Google Sign-In lifecycle management

**Choice:** The Credential Manager request is launched from `LoginFragment`
using `CredentialManager.getCredential()`. The resulting
`GoogleIdTokenCredential` is passed to Firebase Auth via
`GoogleAuthProvider.getCredential()` + `FirebaseAuth.signInWithCredential()`.
The Firebase ID token is then sent to the backend via
`AuthRepository.googleLogin(idToken)`.

**Why:** Credential Manager handles the Google account picker UI. Firebase Auth
SDK handles token exchange. This two-step flow produces the exact Firebase ID
token format that the backend expects.

## Risks / Trade-offs

- **[Risk] `google-services.json` not in repo** → Mitigation: Document as
  prerequisite. Build will fail with a clear error message if missing.
  `.gitignore` should be updated to exclude it.
- **[Risk] Firebase Auth SDK adds ~1MB to APK size** → Mitigation: Acceptable
  trade-off for proper token verification chain. Future: consider R8/ProGuard
  minification for release builds.
- **[Risk] No token refresh interceptor** → Mitigation: Access tokens last 900
  seconds (15 min). For MVP, user re-logs on expiry. Auth interceptor is a
  planned follow-up change.
- **[Risk] Fragment-based refactor breaks WelcomeActivity navigation** →
  Mitigation: Update `WelcomeActivity` to launch `AuthActivity` with a nav
  argument indicating login vs register destination.
- **[Trade-off] Glide and Lottie added but unused** → Accepted per user request.
  Dependencies are declared in version catalog but not used in any screen until
  future changes.
- **[Trade-off] No unit tests** → Accepted. No test infrastructure exists in the
  Android app. Testing is a separate future initiative.
