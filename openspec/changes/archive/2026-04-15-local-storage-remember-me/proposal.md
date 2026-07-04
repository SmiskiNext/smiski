# Why

The Android app currently has no persistent user session management. Users must
log in every time they open the app, even if they used it moments ago.
Additionally, there's no way to persist user preferences (theme, mic/camera
defaults) across sessions. This creates friction and a poor user experience
compared to competing meeting apps.

## What Changes

- Add DataStore Preferences for persistent local storage (user session + app
  settings)
- Add "Remember Me" checkbox on login screen to enable session persistence
- Implement auto-login flow at app startup when user has valid tokens and
  rememberMe enabled
- Add session expired handling with smooth UX transition when token refresh
  fails
- Add authenticated API interceptor for Bearer token injection
- Add Theme selector (Dark/Light/System) in Settings
- Persist last used mic/camera states for PreJoin and CreateMeeting screens
- Implement proper logout that clears all session data

## Capabilities

### New Capabilities

- `android-local-storage`: DataStore-based persistence for user session (userId,
  email, fullName, username, avatarUrl, rememberMe) and app settings (theme,
  lastMicEnabled, lastCameraEnabled)
- `android-auto-login`: Automatic login flow at splash screen - check tokens +
  rememberMe flag, attempt token refresh, navigate to Main on success or show
  "Session expired" transition on failure
- `android-auth-interceptor`: OkHttp interceptor that injects Bearer token from
  TokenManager into authenticated API requests

### Modified Capabilities

- `android-auth`: Adding Remember Me checkbox to login flow, fetching user
  profile via GET /api/v1/me after successful login, storing session data when
  rememberMe is checked

## Impact

**Code changes:**

- `data/local/`: New UserPreferencesManager, UserSession, AppSettings, ThemeMode
  classes
- `data/remote/interceptor/`: New AuthInterceptor
- `di/StorageModule.java`: DataStore provider
- `di/NetworkModule.java`: AuthInterceptor integration
- `domain/usecase/`: New RefreshTokenUseCase, GetMeUseCase
- `presentation/splash/`: SplashViewModel state machine, SplashActivity
  transition UI
- `presentation/auth/login/`: Remember Me checkbox, profile fetch on success
- `presentation/main/settings/`: Theme selector
- `presentation/main/profile/`: Proper logout implementation
- `presentation/videocall/PreJoinFragment`: Init from saved mic/camera state
- `presentation/main/meeting/CreateMeetingFragment`: Init from saved mic/camera
  state

**Dependencies:**

- Add `androidx.datastore:datastore-preferences` to app/build.gradle.kts

**APIs used:**

- `POST /api/v1/auth/refresh` - Token refresh for auto-login
- `GET /api/v1/me` - Fetch user profile after login
