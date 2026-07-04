# ADDED Requirements

## Requirement: Account settings screen exposes account deletion entry point

AccountSettingsFragment SHALL include a destructive account-deletion entry point
as part of the account-management experience.

### Scenario: Danger zone appears below profile save controls

- **WHEN** the account settings screen is displayed
- **THEN** the layout SHALL render additional spacing and a divider below the
  Save button
- **AND** the layout SHALL show a `Danger Zone` section beneath the profile
  editing controls
- **AND** the section header SHALL use the error color to visually distinguish
  the destructive action

### Scenario: Delete action reflects destructive styling

- **WHEN** the `Delete Account` entry point is shown
- **THEN** it SHALL use destructive styling consistent with Material 3 outlined
  buttons and the app's error color tokens
- **AND** its description SHALL communicate that deletion is permanent and
  cannot be undone
