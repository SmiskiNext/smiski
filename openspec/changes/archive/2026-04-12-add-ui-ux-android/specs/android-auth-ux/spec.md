# ADDED Requirements

## Requirement: Google Sign-In Button Branding

The Google Sign-In button on the login screen SHALL display the official Google
"G" logo icon and text "Sign in with Google" per Google branding guidelines.

### Scenario: Google button displays icon and text

- **WHEN** the login screen is displayed
- **THEN** `btnGoogle` SHALL show `@drawable/ic_google_logo` as a start icon
  with `app:iconTint="@null"` (preserving multicolor), text
  `@string/login_btn_google` ("Sign in with Google"), outlined button style with
  `?attr/colorOutline` stroke, and `?attr/colorOnSurface` text color

## Requirement: Touch Target Minimum Size

All interactive elements in auth screens SHALL meet the 48dp minimum touch
target size required by WCAG 2.1 AA and Material Design guidelines.

### Scenario: Back button touch target

- **WHEN** `btnBack` is rendered on Login or Register screens
- **THEN** it SHALL have minimum width and height of 48dp, and SHALL use
  `?attr/selectableItemBackgroundBorderless` for touch feedback (ripple)

### Scenario: Link touch targets

- **WHEN** `tvForgotPassword`, `tvNeedAccount`, or `tvHaveAccount` are rendered
- **THEN** each SHALL have `android:minHeight="48dp"` to ensure adequate touch
  target size

## Requirement: Color Contrast Compliance

Text in auth screens SHALL meet WCAG 2.1 AA minimum contrast ratio of 4.5:1 for
normal text and 3.0:1 for large text.

### Scenario: Divider label contrast

- **WHEN** the "OR CONTINUE WITH" text is displayed on the login screen
- **THEN** its text color SHALL be `?attr/colorOnSurfaceVariant` (not `#999999`)
  to achieve at least 4.5:1 contrast ratio against the surface background

### Scenario: Error text contrast

- **WHEN** an error message is displayed in `tvGeneralError`
- **THEN** its text color SHALL be `?attr/colorError` which resolves to at least
  4.5:1 contrast against the surface background in both light and dark modes

## Requirement: Accessibility Content Descriptions

Decorative images SHALL be excluded from the accessibility tree. Interactive
elements acting as buttons SHALL be focusable and clickable.

### Scenario: Decorative logo excluded

- **WHEN** `imgLogo` is rendered on the login screen
- **THEN** it SHALL have `android:importantForAccessibility="no"` (decorative,
  no content description needed)

### Scenario: Back button content description

- **WHEN** `btnBack` is rendered
- **THEN** its `android:contentDescription` SHALL reference
  `@string/navigate_up` (localized)

### Scenario: Forgot password focusable

- **WHEN** `tvForgotPassword` is rendered
- **THEN** it SHALL have `android:focusable="true"` and
  `android:clickable="true"` so screen readers announce it as an actionable
  element

## Requirement: Dark Mode Support for Auth Layouts

All hardcoded hex color values in `fragment_login.xml` and
`fragment_register.xml` SHALL be replaced with Material theme attributes so that
the layouts render correctly in both light and dark modes.

### Scenario: Login screen in dark mode

- **WHEN** the device is in dark mode and the login screen is displayed
- **THEN** the background SHALL use `?attr/colorSurface` (dark), text SHALL use
  `?attr/colorOnSurface`, and buttons SHALL use `?attr/colorPrimary` — no
  hardcoded `@android:color/white`, `@android:color/black`, `#666666`,
  `#4285F4`, `#D32F2F`, or `#E0E0E0` SHALL appear in the layout XML

### Scenario: Register screen in dark mode

- **WHEN** the device is in dark mode and the register screen is displayed
- **THEN** the same theme attribute replacements SHALL apply — no hardcoded hex
  colors in layout XML

## Requirement: Terms and Privacy Clickable Spans

The Terms of Service checkbox on the register screen SHALL make "Terms of
Service" and "Privacy Policy" individually tappable as links.

### Scenario: Terms link tapped

- **WHEN** the user taps "Terms of Service" within the checkbox text
- **THEN** the app SHALL open a browser or display a placeholder action (not
  navigate away from register). The rest of the checkbox text SHALL NOT trigger
  the link action.

### Scenario: Privacy link tapped

- **WHEN** the user taps "Privacy Policy" within the checkbox text
- **THEN** the app SHALL open a browser or display a placeholder action

### Scenario: Checkbox still toggleable

- **WHEN** the user taps the checkbox icon or non-link text
- **THEN** the checkbox SHALL toggle its checked state normally

## Requirement: Forgot Password Graceful Disable

The "Forgot password?" link on the login screen SHALL be visually dimmed and
show a "Coming soon" message instead of a placeholder Toast.

### Scenario: Forgot password tapped

- **WHEN** the user taps "Forgot password?"
- **THEN** a Snackbar SHALL display with text from
  `@string/login_forgot_password_coming_soon` (e.g., "Password recovery coming
  soon"). No Toast SHALL be shown.

### Scenario: Visual dimming

- **WHEN** the login screen is displayed
- **THEN** `tvForgotPassword` SHALL have reduced opacity (alpha ~0.5) to
  indicate it is not fully functional

## Requirement: Register Form Simplified Labels

The register screen SHALL NOT display separate label TextViews above
`TextInputLayout` fields. The `TextInputLayout` floating hint SHALL serve as
both label and placeholder.

### Scenario: No redundant labels

- **WHEN** `fragment_register.xml` is inspected
- **THEN** there SHALL be no standalone `TextView` elements acting as field
  labels (e.g., no `lblFullName`, `lblUsername`, `lblEmail`, `lblPassword`,
  `lblConfirmPassword`). Each `TextInputLayout` hint provides the label.

## Requirement: Error Recovery on Text Change

When the user begins typing in a field that has an error, the error SHALL clear
immediately without requiring a form resubmission.

### Scenario: Email error clears on type

- **WHEN** `tilEmail` displays an error and the user types in `edtEmail`
- **THEN** `tilEmail.setError(null)` SHALL be called, clearing the inline error.
  If `tvGeneralError` is visible, it SHALL also be hidden.

### Scenario: Password error clears on type

- **WHEN** `tilPassword` displays an error and the user types in `edtPassword`
- **THEN** `tilPassword.setError(null)` SHALL be called, clearing the error

## Requirement: Login Success Feedback

Upon successful login, the app SHALL provide brief visual feedback before
navigating to the Dashboard.

### Scenario: Success state shown

- **WHEN** `LoginViewModel` posts `UiState.Success`
- **THEN** the login button SHALL briefly display a success indicator (e.g.,
  checkmark text or green tint) for approximately 400ms, then navigate to
  `DashboardActivity` with `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK`

## Requirement: Register Success Feedback

Upon successful registration, the app SHALL inform the user before navigating
back to the login screen.

### Scenario: Success message shown

- **WHEN** `RegisterViewModel` posts `UiState.Success`
- **THEN** a Snackbar SHALL display with text from
  `@string/register_success_message` (e.g., "Account created! Please sign in.")
  and the app SHALL navigate to `LoginFragment`

## Requirement: Material Typography Scale

Auth screen text sizes SHALL use Material 3 type scale attributes instead of
hardcoded `sp` values.

### Scenario: Headline text

- **WHEN** the login screen title ("Welcome Back") is rendered
- **THEN** it SHALL use `?attr/textAppearanceHeadlineMedium` instead of
  `textSize="28sp"`

### Scenario: Body text

- **WHEN** subtitle or label text is rendered (e.g., "Sign in to access your AI
  meetings")
- **THEN** it SHALL use `?attr/textAppearanceBodyLarge` instead of
  `textSize="16sp"`

### Scenario: Label text

- **WHEN** small label text is rendered (e.g., "OR CONTINUE WITH")
- **THEN** it SHALL use `?attr/textAppearanceLabelMedium` instead of
  `textSize="12sp"`
