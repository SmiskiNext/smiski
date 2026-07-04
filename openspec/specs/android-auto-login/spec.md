# ADDED Requirements

## Requirement: Auto-Login Check at Splash

The system SHALL check for auto-login eligibility when SplashActivity starts.

### Scenario: Check auto-login eligibility

- **WHEN** SplashActivity starts
- **THEN** SplashViewModel SHALL check if `TokenManager.hasTokens()` AND
  `UserPreferencesManager.isRememberMe()` are both true

### Scenario: No tokens stored

- **WHEN** `TokenManager.hasTokens()` returns false
- **THEN** SplashViewModel SHALL emit `NavigateToWelcome` state immediately
  after splash animation

### Scenario: Tokens exist but rememberMe is false

- **WHEN** `TokenManager.hasTokens()` returns true but
  `UserPreferencesManager.isRememberMe()` returns false
- **THEN** SplashViewModel SHALL emit `NavigateToWelcome` state (user chose not
  to stay logged in)

## Requirement: Token Refresh for Auto-Login

The system SHALL attempt to refresh the access token when auto-login is
eligible.

### Scenario: Successful token refresh

- **WHEN** auto-login is eligible and `RefreshTokenUseCase.execute()` succeeds
- **THEN** SplashViewModel SHALL save the new tokens to TokenManager and emit
  `NavigateToMain` state

### Scenario: Failed token refresh (invalid token)

- **WHEN** auto-login is eligible but `RefreshTokenUseCase.execute()` fails with
  401
- **THEN** SplashViewModel SHALL clear tokens via `TokenManager.clearTokens()`,
  clear session via `UserPreferencesManager.clearSession()`, and emit
  `SessionExpired` state

### Scenario: Failed token refresh (network error)

- **WHEN** auto-login is eligible but `RefreshTokenUseCase.execute()` fails with
  network error
- **THEN** SplashViewModel SHALL emit `SessionExpired` state (treat as expired
  to avoid indefinite retry)

### Scenario: Refresh timeout

- **WHEN** token refresh takes longer than 10 seconds
- **THEN** SplashViewModel SHALL cancel the request and emit `SessionExpired`
  state

## Requirement: Session Expired Transition UI

The system SHALL display a brief transition message when session has expired
during auto-login.

### Scenario: Display session expired message

- **WHEN** SplashViewModel emits `SessionExpired` state
- **THEN** SplashActivity SHALL show a centered message "Your session has
  expired" with an info icon below the logo

### Scenario: Transition timing

- **WHEN** session expired message is displayed
- **THEN** SplashActivity SHALL wait 1.5 seconds before navigating to
  WelcomeActivity

### Scenario: Pass expired flag to Welcome

- **WHEN** navigating to WelcomeActivity after session expired
- **THEN** SplashActivity SHALL pass `EXTRA_SESSION_EXPIRED=true` in the intent

### Scenario: Show snackbar on Welcome

- **WHEN** WelcomeActivity receives `EXTRA_SESSION_EXPIRED=true`
- **THEN** WelcomeActivity SHALL show a Snackbar "Session expired. Please sign
  in." for 4 seconds

## Requirement: SplashViewModel States

The SplashViewModel SHALL use a sealed interface for splash states.

### Scenario: State definitions

- **WHEN** defining SplashState
- **THEN** it SHALL include: `Loading`, `AutoLoginAttempt`, `SessionExpired`,
  `NavigateToWelcome`, `NavigateToMain`

### Scenario: Initial state

- **WHEN** SplashViewModel is created
- **THEN** initial state SHALL be `Loading`

### Scenario: State observation

- **WHEN** SplashActivity observes state
- **THEN** it SHALL react to each state appropriately (show loading, attempt
  login, show expired message, navigate)

## Requirement: RefreshTokenUseCase

The system SHALL provide a use case for refreshing access tokens.

### Scenario: Execute refresh

- **WHEN** `RefreshTokenUseCase.execute()` is called
- **THEN** it SHALL call `AuthRepository.refreshToken(refreshToken)` with the
  current refresh token from TokenManager

### Scenario: Return new tokens

- **WHEN** refresh succeeds
- **THEN** `RefreshTokenUseCase` SHALL return a `CompletableFuture<LoginResult>`
  with new access and refresh tokens
