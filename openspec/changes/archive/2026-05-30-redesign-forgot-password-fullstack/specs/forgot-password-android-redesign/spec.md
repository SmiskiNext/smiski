## ADDED Requirements

### Requirement: Two-step password reset UI flow

The Android app SHALL implement a 2-step password reset flow within a single
Fragment: Step 1 verifies OTP, Step 2 sets new password.

#### Scenario: Initial screen shows Step 1

- **WHEN** user navigates to ResetPasswordFragment with email parameter
- **THEN** system displays Step 1 UI (OTP verification)
- **AND** system displays step indicator showing "● ○" (step 1 of 2)
- **AND** system displays 6 separate OTP input boxes
- **AND** system displays countdown timer "Expires in: MM:SS"
- **AND** system displays resend button (disabled for 120 seconds)

#### Scenario: OTP verification success transitions to Step 2

- **WHEN** user enters valid 6-digit OTP in Step 1
- **AND** system successfully verifies OTP via `POST /v1/auth/verify-otp`
- **THEN** system stores temporary token in ViewModel
- **AND** system animates transition to Step 2 UI (password entry)
- **AND** system updates step indicator to "○ ●" (step 2 of 2)
- **AND** system displays password input field
- **AND** system displays confirm password input field
- **AND** system displays password strength meter

#### Scenario: OTP verification failure stays on Step 1

- **WHEN** user enters invalid OTP in Step 1
- **THEN** system displays error message below OTP boxes
- **AND** system shakes OTP input boxes (animation)
- **AND** system clears OTP input boxes
- **AND** system remains on Step 1
- **AND** system displays remaining attempts if applicable

#### Scenario: Back button on Step 1 navigates to email screen

- **WHEN** user is on Step 1 (OTP verification)
- **AND** user presses back button
- **THEN** system navigates back to ForgotPasswordFragment (email entry)

#### Scenario: Back button on Step 2 is disabled

- **WHEN** user is on Step 2 (password entry)
- **AND** user presses back button
- **THEN** system does NOT navigate back to Step 1
- **AND** system shows toast "Please complete password reset or cancel"

#### Scenario: Password reset success navigates to login

- **WHEN** user enters valid password and confirmation in Step 2
- **AND** system successfully resets password via `POST /v1/auth/reset-password`
- **THEN** system displays success message
- **AND** system navigates to LoginFragment after 2 seconds
- **AND** system pre-fills email in login form

### Requirement: Six separate OTP input boxes

The Android app SHALL display 6 separate EditText boxes for OTP input with
auto-focus behavior.

#### Scenario: Auto-focus on first box

- **WHEN** Step 1 UI is displayed
- **THEN** system auto-focuses first OTP input box
- **AND** system shows keyboard

#### Scenario: Auto-focus next box on digit entry

- **WHEN** user enters digit in OTP box N (where N < 6)
- **THEN** system auto-focuses OTP box N+1
- **AND** system keeps keyboard visible

#### Scenario: Auto-submit on sixth digit entry

- **WHEN** user enters digit in sixth OTP box
- **THEN** system auto-submits OTP for verification
- **AND** system displays loading indicator

#### Scenario: Backspace moves to previous box

- **WHEN** user presses backspace in empty OTP box N (where N > 1)
- **THEN** system focuses OTP box N-1
- **AND** system clears digit in box N-1

#### Scenario: Paste OTP from clipboard

- **WHEN** user pastes 6-digit code into any OTP box
- **THEN** system distributes digits across all 6 boxes
- **AND** system auto-submits OTP for verification

### Requirement: Live countdown timer for OTP expiry

The Android app SHALL display a live countdown timer showing remaining time
until OTP expires (20 minutes).

#### Scenario: Countdown timer starts at 20 minutes

- **WHEN** user navigates to Step 1 with OTP issued at time T
- **AND** current time is T + 2 minutes
- **THEN** system displays "Expires in: 18:00"

#### Scenario: Countdown timer updates every second

- **WHEN** countdown timer is displayed
- **THEN** system updates timer every second
- **AND** system displays format "MM:SS"

#### Scenario: Countdown timer reaches zero

- **WHEN** countdown timer reaches 00:00
- **THEN** system displays "Code expired"
- **AND** system disables OTP input boxes
- **AND** system displays "Request new code" button

#### Scenario: Countdown timer shows warning at 2 minutes

- **WHEN** countdown timer reaches 02:00 or less
- **THEN** system changes timer text color to warning color (orange/red)

### Requirement: Resend button with 120-second cooldown

The Android app SHALL display a resend button that is disabled for 120 seconds
after OTP is sent.

#### Scenario: Resend button disabled initially

- **WHEN** user navigates to Step 1 with fresh OTP
- **THEN** system displays resend button as disabled
- **AND** system displays "Resend (2:00)" countdown

#### Scenario: Resend countdown updates every second

- **WHEN** resend button is disabled
- **THEN** system updates countdown every second
- **AND** system displays format "Resend (M:SS)"

#### Scenario: Resend button enabled after 120 seconds

- **WHEN** 120 seconds have elapsed since OTP was sent
- **THEN** system enables resend button
- **AND** system changes button text to "Resend code"

#### Scenario: Resend button click requests new OTP

- **WHEN** user clicks enabled resend button
- **THEN** system calls `POST /v1/auth/forgot-password` with email
- **AND** system resets resend countdown to 120 seconds
- **AND** system resets OTP expiry countdown to 20 minutes
- **AND** system displays success message "New code sent"

### Requirement: Password strength meter

The Android app SHALL display a visual password strength meter with 4 levels:
Weak, Fair, Good, Strong.

#### Scenario: Weak password (< 8 characters)

- **WHEN** user enters password with fewer than 8 characters
- **THEN** system displays strength meter at 25% (red)
- **AND** system displays label "Weak"

#### Scenario: Fair password (8+ chars, lowercase only)

- **WHEN** user enters password with 8+ characters, lowercase only
- **THEN** system displays strength meter at 50% (orange)
- **AND** system displays label "Fair"

#### Scenario: Good password (8+ chars, mixed case + numbers)

- **WHEN** user enters password with 8+ characters, mixed case, and numbers
- **THEN** system displays strength meter at 75% (yellow)
- **AND** system displays label "Good"

#### Scenario: Strong password (8+ chars, mixed case + numbers + symbols)

- **WHEN** user enters password with 8+ characters, mixed case, numbers, and
  symbols
- **THEN** system displays strength meter at 100% (green)
- **AND** system displays label "Strong"

#### Scenario: Password strength updates in real-time

- **WHEN** user types in password field
- **THEN** system updates strength meter after each keystroke
- **AND** system updates strength label

### Requirement: Mail icon in ForgotPasswordFragment

The Android app SHALL display a proper mail icon in ForgotPasswordFragment
instead of placeholder.

#### Scenario: Mail icon displayed

- **WHEN** user navigates to ForgotPasswordFragment
- **THEN** system displays Material Symbols mail icon
- **AND** icon is centered above email input field
- **AND** icon size is 48dp

### Requirement: Accessibility support

The Android app SHALL provide accessibility support for password reset flow
including screen reader announcements and content descriptions.

#### Scenario: OTP input boxes have content descriptions

- **WHEN** screen reader is enabled
- **THEN** each OTP box announces "OTP digit N of 6"
- **AND** system announces "Enter 6-digit code sent to your email"

#### Scenario: Countdown timer announces at intervals

- **WHEN** screen reader is enabled
- **AND** countdown timer is running
- **THEN** system announces remaining time every 30 seconds
- **AND** system announces "Code expires in M minutes"

#### Scenario: Step transitions are announced

- **WHEN** screen reader is enabled
- **AND** user transitions from Step 1 to Step 2
- **THEN** system announces "Step 2 of 2: Set new password"

#### Scenario: Password strength changes are announced

- **WHEN** screen reader is enabled
- **AND** password strength changes
- **THEN** system announces "Password strength: [Weak/Fair/Good/Strong]"
