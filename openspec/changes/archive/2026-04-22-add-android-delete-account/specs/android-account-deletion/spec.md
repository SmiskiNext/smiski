# ADDED Requirements

## Requirement: Android users can permanently delete their account from Account Settings

The Android app SHALL allow an authenticated user to permanently delete their
account from the Account Settings screen through an explicit destructive-action
flow.

### Scenario: Danger zone is displayed on Account Settings

- **WHEN** `AccountSettingsFragment` is rendered for an authenticated user
- **THEN** the screen SHALL display a danger-zone section below the Save button
- **AND** the section SHALL include a destructive header, explanatory text, and
  a `Delete Account` button

### Scenario: User opens the delete-account confirmation dialog

- **WHEN** the user taps the `Delete Account` button
- **THEN** the app SHALL display a Material dialog titled `Delete Account?`
- **AND** the dialog SHALL explain that the account will be permanently deleted,
  the user will be logged out on all devices, and the action cannot be undone
- **AND** the dialog SHALL include a text input prompting the user to type
  `DELETE` to confirm

### Scenario: Confirm action remains disabled until confirmation text matches

- **WHEN** the delete-account confirmation dialog is visible
- **AND** the confirmation input does not exactly equal `DELETE`
- **THEN** the destructive confirm button SHALL remain disabled

### Scenario: User cancels account deletion

- **WHEN** the delete-account confirmation dialog is visible
- **AND** the user taps Cancel or dismisses the dialog
- **THEN** the app SHALL abandon the delete-account flow
- **AND** the account settings screen SHALL remain open without deleting the
  account

### Scenario: Account deletion succeeds

- **WHEN** the user confirms deletion with the exact text `DELETE`
- **AND** `DELETE /api/v1/me` completes successfully
- **THEN** the app SHALL clear all locally stored session data
- **AND** the app SHALL launch `WelcomeActivity` with a cleared task stack
- **AND** the user SHALL no longer be able to navigate back to authenticated
  screens from that task

### Scenario: Account deletion fails

- **WHEN** the user confirms deletion with the exact text `DELETE`
- **AND** the delete request fails
- **THEN** the app SHALL dismiss the confirmation dialog
- **AND** the app SHALL show an error Snackbar with a retry action
- **AND** the user SHALL remain on the account settings screen until deletion
  succeeds or is cancelled
