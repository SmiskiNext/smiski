# Why

The Android auth screens (Login, Register) implemented in `add-auth` contain ~61
hardcoded English strings, ~13 hardcoded hex colors, and zero
accessibility/dark-mode support. The Material 3 DayNight theme is configured but
`colorPrimary` is not set; auth screens use `#4285F4` (Google blue) while the
rest of the app uses `#1877F2`. The existing `ErrorTranslator` i18n hook in
`JsendUnwrapInterceptor` is wired to a pass-through default and does not
translate backend error codes or field-level violation codes. These gaps block
localization (Vietnamese), break dark mode, and fail WCAG 2.1 AA touch-target
and contrast requirements.

## What Changes

- Define a Material 3 color system (`colors.xml`, `themes.xml` light + dark)
  replacing all hardcoded hex values in auth layouts with theme attributes.
- Extract ~61 hardcoded strings from auth XML layouts, Fragments, and ViewModels
  into `values/strings.xml` (English) and `values-vi/strings.xml` (Vietnamese).
- Implement `AndroidErrorTranslator` (replacing the pass-through default) that
  translates both top-level backend error codes (`INVALID_CREDENTIALS`,
  `EMAIL_ALREADY_EXISTS`, etc.) and field-level violation codes (`REQUIRED`,
  `INVALID_FORMAT`, etc.) to locale-specific strings via Android resources.
- Update `JsendUnwrapInterceptor.handleFail()` to translate violation-level
  messages through `ErrorTranslator` (currently only top-level code is
  translated).
- Refactor `FieldError` record to support code-only construction (client-side
  validation) so ViewModels no longer contain user-facing English strings.
- Refactor `LoginViewModel` and `RegisterViewModel` to remove hardcoded error
  messages: client-side validation uses code-only `FieldError`, backend errors
  pass through translated messages from `ErrorTranslator`, and
  ServerError/NetworkError/Unknown types are resolved to localized strings at
  the Fragment layer.
- Fix 10 UX issues: add Google "G" icon to sign-in button, fix touch targets to
  48dp minimum (btnBack, links), fix color contrast (WCAG AA), make
  Terms/Privacy clickable spans, gracefully disable "Forgot password?", remove
  redundant labels on Register (use floating hints), add TextWatcher
  error-clearing, add login/register success feedback, and apply Material type
  scale.

## Capabilities

### New Capabilities

- `android-design-system`: Color palette, theme attributes (light + dark),
  spacing tokens, and Material type scale for the Android app. Foundation for
  all UI work.
- `android-i18n-auth`: i18n string extraction for auth flow (en + vi), including
  `AndroidErrorTranslator` implementation that translates backend error codes
  and violation codes to locale-specific messages via Android string resources.
- `android-auth-ux`: UX improvements for Login and Register screens: Google
  button branding, accessibility (touch targets, contrast, screen reader),
  Terms/Privacy clickable spans, error recovery (TextWatcher), success feedback,
  "Forgot password?" graceful disable, redundant label removal, Material
  typography.

### Modified Capabilities

- `android-auth`: Auth flow error handling changes. `FieldError` gains code-only
  constructor. ViewModels stop producing hardcoded English error messages.
  Fragments resolve localized messages from error codes/types. `handleFail()` in
  interceptor now translates violation messages. `NetworkModule` provides
  `AndroidErrorTranslator` instead of `ErrorTranslator.DEFAULT`.

## Impact

- **Files modified** (~20):
    - `res/values/colors.xml`, `res/values/themes.xml`,
      `res/values-night/themes.xml` — color system
    - `res/values/strings.xml`, `res/values-vi/strings.xml` (new) — i18n
    - `res/values/dimens.xml` (new) — spacing tokens
    - `res/layout/fragment_login.xml`, `res/layout/fragment_register.xml` — dark
      mode + i18n + UX
    - `res/drawable/bg_login_header.xml`,
      `res/drawable-night/bg_login_header.xml` (new) — dark mode gradient
    - `res/drawable/ic_google_logo.xml` (new) — Google button icon
    - `LoginFragment.java`, `RegisterFragment.java` — i18n + UX
    - `LoginViewModel.java`, `RegisterViewModel.java` — error refactor
    - `FieldError.java` — code-only constructor
    - `JsendUnwrapInterceptor.java` — violation translation
    - `AndroidErrorTranslator.java` (new) — i18n translator
    - `NetworkModule.java` — wire translator
- **Dependencies**: None new. Uses existing `ErrorTranslator` interface.
- **Breaking changes**: None. `FieldError` gains a new constructor; existing
  3-arg constructor unchanged. `ErrorTranslator` interface unchanged.
- **Backend**: No changes required. Backend already sends machine-readable
  codes.
