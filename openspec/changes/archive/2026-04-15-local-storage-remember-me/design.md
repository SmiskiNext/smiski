# Context

The Android app uses MVVM + Clean Architecture with Hilt DI. Current state:

- **Token storage**: `TokenManager` uses `EncryptedSharedPreferences` for
  access/refresh tokens
- **No session persistence**: App always navigates Splash → Welcome, requiring
  login every time
- **No user info storage**: `domain/model/User.java` is empty, user data not
  cached
- **Theme**: Uses `Theme.Material3.DayNight.NoActionBar`, no manual control
- **Language**: Managed by Android OS via
  `AppCompatDelegate.setApplicationLocales()` (already working)
- **Mic/Camera defaults**: Hardcoded in `CallViewModel` (mic=true, camera=false)

Key constraint: Must follow existing Clean Architecture patterns (domain has no
Android dependencies, data implements domain interfaces).

## Goals / Non-Goals

**Goals:**

- Persist user session (rememberMe flag + user profile) across app restarts
- Enable auto-login when tokens are valid and rememberMe is enabled
- Graceful handling of expired sessions with clear UX feedback
- Allow users to customize theme (Dark/Light/System)
- Remember last mic/camera toggle states for meeting screens
- Proper logout that clears all persisted data

**Non-Goals:**

- Biometric authentication (future scope)
- Syncing preferences with backend (local-only for now)
- Offline mode / offline data caching (just session persistence)
- Language settings in DataStore (already handled by Android OS)

## Decisions

### 1. Storage: DataStore Preferences over Room/SQLite

**Decision**: Use Jetpack DataStore Preferences for all local storage.

**Rationale**:

- Simple key-value data (no relationships, no complex queries)
- Type-safe, async, Flow-based API
- Smaller footprint than Room
- Already have EncryptedSharedPreferences for tokens, DataStore complements it

**Alternatives considered**:

- Room: Overkill for single-user session, adds unnecessary complexity
- SharedPreferences: Synchronous, no type safety, deprecated patterns

### 2. Separate EncryptedSharedPreferences (tokens) and DataStore (session/settings)

**Decision**: Keep tokens in EncryptedSharedPreferences, use DataStore for
non-sensitive data.

**Rationale**:

- Tokens require encryption at rest (security requirement)
- User profile (email, name) and settings (theme, mic state) don't need
  encryption
- Avoids migration complexity (TokenManager already exists and works)
- Clear separation of concerns

### 3. AuthInterceptor for Bearer Token Injection

**Decision**: Add OkHttp interceptor that reads token from TokenManager and
injects `Authorization: Bearer <token>` header.

**Rationale**:

- Centralized auth logic (all authenticated requests get token automatically)
- Retrofit generated APIs don't need individual `@Header` annotations
- Easy to extend (e.g., add token refresh on 401 later)

**Implementation**:

```java
public class AuthInterceptor implements Interceptor {
    private final TokenManager tokenManager;

    @Override
    public Response intercept(Chain chain) throws IOException {
        String token = tokenManager.getAccessToken();
        if (token != null) {
            Request newRequest = chain.request().newBuilder()
                .addHeader("Authorization", "Bearer " + token)
                .build();
            return chain.proceed(newRequest);
        }
        return chain.proceed(chain.request());
    }
}
```

### 4. SplashViewModel State Machine for Auto-Login

**Decision**: Implement sealed interface states in SplashViewModel to handle
auto-login flow.

**States**:

```
Loading → AutoLoginAttempt → Success (→ Main)
                          → SessionExpired (→ Welcome + Snackbar)
       → NavigateToWelcome (no rememberMe)
```

**Rationale**:

- Clear state transitions, easy to test
- Separates UI concerns from business logic
- Handles edge cases explicitly (network error, token invalid, etc.)

### 5. Fetch User Profile via GET /api/v1/me After Login

**Decision**: After successful login with rememberMe=true, call `/api/v1/me` to
get user profile and persist it.

**Rationale**:

- Login response only has tokens + expiresIn, no user info
- `/api/v1/me` returns complete user profile (id, email, fullName, username,
  avatarUrl)
- One extra API call, but only on login (not on auto-login)

**Alternative considered**:

- Decode JWT: Not reliable (token structure may change, not all fields may be in
  claims)

### 6. Theme Switching with Activity Recreate

**Decision**: Use `AppCompatDelegate.setDefaultNightMode()` +
`activity.recreate()`.

**Rationale**:

- Standard Android approach
- NavController state survives recreate via savedInstanceState
- Immediate visual feedback

### 7. Implicit Mic/Camera State Persistence

**Decision**: Auto-save last mic/camera state when user joins/starts meeting, no
explicit toggle in Settings.

**Rationale**:

- Most natural UX (remembers what user last used)
- No extra settings complexity
- Save on "Join Meeting" / "Start Meeting" button click only (not on every
  toggle)

## Data Models

### UserSession (DataStore)

```java
public record UserSession(
    String userId,
    String email,
    String fullName,
    String username,
    String avatarUrl,
    boolean rememberMe
) {}
```

### AppSettings (DataStore)

```java
public record AppSettings(
    ThemeMode theme,           // DARK, LIGHT, SYSTEM
    boolean lastMicEnabled,    // default: true
    boolean lastCameraEnabled  // default: true
) {}

public enum ThemeMode { DARK, LIGHT, SYSTEM }
```

## Risks / Trade-offs

### [Risk] Token refresh fails on slow network → user sees "Session expired" unnecessarily

**Mitigation**: Set reasonable timeout (10s), show loading indicator during
refresh. Consider retry logic for transient failures.

### [Risk] /api/v1/me call fails after successful login → user logged in but no profile cached

**Mitigation**: Non-blocking - login succeeds even if /me fails. Profile will be
fetched on next auto-login or can be retried.

### [Risk] DataStore migration if schema changes in future

**Mitigation**: Use versioned keys or migration strategy. For now, simple enough
that wipe-and-refetch is acceptable on schema change.

### [Trade-off] Two storage mechanisms (EncryptedSP + DataStore)

**Accepted**: Complexity is manageable, clear separation of sensitive vs
non-sensitive data.

### [Trade-off] Activity recreate on theme change disrupts user flow

**Accepted**: Standard Android behavior, NavController preserves state.
Alternative (delegate.applyDayNight()) doesn't work reliably for all views.
