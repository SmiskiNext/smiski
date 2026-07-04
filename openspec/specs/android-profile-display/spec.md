# Purpose

Define how the Android profile screen loads and presents authenticated user
information, including navigation to profile editing and avatar fallbacks.

# Requirements

## Requirement: Profile screen displays user data from API

ProfileFragment SHALL fetch the authenticated user's profile via GET
`/api/v1/me` and display the user's avatar, full name, and email address.

### Scenario: Successful profile load

- **WHEN** ProfileFragment is displayed
- **THEN** the system SHALL call GET `/api/v1/me`
- **AND** display the user's avatar image in `imgAvatar` ImageView
- **AND** display the user's full name in `tvName` TextView
- **AND** display the user's email in `tvEmail` TextView

### Scenario: Profile load while loading

- **WHEN** the API request is in progress
- **THEN** the system SHALL display a loading indicator
- **AND** the avatar, name, and email views SHALL show placeholder content

### Scenario: Profile load fails with network error

- **WHEN** the API request fails due to network connectivity
- **THEN** the system SHALL display a Snackbar with message "Network error.
  Check your connection."
- **AND** the Snackbar SHALL include a "Retry" action
- **AND** tapping "Retry" SHALL re-attempt the API call

### Scenario: Profile load fails with server error

- **WHEN** the API request returns HTTP 5xx
- **THEN** the system SHALL display a Snackbar with message "Something went
  wrong. Please try again."
- **AND** the Snackbar SHALL include a "Retry" action

## Requirement: Avatar displays with fallback

The avatar ImageView SHALL display the user's avatar image from `avatarUrl`,
with appropriate fallbacks for missing or failed images.

### Scenario: User has avatar URL

- **WHEN** the user's `avatarUrl` is not null and not empty
- **THEN** the system SHALL load the image using Glide
- **AND** display it in a circular crop

### Scenario: User has no avatar URL

- **WHEN** the user's `avatarUrl` is null or empty
- **THEN** the system SHALL display a default avatar showing the user's initials
- **AND** the initials SHALL be the first letter of first name and first letter
  of last name (e.g., "Jane Doe" -> "JD")
- **AND** the background color SHALL be deterministic based on userId

### Scenario: Avatar image fails to load

- **WHEN** Glide fails to load the avatar image (network error, 404, etc.)
- **THEN** the system SHALL display the initials-based default avatar as
  fallback

## Requirement: Profile state persists across configuration changes

ProfileViewModel SHALL preserve profile data across configuration changes
(rotation, theme change).

### Scenario: Screen rotation during profile display

- **WHEN** the user rotates the device while profile is displayed
- **THEN** the profile data SHALL remain visible without re-fetching from API
- **AND** no loading indicator SHALL appear

### Scenario: Screen rotation during loading

- **WHEN** the user rotates the device while profile is loading
- **THEN** the loading state SHALL be preserved
- **AND** the API call SHALL NOT be duplicated

## Requirement: Avatar tap navigates to account settings

Tapping the avatar image in ProfileFragment SHALL navigate to
AccountSettingsFragment.

### Scenario: User taps avatar

- **WHEN** the user taps the `imgAvatar` ImageView
- **THEN** the system SHALL navigate to AccountSettingsFragment
- **AND** the navigation SHALL use the standard enter/exit animations

### Scenario: User taps Account Settings menu item

- **WHEN** the user taps the "Account Settings" menu item
- **THEN** the system SHALL navigate to AccountSettingsFragment
