# ADDED Requirements

## Requirement: Material 3 Color Palette

The Android app SHALL define a complete Material 3 color palette in
`res/values/colors.xml` with named color resources for both light and dark
themes. The palette SHALL include at minimum: primary, onPrimary, surface,
onSurface, onSurfaceVariant, error, and outline — each with light and dark
variants. The app-wide primary color SHALL be `#1877F2`. A separate
`google_blue` (`#4285F4`) resource SHALL exist exclusively for the Google
Sign-In button.

### Scenario: Light theme colors defined

- **WHEN** the app is built
- **THEN** `res/values/colors.xml` SHALL contain at minimum:
  `md_theme_light_primary` (#1877F2), `md_theme_light_onPrimary` (#FFFFFF),
  `md_theme_light_surface` (#FFFFFF), `md_theme_light_onSurface` (#1E1E1E),
  `md_theme_light_onSurfaceVariant` (#666666), `md_theme_light_error` (#B3261E),
  `md_theme_light_outline` (#E0E0E0), and `google_blue` (#4285F4)

### Scenario: Dark theme colors defined

- **WHEN** the app is built
- **THEN** `res/values/colors.xml` SHALL also contain: `md_theme_dark_primary`
  (#4A90E2), `md_theme_dark_onPrimary` (#000000), `md_theme_dark_surface`
  (#121212), `md_theme_dark_onSurface` (#E3E3E3),
  `md_theme_dark_onSurfaceVariant` (#A0A0A0), `md_theme_dark_error` (#F2B8B5),
  `md_theme_dark_outline` (#444746)

## Requirement: Theme Attribute Configuration

The app's `themes.xml` (light) and `values-night/themes.xml` (dark) SHALL
override Material 3 theme attributes (`colorPrimary`, `colorOnPrimary`,
`colorSurface`, `colorOnSurface`, `colorOnSurfaceVariant`, `colorError`,
`colorOutline`) referencing the appropriate light or dark color resources.

### Scenario: Light theme attributes set

- **WHEN** the app runs in light mode
- **THEN** `Theme.ZeroMeeting` SHALL resolve `colorPrimary` to `#1877F2`,
  `colorSurface` to `#FFFFFF`, `colorOnSurface` to `#1E1E1E`, `colorError` to
  `#B3261E`, and `colorOutline` to `#E0E0E0`

### Scenario: Dark theme attributes set

- **WHEN** the app runs in dark mode (system dark mode enabled)
- **THEN** `Theme.ZeroMeeting` SHALL resolve `colorPrimary` to `#4A90E2`,
  `colorSurface` to `#121212`, `colorOnSurface` to `#E3E3E3`, `colorError` to
  `#F2B8B5`, and `colorOutline` to `#444746`

## Requirement: Spacing Tokens

The app SHALL define reusable spacing dimensions in `res/values/dimens.xml`.

### Scenario: Standard spacing values defined

- **WHEN** the app is built
- **THEN** `dimens.xml` SHALL contain: `spacing_xs` (4dp), `spacing_sm` (8dp),
  `spacing_md` (16dp), `spacing_lg` (24dp), `spacing_xl` (32dp),
  `touch_target_min` (48dp), `corner_radius_md` (8dp), `corner_radius_lg` (12dp)

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
