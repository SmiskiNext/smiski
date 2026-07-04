# ADDED Requirements

## Requirement: Account settings screen displays editable profile form

AccountSettingsFragment SHALL display a form allowing the user to edit their
profile information including avatar, full name, and username.

### Scenario: Screen loads with current profile data

- **WHEN** AccountSettingsFragment is displayed
- **THEN** the system SHALL populate the avatar ImageView with current avatar or
  initials
- **AND** populate the Full Name TextInputEditText with current fullName
- **AND** populate the Username TextInputEditText with current username
- **AND** display the Email field as read-only with current email
- **AND** the Save button SHALL be disabled (no changes yet)

### Scenario: Screen shows loading while fetching profile

- **WHEN** profile data is being fetched
- **THEN** the system SHALL display a loading indicator
- **AND** form fields SHALL be disabled

## Requirement: Avatar can be changed via image picker

The user SHALL be able to change their avatar by selecting an image from Gallery
or taking a photo with Camera.

### Scenario: User taps avatar to open picker

- **WHEN** the user taps the avatar image or camera overlay badge
- **THEN** the system SHALL display AvatarPickerSheet BottomSheet
- **AND** the BottomSheet SHALL show options: "Take Photo", "Choose from
  Gallery"
- **AND** if user has custom avatar, show "Remove Photo" option

### Scenario: User selects image from gallery

- **WHEN** the user selects "Choose from Gallery"
- **AND** selects an image
- **THEN** the selected image SHALL be displayed in the avatar preview
- **AND** the Save button SHALL become enabled
- **AND** the image SHALL NOT be uploaded yet (deferred until Save)

### Scenario: User takes photo with camera

- **WHEN** the user selects "Take Photo"
- **AND** captures a photo
- **THEN** the captured photo SHALL be displayed in the avatar preview
- **AND** the Save button SHALL become enabled

### Scenario: User removes avatar

- **WHEN** the user selects "Remove Photo"
- **THEN** the avatar preview SHALL show the initials-based default avatar
- **AND** the Save button SHALL become enabled
- **AND** saving SHALL send `avatarUrl: null` to clear the avatar

### Scenario: User cancels image picker

- **WHEN** the user dismisses the BottomSheet or cancels the picker
- **THEN** no changes SHALL be made to the avatar preview
- **AND** the Save button state SHALL remain unchanged

## Requirement: Full name can be edited with validation

The Full Name field SHALL allow editing with inline validation.

### Scenario: User edits full name with valid input

- **WHEN** the user enters a full name between 1 and 255 characters
- **THEN** no error SHALL be displayed
- **AND** the Save button SHALL become enabled

### Scenario: User clears full name field

- **WHEN** the user clears the Full Name field (empty)
- **THEN** the system SHALL display inline error "Full name is required"
- **AND** the Save button SHALL be disabled

### Scenario: User enters full name exceeding max length

- **WHEN** the user enters more than 255 characters
- **THEN** the system SHALL display inline error "Full name is too long (max 255
  characters)"
- **AND** the Save button SHALL be disabled

## Requirement: Username can be edited with validation

The Username field SHALL allow editing with inline validation matching backend
constraints.

### Scenario: User edits username with valid input

- **WHEN** the user enters a username between 3 and 30 characters
- **AND** the username contains only letters, numbers, underscore, or hyphen
- **THEN** no error SHALL be displayed
- **AND** the Save button SHALL become enabled

### Scenario: User enters username too short

- **WHEN** the user enters fewer than 3 characters
- **THEN** the system SHALL display inline error "Username must be at least 3
  characters"

### Scenario: User enters username too long

- **WHEN** the user enters more than 30 characters
- **THEN** the system SHALL display inline error "Username must be at most 30
  characters"

### Scenario: User enters username with invalid characters

- **WHEN** the user enters characters not matching `[a-zA-Z0-9_-]`
- **THEN** the system SHALL display inline error "Username can only contain
  letters, numbers, \_ and -"

## Requirement: Save button submits profile changes

The Save button SHALL submit all pending changes (avatar upload + profile PATCH)
when tapped.

### Scenario: Save with avatar change

- **WHEN** the user taps Save
- **AND** avatar has been changed (new image selected)
- **THEN** the system SHALL upload the image to Firebase Storage
- **AND** after successful upload, PATCH `/api/v1/me` with the new avatarUrl
- **AND** display a loading indicator on the Save button during the process

### Scenario: Save with avatar removal

- **WHEN** the user taps Save
- **AND** avatar has been removed
- **THEN** the system SHALL PATCH `/api/v1/me` with `avatarUrl: null`

### Scenario: Save without avatar change

- **WHEN** the user taps Save
- **AND** only fullName or username has changed
- **THEN** the system SHALL PATCH `/api/v1/me` with only the changed fields
- **AND** avatarUrl SHALL NOT be included in the request (undefined)

### Scenario: Save succeeds

- **WHEN** all API calls complete successfully
- **THEN** the system SHALL update the local session cache with new profile data
- **AND** display a Snackbar "Profile updated"
- **AND** navigate back to ProfileFragment

### Scenario: Save fails due to username conflict

- **WHEN** PATCH `/api/v1/me` returns HTTP 409 with error code
  "USERNAME_ALREADY_EXISTS"
- **THEN** the system SHALL display inline error "Username is already taken"
- **AND** the user SHALL remain on AccountSettingsFragment
- **AND** the Save button SHALL be re-enabled

### Scenario: Save fails due to network error

- **WHEN** the API call fails due to network connectivity
- **THEN** the system SHALL display Snackbar "Network error. Check your
  connection."
- **AND** the Snackbar SHALL include a "Retry" action
- **AND** the user SHALL remain on AccountSettingsFragment

### Scenario: Avatar upload fails

- **WHEN** Firebase Storage upload fails
- **THEN** the system SHALL display Snackbar "Failed to upload photo"
- **AND** the Snackbar SHALL include a "Retry" action
- **AND** the profile text changes SHALL NOT be submitted (all-or-nothing)

## Requirement: Back navigation with unsaved changes shows confirmation

If the user has unsaved changes and attempts to navigate back, the system SHALL
prompt for confirmation.

### Scenario: User navigates back with unsaved changes

- **WHEN** the user taps the back button or system back gesture
- **AND** there are unsaved changes
- **THEN** the system SHALL display a confirmation dialog
- **AND** the dialog SHALL ask "Discard changes?"
- **AND** provide "Discard" and "Keep Editing" options

### Scenario: User confirms discard

- **WHEN** the user taps "Discard" in the confirmation dialog
- **THEN** the system SHALL navigate back to ProfileFragment
- **AND** discard all pending changes

### Scenario: User cancels discard

- **WHEN** the user taps "Keep Editing" in the confirmation dialog
- **THEN** the system SHALL dismiss the dialog
- **AND** remain on AccountSettingsFragment

### Scenario: User navigates back without changes

- **WHEN** the user taps the back button
- **AND** there are no unsaved changes
- **THEN** the system SHALL navigate back immediately without confirmation

## Requirement: Form accessibility compliance

AccountSettingsFragment SHALL meet accessibility requirements for form inputs.

### Scenario: TalkBack announces form fields

- **WHEN** TalkBack is enabled
- **AND** the user navigates through form fields
- **THEN** each field SHALL announce its label and current value
- **AND** error states SHALL be announced when present

### Scenario: Avatar has accessible description

- **WHEN** TalkBack is enabled
- **THEN** the avatar image SHALL have content description "Profile photo.
  Double tap to change"

### Scenario: Touch targets meet minimum size

- **WHEN** any interactive element is rendered
- **THEN** it SHALL have minimum touch target of 48dp x 48dp
