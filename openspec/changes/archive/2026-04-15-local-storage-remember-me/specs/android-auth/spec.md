# MODIFIED Requirements

## Requirement: Remember Me Checkbox on Login

The login screen SHALL include a "Remember me" checkbox that controls session
persistence.

### Scenario: Remember Me checkbox display

- **WHEN** LoginFragment is displayed
- **THEN** it SHALL show a CheckBox with label "Remember me" below the password
  field

### Scenario: Remember Me default state

- **WHEN** LoginFragment initializes
- **THEN** the Remember Me checkbox SHALL be unchecked (default OFF)

### Scenario: Remember Me state passed to ViewModel

- **WHEN** user clicks Sign In button
- **THEN** LoginFragment SHALL pass the checkbox state to
  `LoginViewModel.loginWithEmail(email, password, rememberMe)`

### Scenario: Remember Me with Google Sign-In

- **WHEN** user signs in with Google
- **THEN** LoginFragment SHALL pass the checkbox state to
  `LoginViewModel.loginWithGoogle(firebaseIdToken, rememberMe)`

## Requirement: Fetch User Profile After Login

LoginViewModel SHALL fetch user profile via GET /api/v1/me when rememberMe is
enabled.

### Scenario: Fetch profile on successful login with rememberMe

- **WHEN** login succeeds and rememberMe is true
- **THEN** LoginViewModel SHALL call `GetMeUseCase.execute()` to fetch user
  profile

### Scenario: Save session after profile fetch

- **WHEN** GetMeUseCase returns user profile and rememberMe is true
- **THEN** LoginViewModel SHALL call
  `UserPreferencesManager.saveUserSession(UserSession)` with the fetched data

### Scenario: Set rememberMe flag

- **WHEN** login succeeds with rememberMe=true
- **THEN** LoginViewModel SHALL call
  `UserPreferencesManager.setRememberMe(true)`

### Scenario: Skip profile fetch when rememberMe is false

- **WHEN** login succeeds and rememberMe is false
- **THEN** LoginViewModel SHALL NOT fetch profile and SHALL call
  `UserPreferencesManager.setRememberMe(false)`

### Scenario: Profile fetch failure is non-blocking

- **WHEN** login succeeds but GetMeUseCase fails
- **THEN** LoginViewModel SHALL still emit Success state (login succeeded,
  profile will be fetched later)

## Requirement: Proper Logout Clearing

ProfileViewModel.logOut() SHALL clear all session data.

### Scenario: Clear tokens on logout

- **WHEN** user clicks logout
- **THEN** ProfileViewModel SHALL call `TokenManager.clearTokens()`

### Scenario: Clear session on logout

- **WHEN** user clicks logout
- **THEN** ProfileViewModel SHALL call `UserPreferencesManager.clearSession()`

### Scenario: Reset rememberMe on logout

- **WHEN** user clicks logout
- **THEN** ProfileViewModel SHALL call
  `UserPreferencesManager.setRememberMe(false)`

### Scenario: Navigate to Welcome after logout

- **WHEN** logout completes
- **THEN** ProfileFragment SHALL navigate to WelcomeActivity with
  FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK

## Requirement: Theme Settings

SettingsFragment SHALL allow users to change the app theme.

### Scenario: Display theme option

- **WHEN** SettingsFragment is displayed
- **THEN** it SHALL show a "Theme" row with current theme value
  (Dark/Light/System)

### Scenario: Theme selection dialog

- **WHEN** user taps Theme row
- **THEN** SettingsFragment SHALL show a dialog with options: Dark, Light,
  System (follow device)

### Scenario: Apply theme change

- **WHEN** user selects a theme option
- **THEN** SettingsFragment SHALL call
  `UserPreferencesManager.setThemeMode(ThemeMode)` and apply via
  `AppCompatDelegate.setDefaultNightMode()`

### Scenario: Recreate activity on theme change

- **WHEN** theme is changed
- **THEN** SettingsFragment SHALL call `requireActivity().recreate()` to apply
  the change immediately

### Scenario: Load theme on app start

- **WHEN** app starts (in Application.onCreate or MainActivity.onCreate)
- **THEN** the system SHALL read `UserPreferencesManager.getThemeMode()` and
  apply via `AppCompatDelegate.setDefaultNightMode()`

## Requirement: Mic/Camera State Persistence

PreJoinFragment and CreateMeetingFragment SHALL use persisted mic/camera states.

### Scenario: Initialize mic/camera from saved state in PreJoin

- **WHEN** PreJoinFragment.onViewCreated() is called
- **THEN** it SHALL read `UserPreferencesManager.getLastMicEnabled()` and
  `getLastCameraEnabled()` to initialize switch states

### Scenario: Initialize mic/camera from saved state in CreateMeeting

- **WHEN** CreateMeetingFragment.onViewCreated() is called
- **THEN** it SHALL read `UserPreferencesManager.getLastMicEnabled()` and
  `getLastCameraEnabled()` to initialize switch states

### Scenario: Save mic/camera state on join

- **WHEN** user clicks "Join Meeting" in PreJoinFragment
- **THEN** it SHALL call `UserPreferencesManager.setLastMicEnabled()` and
  `setLastCameraEnabled()` with current switch states before navigating

### Scenario: Save mic/camera state on create

- **WHEN** user clicks "Start Meeting" in CreateMeetingFragment
- **THEN** it SHALL call `UserPreferencesManager.setLastMicEnabled()` and
  `setLastCameraEnabled()` with current switch states before proceeding
