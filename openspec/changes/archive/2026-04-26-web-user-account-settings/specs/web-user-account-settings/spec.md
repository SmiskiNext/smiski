# ADDED Requirements

## Requirement: Web account settings loads and displays the authenticated user profile

The web workspace SHALL load the authenticated user's account profile from
`getMe()` and render real account data on the existing profile screen.

### Scenario: Initial profile load succeeds

- **WHEN** an authenticated user opens the web workspace profile screen
- **THEN** the system SHALL call `getMe()` to retrieve the current user profile
- **THEN** the system SHALL display the returned `fullName`, `email`,
  `username`, `avatarUrl`, `authProvider`, and relevant account metadata using
  localized labels and fallback presentation for optional values

### Scenario: Initial profile load is pending

- **WHEN** the profile request is still in progress after the profile screen
  mounts
- **THEN** the system SHALL render a localized loading state within the account
  settings surface
- **THEN** the system SHALL NOT render hardcoded profile values while the
  request is unresolved

### Scenario: Initial profile load fails

- **WHEN** the profile request fails because of network, API, or server errors
- **THEN** the system SHALL render a localized error state with a retry action
- **THEN** activating retry SHALL trigger a new `getMe()` request without
  requiring a full page refresh

## Requirement: Web users can edit profile identity fields with backend-aligned validation

The web workspace SHALL allow authenticated users to edit their profile name,
username, and avatar data using a validated form backed by `putMe()`.

### Scenario: Edit form is prefilled from current profile data

- **WHEN** profile data has loaded successfully
- **THEN** the system SHALL initialize the edit form from the current
  `fullName`, `username`, and `avatarUrl` values
- **THEN** the system SHALL preserve the `email` as read-only account
  information rather than an editable form field

### Scenario: Client validation prevents invalid submissions

- **WHEN** the user enters invalid profile data
- **THEN** the system SHALL block submission when `fullName` is empty or exceeds
  255 characters
- **THEN** the system SHALL block submission when `username` is empty, shorter
  than 3 characters, longer than 30 characters, or does not match
  `^[a-zA-Z0-9_-]+$`
- **THEN** the system SHALL block submission when `avatarUrl` exceeds 2048
  characters for URL-based persistence

### Scenario: Successful profile save refreshes visible account data

- **WHEN** the user submits valid profile changes and `putMe()` succeeds
- **THEN** the system SHALL show localized success feedback
- **THEN** the system SHALL update the rendered profile summary and form
  defaults to match the saved response state without requiring a full page
  reload

### Scenario: Failed profile save preserves unsaved input

- **WHEN** the user submits profile changes and `putMe()` fails because of API,
  validation, or server errors
- **THEN** the system SHALL keep the user's unsaved form values intact
- **THEN** the system SHALL render localized recoverable error feedback at the
  field or form level as appropriate

## Requirement: Web account settings supports staged avatar selection with preview and constrained file validation

The web workspace SHALL support choosing a replacement avatar file locally,
validating it on the client, and previewing it before the user saves profile
changes.

### Scenario: Valid avatar file shows local preview

- **WHEN** the user selects an avatar image file of type JPEG, PNG, or WebP and
  size at or below 5 MB
- **THEN** the system SHALL generate a local preview for the selected image
  before profile save
- **THEN** the selected file SHALL remain a staged change until the user
  explicitly saves the profile form

### Scenario: Invalid avatar file is rejected immediately

- **WHEN** the user selects a file larger than 5 MB or a file whose type is not
  JPEG, PNG, or WebP
- **THEN** the system SHALL reject the selection
- **THEN** the system SHALL display localized validation feedback and keep the
  previous avatar state unchanged

### Scenario: Avatar persistence respects available backend contract

- **WHEN** the user saves profile changes that include an avatar change
- **THEN** the system SHALL persist the avatar through the confirmed backend
  contract available at implementation time
- **THEN** if only `avatarUrl` persistence is available, the system SHALL limit
  saved avatar behavior to that supported path and SHALL NOT imply that local
  file upload succeeded when no upload API exists

## Requirement: Web users can log out from account settings

The web workspace SHALL allow the authenticated user to end the current session
from the account settings screen.

### Scenario: Logout succeeds

- **WHEN** the user activates the logout action and `logout()` succeeds
- **THEN** the system SHALL clear visible authenticated account state
- **THEN** the system SHALL redirect the user to the locale-aware login
  experience

### Scenario: Logout prevents duplicate submission

- **WHEN** the logout request is in progress
- **THEN** the system SHALL disable repeated logout activation
- **THEN** the system SHALL show localized pending feedback until the request
  resolves

### Scenario: Logout request fails

- **WHEN** the logout request fails because of network, API, or server errors
- **THEN** the system SHALL show localized error feedback
- **THEN** the system SHALL allow the user to retry logout or continue using the
  current screen according to the resolved auth state

## Requirement: Web users can delete their account with explicit destructive confirmation

The web workspace SHALL support deleting the authenticated user's account
through a confirmation dialog backed by `deleteMe()`.

### Scenario: Delete account requires exact confirmation text

- **WHEN** the user opens the delete account dialog
- **THEN** the system SHALL require the user to type `DELETE` exactly before
  enabling the destructive confirmation action
- **THEN** the system SHALL keep the destructive action disabled while the
  confirmation text is missing or incorrect

### Scenario: Delete account succeeds

- **WHEN** the user confirms deletion and `deleteMe()` succeeds
- **THEN** the system SHALL clear visible authenticated account state
- **THEN** the system SHALL redirect the user to the locale-aware login
  experience after account deletion completes

### Scenario: Delete account fails

- **WHEN** the user confirms deletion and `deleteMe()` fails because of network,
  API, or server errors
- **THEN** the system SHALL keep the dialog open
- **THEN** the system SHALL show localized error feedback and allow the user to
  retry or cancel

## Requirement: Web account settings is responsive, accessible, and localized in supported locales

The web workspace SHALL provide the account settings experience with responsive
layout, keyboard-accessible controls, and localized English and Vietnamese
messaging.

### Scenario: Account settings adapts across viewport sizes

- **WHEN** the account settings screen is rendered on mobile or desktop viewport
  sizes
- **THEN** the system SHALL keep profile content, edit controls, logout, and
  delete actions usable without horizontal scrolling
- **THEN** the system SHALL stack or separate sections responsively to preserve
  clear action hierarchy

### Scenario: Account settings supports accessible forms and dialogs

- **WHEN** the user interacts with profile form fields, file selection controls,
  logout actions, or the delete account dialog
- **THEN** the system SHALL expose accessible labels, validation errors, focus
  behavior, and keyboard interaction through the rendered controls
- **THEN** destructive confirmation and error feedback SHALL remain perceivable
  to assistive technologies

### Scenario: Localized copy covers all account settings states

- **WHEN** the account settings feature is rendered in English or Vietnamese
- **THEN** all headings, descriptions, form labels, validation messages, loading
  text, success feedback, error feedback, logout messaging, and delete-account
  messaging SHALL come from locale message files
- **THEN** the system SHALL NOT rely on hardcoded English copy for new account
  settings behavior
