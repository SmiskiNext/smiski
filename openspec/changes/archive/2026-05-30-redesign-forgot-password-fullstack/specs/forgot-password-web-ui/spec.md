## ADDED Requirements

### Requirement: Modal-based forgot password flow

The web app SHALL implement forgot password as a modal dialog overlaying the
login page with 3 internal screens: email entry, OTP verification, and password
reset.

#### Scenario: Modal opens from login page

- **WHEN** user clicks "Forgot Password?" link in login form
- **THEN** system opens forgot password modal
- **AND** system displays Screen 1 (email entry)
- **AND** system focuses email input field
- **AND** system traps keyboard focus within modal

#### Scenario: Modal closes on ESC key

- **WHEN** forgot password modal is open
- **AND** user presses ESC key
- **THEN** system displays confirmation dialog "Are you sure you want to cancel
  password reset?"
- **AND** if user confirms, system closes modal and returns focus to login page

#### Scenario: Modal closes on backdrop click

- **WHEN** forgot password modal is open
- **AND** user clicks outside modal (on backdrop)
- **THEN** system displays confirmation dialog "Are you sure you want to cancel
  password reset?"
- **AND** if user confirms, system closes modal

#### Scenario: Modal closes on X button

- **WHEN** forgot password modal is open
- **AND** user clicks X button in modal header
- **THEN** system displays confirmation dialog
- **AND** if user confirms, system closes modal

#### Scenario: Screen 1 - Email entry

- **WHEN** modal opens to Screen 1
- **THEN** system displays email input field
- **AND** system displays "Send Reset Code" button
- **AND** system displays modal title "Reset Your Password"

#### Scenario: Screen 1 - Submit email

- **WHEN** user enters valid email in Screen 1
- **AND** user clicks "Send Reset Code" button
- **THEN** system calls `POST /v1/auth/forgot-password`
- **AND** system transitions to Screen 2 (OTP verification)
- **AND** system displays success message "Code sent to [email]"

#### Scenario: Screen 2 - OTP verification

- **WHEN** modal transitions to Screen 2
- **THEN** system displays step indicator "Step 1 of 2"
- **AND** system displays 6 separate OTP input boxes
- **AND** system displays countdown timer "Expires in: MM:SS"
- **AND** system displays resend button (disabled for 120 seconds)
- **AND** system displays "Verify Code" button

#### Scenario: Screen 2 - OTP verification success

- **WHEN** user enters valid 6-digit OTP in Screen 2
- **AND** user clicks "Verify Code" button
- **THEN** system calls `POST /v1/auth/verify-otp`
- **AND** system stores temporary token
- **AND** system transitions to Screen 3 (password reset)

#### Scenario: Screen 3 - Password reset

- **WHEN** modal transitions to Screen 3
- **THEN** system displays step indicator "Step 2 of 2"
- **AND** system displays new password input field
- **AND** system displays confirm password input field
- **AND** system displays password strength meter
- **AND** system displays "Reset Password" button

#### Scenario: Screen 3 - Password reset success

- **WHEN** user enters valid password and confirmation in Screen 3
- **AND** user clicks "Reset Password" button
- **THEN** system calls `POST /v1/auth/reset-password` with temporary token
- **AND** system displays success message "Password reset successful"
- **AND** system closes modal after 2 seconds
- **AND** system pre-fills email in login form

### Requirement: Six separate OTP input boxes component

The web app SHALL provide a reusable OTP input component with 6 separate input
boxes and auto-focus behavior.

#### Scenario: Auto-focus on first box

- **WHEN** OTP input component is rendered
- **THEN** system auto-focuses first input box

#### Scenario: Auto-focus next box on digit entry

- **WHEN** user enters digit in box N (where N < 6)
- **THEN** system auto-focuses box N+1

#### Scenario: Auto-submit on sixth digit entry

- **WHEN** user enters digit in sixth box
- **THEN** system auto-submits OTP for verification
- **AND** system displays loading state

#### Scenario: Backspace moves to previous box

- **WHEN** user presses backspace in empty box N (where N > 1)
- **THEN** system focuses box N-1
- **AND** system clears digit in box N-1

#### Scenario: Paste OTP from clipboard

- **WHEN** user pastes 6-digit code into any box
- **THEN** system distributes digits across all 6 boxes
- **AND** system auto-submits OTP

#### Scenario: Arrow key navigation

- **WHEN** user presses right arrow in box N (where N < 6)
- **THEN** system focuses box N+1
- **AND** **WHEN** user presses left arrow in box N (where N > 1)
- **THEN** system focuses box N-1

#### Scenario: OTP input error state

- **WHEN** OTP verification fails
- **THEN** system applies error styling to all boxes (red border)
- **AND** system shakes boxes (CSS animation)
- **AND** system clears all boxes after animation
- **AND** system focuses first box

### Requirement: Password strength meter component

The web app SHALL provide a reusable password strength meter component with 4
levels: Weak, Fair, Good, Strong.

#### Scenario: Weak password (< 8 characters)

- **WHEN** user enters password with fewer than 8 characters
- **THEN** system displays strength bar at 25% width (red background)
- **AND** system displays label "Weak"

#### Scenario: Fair password (8+ chars, lowercase only)

- **WHEN** user enters password with 8+ characters, lowercase only
- **THEN** system displays strength bar at 50% width (orange background)
- **AND** system displays label "Fair"

#### Scenario: Good password (8+ chars, mixed case + numbers)

- **WHEN** user enters password with 8+ characters, mixed case, and numbers
- **THEN** system displays strength bar at 75% width (yellow background)
- **AND** system displays label "Good"

#### Scenario: Strong password (8+ chars, mixed case + numbers + symbols)

- **WHEN** user enters password with 8+ characters, mixed case, numbers, and
  symbols
- **THEN** system displays strength bar at 100% width (green background)
- **AND** system displays label "Strong"

#### Scenario: Password strength updates in real-time

- **WHEN** user types in password field
- **THEN** system updates strength meter on every keystroke
- **AND** system updates strength label

### Requirement: Countdown timer for OTP expiry

The web app SHALL display a live countdown timer showing remaining time until
OTP expires (20 minutes).

#### Scenario: Countdown timer starts at 20 minutes

- **WHEN** user navigates to Screen 2 with OTP issued at time T
- **AND** current time is T + 2 minutes
- **THEN** system displays "Expires in: ⏱️ 18:00"

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
- **THEN** system changes timer text color to warning color (red)

### Requirement: Resend button with 120-second cooldown

The web app SHALL display a resend button that is disabled for 120 seconds after
OTP is sent.

#### Scenario: Resend button disabled initially

- **WHEN** user navigates to Screen 2 with fresh OTP
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
- **AND** system displays success toast "New code sent"

### Requirement: Accessibility support

The web app SHALL provide WCAG 2.1 AA accessibility support for forgot password
modal including ARIA labels, keyboard navigation, and screen reader support.

#### Scenario: Modal has proper ARIA attributes

- **WHEN** forgot password modal is open
- **THEN** modal has `role="dialog"`
- **AND** modal has `aria-labelledby` pointing to modal title
- **AND** modal has `aria-modal="true"`

#### Scenario: Focus trap within modal

- **WHEN** forgot password modal is open
- **AND** user presses Tab key
- **THEN** focus cycles through interactive elements within modal
- **AND** focus does NOT escape to elements behind modal

#### Scenario: Focus returns to trigger on close

- **WHEN** user closes forgot password modal
- **THEN** focus returns to "Forgot Password?" link that opened modal

#### Scenario: OTP input boxes have ARIA labels

- **WHEN** screen reader is active
- **THEN** each OTP box announces "Digit N of 6"
- **AND** first box announces "Enter 6-digit code sent to your email"

#### Scenario: Countdown timer has ARIA live region

- **WHEN** screen reader is active
- **AND** countdown timer updates
- **THEN** system announces remaining time every 30 seconds via
  `aria-live="polite"`

#### Scenario: Password strength has ARIA live region

- **WHEN** screen reader is active
- **AND** password strength changes
- **THEN** system announces "Password strength: [Weak/Fair/Good/Strong]" via
  `aria-live="polite"`

#### Scenario: Error messages have ARIA live region

- **WHEN** screen reader is active
- **AND** error occurs (invalid OTP, network error, etc.)
- **THEN** system announces error message via `aria-live="assertive"`

### Requirement: State management hook

The web app SHALL provide a `useForgotPassword` hook to manage forgot password
flow state including current screen, email, OTP, temporary token, and error
handling.

#### Scenario: Hook initializes with Screen 1

- **WHEN** component mounts and calls `useForgotPassword()`
- **THEN** hook returns `currentScreen: "email"`
- **AND** hook returns `email: ""`
- **AND** hook returns `isLoading: false`
- **AND** hook returns `error: null`

#### Scenario: Hook transitions to Screen 2 after email submission

- **WHEN** component calls `submitEmail(email)`
- **AND** API call succeeds
- **THEN** hook updates `currentScreen: "otp"`
- **AND** hook stores `email` value
- **AND** hook starts OTP expiry timer

#### Scenario: Hook transitions to Screen 3 after OTP verification

- **WHEN** component calls `verifyOtp(otp)`
- **AND** API call succeeds
- **THEN** hook updates `currentScreen: "password"`
- **AND** hook stores temporary token
- **AND** hook stops OTP expiry timer

#### Scenario: Hook handles API errors

- **WHEN** component calls `submitEmail(email)`
- **AND** API call fails with error
- **THEN** hook updates `error` with error message
- **AND** hook sets `isLoading: false`
- **AND** hook does NOT change `currentScreen`
