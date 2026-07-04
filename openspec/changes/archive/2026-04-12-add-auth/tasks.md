# Tasks

## 1. Dependencies & Build Configuration

- [x] 1.1 Add version entries to `gradle/libs.versions.toml`:
      `androidxNavigation`, `firebaseAuth`, `firebaseBom`, `googleServices`,
      `androidxSecurityCrypto`, `androidxCredentials`, `glide`, `lottie`
- [x] 1.2 Add library entries to `gradle/libs.versions.toml`:
      `navigation-fragment`, `navigation-ui`, `firebase-bom`, `firebase-auth`,
      `security-crypto`, `credentials`, `credentials-play-services-auth`,
      `google-id`, `glide`, `lottie`
- [x] 1.3 Add `google-services` plugin entry to `gradle/libs.versions.toml`
      `[plugins]` section
- [x] 1.4 Apply `google-services` plugin in
      `frontends/android-app/build.gradle.kts` (root — declare plugin
      `apply false`) and `app/build.gradle.kts` (apply plugin)
- [x] 1.5 Add all new `implementation()` dependencies to `app/build.gradle.kts`
- [x] 1.6 Add `google-services.json` to `.gitignore` (file itself is provided by
      developer, not committed)

## 2. Token Persistence Layer

- [x] 2.1 Create `data/local/TokenManager.java` — `@Singleton` class wrapping
      `EncryptedSharedPreferences` with methods:
      `saveTokens(accessToken, refreshToken)`, `getAccessToken()`,
      `getRefreshToken()`, `clearTokens()`, `hasTokens()`
- [x] 2.2 Add `@Provides @Singleton` for `TokenManager` in a new
      `di/StorageModule.java` Hilt module (provides `EncryptedSharedPreferences`
      and `TokenManager`)

## 3. Domain Layer — Auth Contracts

- [x] 3.1 Create `domain/model/LoginResult.java` — record with fields:
      `accessToken`, `refreshToken`, `expiresIn`
- [x] 3.2 Create `domain/model/RegisterResult.java` — record with fields:
      `userId`, `email`, `fullName`, `username`
- [x] 3.3 Add methods to `domain/repository/AuthRepository.java` interface:
      `login(email, password)` returning `LoginResult`,
      `register(fullName, username, email, password)` returning
      `RegisterResult`, `googleLogin(idToken)` returning `LoginResult`
- [x] 3.4 Implement `LoginUseCase.java` — inject `AuthRepository`, add
      `execute(email, password)` method that delegates to
      `authRepository.login()`
- [x] 3.5 Implement `RegisterUseCase.java` — inject `AuthRepository`, add
      `execute(fullName, username, email, password)` method that delegates to
      `authRepository.register()`
- [x] 3.6 Create `domain/usecase/auth/GoogleLoginUseCase.java` — inject
      `AuthRepository`, add `execute(idToken)` method that delegates to
      `authRepository.googleLogin()`

## 4. Data Layer — Repository Implementation

- [x] 4.1 Implement `AuthRepositoryImpl.java` — inject `AuthApi` (generated
      Retrofit interface), implement `login()` by calling `AuthApi` endpoint and
      mapping DTO to `LoginResult`
- [x] 4.2 Implement `register()` in `AuthRepositoryImpl.java` — call `AuthApi`
      register endpoint, map DTO to `RegisterResult`
- [x] 4.3 Implement `googleLogin()` in `AuthRepositoryImpl.java` — call
      `AuthApi` google-login endpoint, map DTO to `LoginResult`

## 5. Navigation Setup

- [x] 5.1 Create `res/navigation/nav_graph_auth.xml` with two destinations:
      `loginFragment` (start destination) and `registerFragment`, with actions
      for bidirectional navigation and an argument on `AuthActivity` for initial
      destination
- [x] 5.2 Create `res/layout/activity_auth.xml` — single `FragmentContainerView`
      as NavHost filling the screen
- [x] 5.3 Create `presentation/auth/AuthActivity.java` — `@AndroidEntryPoint`
      Activity that sets `activity_auth.xml`, reads intent extra for initial
      destination, and configures NavController
- [x] 5.4 Update `AndroidManifest.xml` — add `AuthActivity`, remove
      `LoginActivity` and `RegisterActivity` declarations
- [x] 5.5 Update `WelcomeActivity.java` — change "Sign In" to launch
      `AuthActivity` (default to login), change "Create Account" to launch
      `AuthActivity` with register destination argument

## 6. Login UI (Fragment)

- [x] 6.1 Create `res/layout/fragment_login.xml`
- [x] 6.2 Create `presentation/auth/login/LoginFragment.java`
- [x] 6.3 Implement `LoginViewModel.java`
- [x] 6.4 Wire Google Sign-In in `LoginFragment`

## 7. Register UI (Fragment)

- [x] 7.1 Create `res/layout/fragment_register.xml`
- [x] 7.2 Create `presentation/auth/register/RegisterFragment.java`
- [x] 7.3 Implement `RegisterViewModel.java`

## 8. Cleanup & Wiring

- [x] 8.1 Delete `presentation/auth/login/LoginActivity.java` (replaced by
      `LoginFragment`)
- [x] 8.2 Delete `presentation/auth/register/RegisterActivity.java` (replaced by
      `RegisterFragment`)
- [x] 8.3 Delete `res/layout/activity_login.xml` (replaced by
      `fragment_login.xml`)
- [x] 8.4 Delete `res/layout/activity_register.xml` (replaced by
      `fragment_register.xml`)
- [x] 8.5 Update `app/codemap.md` to reflect new file structure (AuthActivity,
      LoginFragment, RegisterFragment, TokenManager, StorageModule,
      GoogleLoginUseCase, LoginResult, RegisterResult, fragment layouts,
      nav_graph)

## 9. Build Verification

- [x] 9.1 Ensure project builds successfully with `./gradlew :app:assembleDebug`
      (requires `google-services.json` in `app/`) NOTE: Build blocked only by
      missing google-services.json (prerequisite). All code compiles through
      generateDebugBuildConfig without errors.
- [x] 9.2 Verify no lint errors related to the auth changes NOTE: Lint cannot
      run without google-services.json. No code-level errors detected.
