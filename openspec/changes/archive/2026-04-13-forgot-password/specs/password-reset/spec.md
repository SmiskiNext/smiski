# ADDED Requirements

## Requirement: Request password reset OTP

The system SHALL allow users to request a password reset OTP by providing their
email address. The system SHALL generate a 6-digit numeric OTP, store its hash,
and trigger an email notification.

### Scenario: Valid email with password account

- **WHEN** user submits forgot-password request with a registered email that has
  a password
- **THEN** system generates 6-digit OTP, stores hash with 15-minute expiry,
  publishes PasswordResetRequestedEvent, and returns success message "If account
  exists, OTP sent"

### Scenario: Valid email with Google-only account

- **WHEN** user submits forgot-password request with a registered email that
  uses Google Sign-In only (no password)
- **THEN** system returns success with message "This account uses Google
  Sign-In. Please sign in with Google."

### Scenario: Email not found

- **WHEN** user submits forgot-password request with an unregistered email
- **THEN** system returns success message "If account exists, OTP sent" without
  sending any email (prevents enumeration)

### Scenario: Existing unused OTP invalidated

- **WHEN** user requests new OTP while a previous unused OTP exists
- **THEN** system invalidates the previous OTP and generates a new one

## Requirement: Rate limit password reset requests

The system SHALL enforce rate limits on password reset requests to prevent
abuse.

### Scenario: Email rate limit exceeded

- **WHEN** user submits more than 5 forgot-password requests for the same email
  within 1 hour
- **THEN** system returns 429 Too Many Requests with message "Too many reset
  requests. Try again later."

### Scenario: IP rate limit exceeded

- **WHEN** requests from a single IP address exceed 20 forgot-password requests
  within 1 hour
- **THEN** system returns 429 Too Many Requests with message "Too many requests
  from your location. Try again later."

### Scenario: Rate limit tracking

- **WHEN** user submits forgot-password request
- **THEN** system records the attempt with email and IP address for rate limit
  enforcement

## Requirement: Reset password with OTP

The system SHALL allow users to reset their password by providing a valid OTP
and new password.

### Scenario: Valid OTP and password

- **WHEN** user submits reset-password with correct OTP and valid new password
- **THEN** system updates user's password hash, marks OTP as used, revokes all
  refresh tokens for the user, and returns success

### Scenario: Invalid OTP

- **WHEN** user submits reset-password with incorrect OTP
- **THEN** system increments attempt counter on the OTP token and returns 400
  with message "Invalid OTP"

### Scenario: OTP expired

- **WHEN** user submits reset-password with an OTP that has passed its 15-minute
  expiry
- **THEN** system returns 400 with message "OTP has expired. Please request a
  new one."

### Scenario: OTP already used

- **WHEN** user submits reset-password with an OTP that has already been used
- **THEN** system returns 400 with message "OTP has already been used. Please
  request a new one."

### Scenario: OTP locked after 5 failed attempts

- **WHEN** user submits incorrect OTP 5 times for the same reset request
- **THEN** system locks the OTP (marks as used), returns 400 with message "Too
  many incorrect attempts. Please request a new OTP."

### Scenario: Password validation failure

- **WHEN** user submits reset-password with valid OTP but invalid new password
  (too short, etc.)
- **THEN** system returns 400 with field validation errors without incrementing
  OTP attempt counter

## Requirement: Send password reset email

The system SHALL send an email containing the OTP when a password reset is
requested.

### Scenario: Email sent successfully

- **WHEN** PasswordResetRequestedEvent is consumed by notification service
- **THEN** system sends email to user with subject "Your password reset code",
  containing the 6-digit OTP and 15-minute expiry notice

### Scenario: Email content

- **WHEN** password reset email is rendered
- **THEN** email SHALL contain: OTP code prominently displayed, expiry time (15
  minutes), warning not to share the code, app name

## Requirement: Android forgot password screen

The system SHALL provide a forgot password screen in the Android app where users
can enter their email to request an OTP.

### Scenario: Navigate to forgot password

- **WHEN** user taps "Forgot password?" on login screen
- **THEN** app navigates to ForgotPasswordFragment

### Scenario: Submit email

- **WHEN** user enters email and taps "Send OTP" button
- **THEN** app shows loading state, calls forgot-password API, and on success
  navigates to reset password screen

### Scenario: Email validation

- **WHEN** user enters invalid email format
- **THEN** app shows inline error "Invalid email format" without calling API

### Scenario: API error handling

- **WHEN** API returns error (rate limit, network error)
- **THEN** app shows appropriate error message and remains on forgot password
  screen

### Scenario: Resend OTP with cooldown

- **WHEN** user is on reset password screen and wants to resend OTP
- **THEN** resend button is disabled for 60 seconds after last send, showing
  countdown timer

## Requirement: Android reset password screen

The system SHALL provide a reset password screen where users enter OTP and new
password.

### Scenario: Navigate from forgot password

- **WHEN** forgot-password API returns success
- **THEN** app navigates to ResetPasswordFragment with email passed as argument

### Scenario: Submit reset

- **WHEN** user enters OTP, new password, and confirms password, then taps
  "Reset Password"
- **THEN** app validates passwords match, shows loading state, calls
  reset-password API

### Scenario: Password mismatch

- **WHEN** user enters passwords that don't match
- **THEN** app shows inline error "Passwords do not match" without calling API

### Scenario: Successful reset

- **WHEN** reset-password API returns success
- **THEN** app shows success message and navigates to login screen

### Scenario: OTP error handling

- **WHEN** reset-password API returns OTP error (invalid, expired, locked)
- **THEN** app shows specific error message, clears OTP field, and if locked
  navigates back to forgot password screen

### Scenario: Back navigation

- **WHEN** user presses back on reset password screen
- **THEN** app navigates back to forgot password screen (OTP remains valid until
  expiry)
