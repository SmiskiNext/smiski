# ADDED Requirements

## Requirement: Theme-Based Color System

All UI elements SHALL use Material 3 theme attributes instead of hardcoded
colors.

### Scenario: Primary color usage

- **WHEN** a UI element needs the brand blue color
- **THEN** it SHALL use `?attr/colorPrimary` instead of `#1877F2`

### Scenario: Surface color usage

- **WHEN** a background needs white/light color
- **THEN** it SHALL use `?attr/colorSurface` instead of `@android:color/white`
  or `#FFFFFF`

### Scenario: Text color usage

- **WHEN** primary text needs black color
- **THEN** it SHALL use `?attr/colorOnSurface` instead of `@android:color/black`
- **WHEN** secondary text needs gray color
- **THEN** it SHALL use `?attr/colorOnSurfaceVariant` instead of `#666666`

### Scenario: Complete color mapping

- **WHEN** any of these hardcoded colors appear in layouts
- **THEN** they SHALL be replaced:
    - `#1877F2` -> `?attr/colorPrimary`
    - `#666666` -> `?attr/colorOnSurfaceVariant`
    - `#F8F9FA` -> `?attr/colorSurfaceVariant`
    - `#E0E0E0` -> `?attr/colorOutline`
    - `#999999` -> `?attr/colorOnSurfaceVariant`
    - `@android:color/white` -> `?attr/colorSurface`
    - `@android:color/black` -> `?attr/colorOnSurface`

## Requirement: Material 3 Color Tokens

The theme SHALL define all required Material 3 color tokens.

### Scenario: Light theme colors

- **WHEN** `values/colors.xml` is loaded
- **THEN** it SHALL contain:
    - `md_theme_light_primary` (#1877F2)
    - `md_theme_light_onPrimary` (#FFFFFF)
    - `md_theme_light_primaryContainer` (#D4E8FF)
    - `md_theme_light_onPrimaryContainer` (#00315B)
    - `md_theme_light_surface` (#FFFFFF)
    - `md_theme_light_surfaceVariant` (#F5F7FA)
    - `md_theme_light_onSurface` (#1E1E1E)
    - `md_theme_light_onSurfaceVariant` (#666666)
    - `md_theme_light_outline` (#E0E0E0)
    - `md_theme_light_outlineVariant` (#C4C7C5)
    - `md_theme_light_error` (#B3261E)
    - `md_theme_light_errorContainer` (#F9DEDC)

### Scenario: Dark theme colors

- **WHEN** `values/colors.xml` is loaded
- **THEN** it SHALL contain:
    - `md_theme_dark_primary` (#4A90E2)
    - `md_theme_dark_onPrimary` (#FFFFFF)
    - `md_theme_dark_primaryContainer` (#004785)
    - `md_theme_dark_onPrimaryContainer` (#D4E8FF)
    - `md_theme_dark_surface` (#121212)
    - `md_theme_dark_surfaceVariant` (#1E1E1E)
    - `md_theme_dark_onSurface` (#E3E3E3)
    - `md_theme_dark_onSurfaceVariant` (#A0A0A0)
    - `md_theme_dark_outline` (#444746)
    - `md_theme_dark_outlineVariant` (#3D3D3D)
    - `md_theme_dark_error` (#F2B8B5)

### Scenario: Theme attribute mapping

- **WHEN** `values/themes.xml` is loaded
- **THEN** all color tokens SHALL be mapped to theme attributes (colorPrimary,
  colorSurface, etc.)
- **WHEN** `values-night/themes.xml` is loaded
- **THEN** dark color tokens SHALL be mapped to the same theme attributes

## Requirement: Dark Mode Support

The app SHALL fully support dark mode via theme attributes and drawable
variants.

### Scenario: Dark mode drawable variants

- **WHEN** dark mode is enabled
- **THEN** drawables that use light-only colors SHALL have variants in
  `drawable-night/`
- **THEN** affected drawables: `bg_circle_white`, `bg_rounded_gray`,
  `bg_leave_button`, `bg_circle_blue`

### Scenario: Root layout backgrounds

- **WHEN** any layout has a root background color
- **THEN** it SHALL use `?attr/colorSurface` or `?attr/colorSurfaceVariant`
- **THEN** it SHALL NOT use hardcoded colors like `#F8F9FA`

## Requirement: Material Symbols Icons

All icons SHALL use Material Symbols instead of Android system icons.

### Scenario: Bottom navigation icons

- **WHEN** `BottomNavigationView` is displayed
- **THEN** Home tab SHALL use `ic_home_outlined` / `ic_home_filled` selector
- **THEN** Calendar tab SHALL use `ic_calendar_outlined` / `ic_calendar_filled`
  selector
- **THEN** Profile tab SHALL use `ic_person_outlined` / `ic_person_filled`
  selector

### Scenario: System icon replacement

- **WHEN** any of these system icons appear
- **THEN** they SHALL be replaced:
    - `@android:drawable/ic_menu_preferences` -> `@drawable/ic_settings`
    - `@android:drawable/ic_menu_add` -> `@drawable/ic_add`
    - `@android:drawable/ic_menu_search` -> `@drawable/ic_search`
    - `@android:drawable/ic_media_previous` -> `@drawable/ic_chevron_left`
    - `@android:drawable/ic_media_next` -> `@drawable/ic_chevron_right`
    - `@android:drawable/ic_menu_manage` -> `@drawable/ic_manage_accounts`
    - `@android:drawable/ic_menu_recent_history` -> `@drawable/ic_history`
    - `@android:drawable/ic_menu_help` -> `@drawable/ic_help_outline`
    - `@android:drawable/ic_lock_power_off` -> `@drawable/ic_logout`

### Scenario: Icon tinting

- **WHEN** icons are displayed
- **THEN** they SHALL be tinted using `?attr/colorOnSurface` or
  `?attr/colorOnSurfaceVariant`
- **THEN** they SHALL NOT use hardcoded tint colors

## Requirement: Accessibility Compliance

All UI elements SHALL meet accessibility requirements.

### Scenario: Touch targets minimum size

- **WHEN** any clickable element is displayed
- **THEN** it SHALL have minimum dimensions of 48dp x 48dp
- **THEN** settings icon (currently 28dp), nav arrows (currently 32dp) SHALL be
  wrapped in 48dp containers

### Scenario: Content descriptions

- **WHEN** an ImageView displays an icon
- **THEN** it SHALL have `android:contentDescription` attribute
- **THEN** decorative images SHALL use `android:importantForAccessibility="no"`

### Scenario: Semantic headings

- **WHEN** a TextView acts as a section header
- **THEN** it SHALL have `android:accessibilityHeading="true"` (API 28+)

### Scenario: Color contrast

- **WHEN** text is displayed on a background
- **THEN** contrast ratio SHALL be at least 4.5:1 for body text
- **THEN** `#999999` text color SHALL be replaced with `#757575` or theme
  attribute

## Requirement: Empty State Design

Dashboard and Calendar SHALL display designed empty states when no data exists.

### Scenario: Dashboard empty state

- **WHEN** user has no upcoming meetings
- **THEN** system SHALL display an illustration
- **THEN** system SHALL display message "No upcoming meetings"
- **THEN** system SHALL display CTA button "Schedule a meeting"

### Scenario: Calendar empty state

- **WHEN** selected day has no events
- **THEN** system SHALL display message "No meetings today"
- **THEN** system SHALL display illustration

## Requirement: Spacing and Dimension Tokens

All spacing SHALL use dimension resources instead of hardcoded values.

### Scenario: Spacing token usage

- **WHEN** padding or margin is needed
- **THEN** it SHALL use one of: `@dimen/spacing_xs` (4dp), `@dimen/spacing_sm`
  (8dp), `@dimen/spacing_md` (16dp), `@dimen/spacing_lg` (24dp),
  `@dimen/spacing_xl` (32dp)
- **THEN** it SHALL NOT use arbitrary values like `20dp`

### Scenario: Additional dimension tokens

- **WHEN** `dimens.xml` is loaded
- **THEN** it SHALL contain:
    - `corner_radius_sm` (4dp), `corner_radius_md` (8dp), `corner_radius_lg`
      (12dp), `corner_radius_xl` (16dp)
    - `icon_size_sm` (16dp), `icon_size_md` (24dp), `icon_size_lg` (32dp)
    - `avatar_size_sm` (40dp), `avatar_size_md` (48dp), `avatar_size_lg` (100dp)
    - `quick_action_size` (80dp)

## Requirement: Dark Mode Login Header Gradient

The login screen header gradient (`bg_login_header.xml`) SHALL render
appropriately in both light and dark modes.

### Scenario: Light mode gradient

- **WHEN** the app runs in light mode
- **THEN** the header background SHALL display a gradient from a light blue tint
  to the surface color

### Scenario: Dark mode gradient

- **WHEN** the app runs in dark mode
- **THEN** `res/drawable-night/bg_login_header.xml` SHALL provide a dark
  gradient (e.g., `#002A5A` to the dark surface color) so the header does not
  display a white/bright gradient on a dark background
