# ADDED Requirements

## Requirement: DataStore Preferences Storage

The system SHALL provide a `UserPreferencesManager` class that uses Jetpack
DataStore Preferences for persistent storage of user session and app settings.

### Scenario: DataStore initialization

- **WHEN** the app starts
- **THEN** `UserPreferencesManager` SHALL be initialized as a Hilt singleton
  with DataStore instance named "zms_user_prefs"

### Scenario: DataStore injection

- **WHEN** a ViewModel or Repository needs access to preferences
- **THEN** it SHALL receive `UserPreferencesManager` via constructor injection

## Requirement: User Session Storage

The system SHALL persist user session data including userId, email, fullName,
username, avatarUrl, and rememberMe flag.

### Scenario: Save user session

- **WHEN** user logs in with rememberMe=true
- **THEN** `UserPreferencesManager.saveUserSession(UserSession)` SHALL persist
  all user profile fields to DataStore

### Scenario: Load user session

- **WHEN** app needs to check logged-in user
- **THEN** `UserPreferencesManager.getUserSession()` SHALL return a
  Flow<UserSession?> that emits the stored session or null

### Scenario: Clear user session

- **WHEN** user logs out
- **THEN** `UserPreferencesManager.clearSession()` SHALL remove all user session
  data from DataStore

### Scenario: Update rememberMe flag

- **WHEN** rememberMe state changes
- **THEN** `UserPreferencesManager.setRememberMe(boolean)` SHALL update only the
  rememberMe flag

### Scenario: Check rememberMe flag

- **WHEN** app needs to check auto-login eligibility
- **THEN** `UserPreferencesManager.isRememberMe()` SHALL return the current
  rememberMe boolean value

## Requirement: App Settings Storage

The system SHALL persist app settings including theme mode and last mic/camera
states.

### Scenario: Save theme mode

- **WHEN** user changes theme in Settings
- **THEN** `UserPreferencesManager.setThemeMode(ThemeMode)` SHALL persist the
  selected theme (DARK, LIGHT, or SYSTEM)

### Scenario: Load theme mode

- **WHEN** app starts or Settings screen opens
- **THEN** `UserPreferencesManager.getThemeMode()` SHALL return the stored
  ThemeMode, defaulting to SYSTEM

### Scenario: Save last mic state

- **WHEN** user joins or starts a meeting
- **THEN** `UserPreferencesManager.setLastMicEnabled(boolean)` SHALL persist the
  mic toggle state

### Scenario: Save last camera state

- **WHEN** user joins or starts a meeting
- **THEN** `UserPreferencesManager.setLastCameraEnabled(boolean)` SHALL persist
  the camera toggle state

### Scenario: Load last mic/camera states

- **WHEN** PreJoinFragment or CreateMeetingFragment initializes
- **THEN** `UserPreferencesManager.getLastMicEnabled()` and
  `getLastCameraEnabled()` SHALL return the stored states, defaulting to true
  for mic and true for camera

## Requirement: ThemeMode Enum

The system SHALL provide a `ThemeMode` enum with values DARK, LIGHT, and SYSTEM.

### Scenario: ThemeMode to NightMode mapping

- **WHEN** applying theme
- **THEN** ThemeMode.DARK SHALL map to MODE_NIGHT_YES, ThemeMode.LIGHT to
  MODE_NIGHT_NO, ThemeMode.SYSTEM to MODE_NIGHT_FOLLOW_SYSTEM
