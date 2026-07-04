# Tasks

## 1. Setup & Dependencies

- [x] 1.1 Add DataStore Preferences dependency to `app/build.gradle.kts`
- [x] 1.2 Create `data/local/model/ThemeMode.java` enum (DARK, LIGHT, SYSTEM)
- [x] 1.3 Create `data/local/model/UserSession.java` record (userId, email,
      fullName, username, avatarUrl, rememberMe)
- [x] 1.4 Create `data/local/model/AppSettings.java` record (theme,
      lastMicEnabled, lastCameraEnabled)

## 2. DataStore Storage Layer

- [x] 2.1 Create `data/local/UserPreferencesManager.java` with DataStore
      initialization
- [x] 2.2 Implement user session methods: `saveUserSession()`,
      `getUserSession()`, `clearSession()`, `setRememberMe()`, `isRememberMe()`
- [x] 2.3 Implement app settings methods: `setThemeMode()`, `getThemeMode()`,
      `setLastMicEnabled()`, `getLastMicEnabled()`, `setLastCameraEnabled()`,
      `getLastCameraEnabled()`
- [x] 2.4 Update `di/StorageModule.java` to provide DataStore instance with
      `@Named("userPrefs")`

## 3. Auth Interceptor

- [x] 3.1 Create `data/remote/interceptor/AuthInterceptor.java` that injects
      Bearer token from TokenManager
- [x] 3.2 Update `di/NetworkModule.java` to include AuthInterceptor in
      OkHttpClient chain (before JsendUnwrapInterceptor)

## 4. Use Cases

- [x] 4.1 Add `refreshToken(String refreshToken)` method to
      `domain/repository/AuthRepository.java` interface
- [x] 4.2 Implement `refreshToken()` in
      `data/repository/AuthRepositoryImpl.java` calling POST
      /api/v1/auth/refresh
- [x] 4.3 Create `domain/usecase/auth/RefreshTokenUseCase.java`
- [x] 4.4 Create `domain/usecase/me/GetMeUseCase.java` calling GET /api/v1/me
- [x] 4.5 Update `domain/model/User.java` with fields: id, email, fullName,
      username, avatarUrl

## 5. Auto-Login Flow (Splash)

- [x] 5.1 Create `SplashState` sealed interface in `presentation/splash/` with
      Loading, AutoLoginAttempt, SessionExpired, NavigateToWelcome,
      NavigateToMain
- [x] 5.2 Implement `SplashViewModel.java` with auto-login state machine logic
- [x] 5.3 Update `SplashActivity.java` to observe SplashViewModel states and
      handle transitions
- [x] 5.4 Add session expired transition UI to `res/layout/activity_splash.xml`
      (message container with info icon)
- [x] 5.5 Add session expired strings to `res/values/strings.xml`
      (session_expired_title, session_expired_message)
- [x] 5.6 Add Vietnamese translations to `res/values-vi/strings.xml`

## 6. Remember Me (Login)

- [x] 6.1 Add Remember Me CheckBox to `res/layout/fragment_login.xml`
- [x] 6.2 Update `LoginFragment.java` to read checkbox state and pass to
      ViewModel
- [x] 6.3 Update `LoginViewModel.java` to accept rememberMe parameter in login
      methods
- [x] 6.4 Inject `GetMeUseCase` and `UserPreferencesManager` into
      `LoginViewModel`
- [x] 6.5 Implement profile fetch and session save logic in `LoginViewModel`
      when rememberMe=true
- [x] 6.6 Add remember_me string to `res/values/strings.xml` and
      `res/values-vi/strings.xml`

## 7. Welcome Session Expired Snackbar

- [x] 7.1 Update `WelcomeActivity.java` to check for EXTRA_SESSION_EXPIRED
      intent extra
- [x] 7.2 Show Snackbar "Session expired. Please sign in." when extra is true

## 8. Logout

- [x] 8.1 Inject `TokenManager` and `UserPreferencesManager` into
      `ProfileViewModel.java`
- [x] 8.2 Implement `logOut()` method to clear tokens, session, and set
      rememberMe=false

## 9. Theme Settings

- [x] 9.1 Add theme row to `res/layout/fragment_settings.xml`
- [x] 9.2 Inject `UserPreferencesManager` into `SettingsFragment.java`
- [x] 9.3 Implement theme selection dialog in `SettingsFragment`
- [x] 9.4 Apply theme change via `AppCompatDelegate.setDefaultNightMode()` +
      `recreate()`
- [x] 9.5 Apply saved theme on app start in `ZeroMeetingApp.java` or
      `MainActivity.onCreate()`
- [x] 9.6 Add theme-related strings to `res/values/strings.xml` (settings_theme,
      theme_dark, theme_light, theme_system)
- [x] 9.7 Add Vietnamese translations for theme strings

## 10. Mic/Camera State Persistence

- [x] 10.1 Inject `UserPreferencesManager` into `PreJoinFragment.java`
- [x] 10.2 Initialize mic/camera switches from saved state in
      `PreJoinFragment.onViewCreated()`
- [x] 10.3 Save mic/camera state in `PreJoinFragment.proceedToCall()` before
      navigation
- [x] 10.4 Inject `UserPreferencesManager` into `CreateMeetingFragment.java`
      (via ViewModel)
- [x] 10.5 Initialize mic/camera switches from saved state in
      `CreateMeetingFragment.onViewCreated()`
- [x] 10.6 Save mic/camera state when "Start Meeting" is clicked

## 11. Testing & Verification

- [x] 11.1 Run `./gradlew spotlessApply` to format XML files
- [x] 11.2 Run `./gradlew test` to verify no regressions
- [x] 11.3 Run `./gradlew assembleDebug` to verify build succeeds
- [ ] 11.4 Manual test: Login with Remember Me → kill app → reopen → should
      auto-login to Main
- [ ] 11.5 Manual test: Login with Remember Me → wait for token expiry → reopen
      → should show "Session expired" → Welcome
- [ ] 11.6 Manual test: Change theme in Settings → verify immediate change and
      persistence after restart
- [ ] 11.7 Manual test: Toggle mic/camera in PreJoin → join → exit → rejoin →
      verify state persisted
