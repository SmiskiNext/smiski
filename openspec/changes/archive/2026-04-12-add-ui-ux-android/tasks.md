# Tasks

## 1. Color System & Theme Foundation (WP1)

- [x] 1.1 Define complete color palette in `res/values/colors.xml`: add
      `md_theme_light_primary` (#1877F2), `md_theme_light_onPrimary` (#FFFFFF),
      `md_theme_dark_primary` (#4A90E2), `md_theme_dark_onPrimary` (#000000),
      `md_theme_light_surface` (#FFFFFF), `md_theme_light_onSurface` (#1E1E1E),
      `md_theme_light_onSurfaceVariant` (#666666), `md_theme_dark_surface`
      (#121212), `md_theme_dark_onSurface` (#E3E3E3),
      `md_theme_dark_onSurfaceVariant` (#A0A0A0), `md_theme_light_error`
      (#B3261E), `md_theme_dark_error` (#F2B8B5), `md_theme_light_outline`
      (#E0E0E0), `md_theme_dark_outline` (#444746), `google_blue` (#4285F4).
      Keep existing `black`/`white` entries.
- [x] 1.2 Update `res/values/themes.xml` light theme: uncomment and set
      `colorPrimary`→`@color/md_theme_light_primary`,
      `colorOnPrimary`→`@color/md_theme_light_onPrimary`,
      `colorSurface`→`@color/md_theme_light_surface`,
      `colorOnSurface`→`@color/md_theme_light_onSurface`,
      `colorOnSurfaceVariant`→`@color/md_theme_light_onSurfaceVariant`,
      `colorError`→`@color/md_theme_light_error`,
      `colorOutline`→`@color/md_theme_light_outline`
- [x] 1.3 Update `res/values-night/themes.xml` dark theme: set same attributes
      using dark color refs (`md_theme_dark_*`)
- [x] 1.4 Create `res/drawable-night/bg_login_header.xml` with dark gradient
      (`#002A5A` → `@color/md_theme_dark_surface`). Update light
      `res/drawable/bg_login_header.xml` to reference color resources instead of
      hardcoded hex
- [x] 1.5 Create `res/values/dimens.xml`: `spacing_xs` (4dp), `spacing_sm`
      (8dp), `spacing_md` (16dp), `spacing_lg` (24dp), `spacing_xl` (32dp),
      `touch_target_min` (48dp), `corner_radius_md` (8dp), `corner_radius_lg`
      (12dp)
- [x] 1.6 Build verification: `./gradlew :app:assembleDebug` passes

## 2. i18n — String Resources (WP2)

- [x] 2.1 Add English string resources to `res/values/strings.xml`. Group by
      section: **Login screen** (`login_title` "Welcome Back", `login_subtitle`
      "Sign in to access your AI meetings", `login_label_email` "Email",
      `login_hint_email` "<name@company.com>", `login_label_password`
      "Password", `login_hint_password` "........", `login_forgot_password`
      "Forgot password?", `login_btn_sign_in` "Sign In",
      `login_or_continue_with` "OR CONTINUE WITH", `login_btn_google` "Sign in
      with Google", `login_need_account` "Don't have an account? Sign up",
      `login_forgot_password_coming_soon` "Password recovery coming soon");
      **Register screen** (`register_title` "Create Account",
      `register_hint_full_name` "Enter your full name", `register_hint_username`
      "Choose a username (letters, digits, \_ or -)", `register_hint_email`
      "Enter your email address", `register_hint_password` "Enter your
      password", `register_hint_confirm_password` "Confirm your password",
      `register_terms` "I agree to the Terms of Service and Privacy Policy.",
      `register_btn_create` "Create Account", `register_have_account` "Already
      have an account? Sign In", `register_success_message` "Account created!
      Please sign in."); **Accessibility** (`navigate_up` "Navigate up");
      **Validation** (`validation_required` "This field is required",
      `validation_invalid_format` "Invalid format",
      `validation_passwords_mismatch` "Passwords do not match",
      `validation_terms_required` "You must agree to the Terms of Service and
      Privacy Policy", `validation_failed` "Please fix the errors above",
      `validation_too_short` "Value is too short", `validation_too_long` "Value
      is too long", `validation_invalid_value` "Invalid value"); **Errors**
      (`error_server` "Something went wrong. Please try again later.",
      `error_network` "No internet connection. Please check your network and try
      again.", `error_unknown` "An unexpected error occurred.",
      `error_google_signin_failed` "Google sign-in failed. Please try again.",
      `error_validation` "Validation failed"); **Backend error translations**
      (`error_invalid_credentials` "Invalid email or password",
      `error_email_already_exists` "Email address is already in use",
      `error_username_already_exists` "Username is already taken",
      `error_user_deleted` "This account has been deleted",
      `error_invalid_firebase_token` "Google sign-in failed. Please try again.",
      `error_firebase_auth` "Service unavailable. Please try again later.")
- [x] 2.2 Create `res/values-vi/strings.xml` with Vietnamese translations for
      all entries from 2.1
- [x] 2.3 Replace hardcoded strings in `fragment_login.xml`: all `android:text`,
      `android:hint`, `android:contentDescription` → `@string/` references
- [x] 2.4 Replace hardcoded strings in `fragment_register.xml`: all
      `android:text`, `android:hint`, `android:contentDescription` → `@string/`
      references
- [x] 2.5 Replace hardcoded strings in `LoginFragment.java`:
      `setText("Sign In")` → `setText(R.string.login_btn_sign_in)`, Toast text →
      `getString(R.string.*)`, `showGeneralError("...")` →
      `showGeneralError(getString(R.string.*))`
- [x] 2.6 Replace hardcoded strings in `RegisterFragment.java`:
      `setText("Create Account")` → `setText(R.string.register_btn_create)`

## 3. ErrorTranslator & ViewModel Refactor (WP3)

- [x] 3.1 Create `data/remote/interceptor/AndroidErrorTranslator.java`:
      implements `ErrorTranslator`, injects `@ApplicationContext Context`.
      Internal `Map<String, Integer> CODE_MAP` using `Map.ofEntries()` mapping
      top-level codes (INVALID_CREDENTIALS→`R.string.error_invalid_credentials`,
      EMAIL_ALREADY_EXISTS→`R.string.error_email_already_exists`,
      USERNAME_ALREADY_EXISTS→`R.string.error_username_already_exists`,
      USER_DELETED→`R.string.error_user_deleted`,
      INVALID_FIREBASE_TOKEN→`R.string.error_invalid_firebase_token`,
      FIREBASE_AUTH_ERROR→`R.string.error_firebase_auth`,
      VALIDATION_ERROR→`R.string.error_validation`) AND violation codes
      (REQUIRED→`R.string.validation_required`,
      INVALID_FORMAT→`R.string.validation_invalid_format`,
      TOO_SHORT→`R.string.validation_too_short`,
      TOO_LONG→`R.string.validation_too_long`,
      INVALID_VALUE→`R.string.validation_invalid_value`). `translate()`: lookup
      in map, found → `context.getString(resId)`, else → `defaultMessage`
- [x] 3.2 Update `JsendUnwrapInterceptor.handleFail()` (line ~128-131): change
      violation message parsing to
      `String translatedViolationMsg = translator.translate(vCode, rawMsg);`
      before adding to violations list. Also in `handleError()`: translate
      fallback "An unexpected server error occurred" via
      `translator.translate("SERVER_ERROR", "An unexpected server error occurred")`
- [x] 3.3 Update `NetworkModule.provideErrorTranslator()`: replace
      `return ErrorTranslator.DEFAULT;` with providing `AndroidErrorTranslator`.
      Either change method signature to inject `@ApplicationContext Context` and
      return `new AndroidErrorTranslator(context)`, or use `@Binds` pattern
- [x] 3.4 Refactor `FieldError.java`: add 2-arg constructor
      `public FieldError(String field, String code) { this(field, null, code); }`;
      update Javadoc
- [x] 3.5 Refactor `LoginViewModel.java`: (a) client-side validation →
      `new FieldError("email", "REQUIRED")` (code-only); (b) `UiError.Fail` for
      validation → `new UiError.Fail("VALIDATION", null, fieldErrors)`
      (message=null); (c) remove `mapApiFail()` method entirely — backend
      `ApiFailException` catch block becomes:
      `new UiError.Fail(e.getCode(), e.getMessage(), fieldErrors)`
      (pass-through); (d) ServerError/NetworkError/Unknown catch blocks keep a
      fallback English string (Fragment will override)
- [x] 3.6 Refactor `RegisterViewModel.java`: same pattern as 3.5 — code-only
      FieldError, remove `mapApiFail()`, pass-through backend errors, null
      message for client validation Fail
- [x] 3.7 Update `LoginFragment.handleError()` and
      `RegisterFragment.handleError()`: (a) for `UiError.Fail`: field errors →
      `String msg = fe.message() != null ? fe.message() : resolveValidationMessage(fe.code()); tilEmail.setError(msg);`
      (b) general error:
      `fail.message() != null ? fail.message() : getString(R.string.error_validation)`
      (c) `UiError.ServerError` →
      `showGeneralError(getString(R.string.error_server))` (d)
      `UiError.NetworkError` →
      `showGeneralError(getString(R.string.error_network))` (e)
      `UiError.Unknown` → `showGeneralError(getString(R.string.error_unknown))`.
      Add private helper `resolveValidationMessage(String code)` with switch:
      REQUIRED→`R.string.validation_required`,
      FORMAT→`R.string.validation_invalid_format`,
      MISMATCH→`R.string.validation_passwords_mismatch`,
      default→`R.string.validation_invalid_value`

## 4. Dark Mode — Auth Layouts (WP4)

- [x] 4.1 Replace hardcoded colors in `fragment_login.xml`:
      `android:background="@android:color/white"` (ScrollView) →
      `android:background="?attr/colorSurface"`;
      `android:backgroundTint="#FFFFFF"` (btnBack) → `?attr/colorSurface`;
      `android:backgroundTint="#4285F4"` (imgLogo) → `?attr/colorPrimary`;
      `android:textColor="@android:color/black"` → `?attr/colorOnSurface`;
      `android:textColor="#666666"` → `?attr/colorOnSurfaceVariant`;
      `app:backgroundTint="#4285F4"` (btnLoginSubmit) → `?attr/colorPrimary`;
      `android:textColor="#4285F4"` (tvForgotPassword) → `?attr/colorPrimary`;
      `android:textColor="#D32F2F"` (tvGeneralError) → `?attr/colorError`;
      `android:background="#E0E0E0"` (dividers) → `?attr/colorOutline`;
      `android:textColor="#999999"` → `?attr/colorOnSurfaceVariant`;
      `app:strokeColor="#E0E0E0"` (btnGoogle) → `?attr/colorOutline`;
      `android:textColor="@android:color/black"` (btnGoogle) →
      `?attr/colorOnSurface`
- [x] 4.2 Replace hardcoded colors in `fragment_register.xml`:
      `android:background="@android:color/white"` → `?attr/colorSurface`;
      `android:textColor="@android:color/black"` → `?attr/colorOnSurface`;
      `app:backgroundTint="#4285F4"` → `?attr/colorPrimary`;
      `android:textColor="#D32F2F"` → `?attr/colorError`;
      `android:textColor="#666666"` → `?attr/colorOnSurfaceVariant`
- [x] 4.3 Verify `activity_auth.xml` and `nav_graph_auth.xml` have no hardcoded
      colors (nav_graph has `android:label` only — OK; activity_auth has
      NavHostFragment — check background)
- [x] 4.4 Build verification: `./gradlew :app:assembleDebug` passes

## 5. UX Improvements (WP5)

- [x] 5.1 Create `res/drawable/ic_google_logo.xml` — Google "G" vector drawable
      (official multicolor: blue #4285F4, red #EA4335, yellow #FBBC05, green
      #34A853). Update `btnGoogle` in `fragment_login.xml`: add
      `app:icon="@drawable/ic_google_logo"`, `app:iconGravity="textStart"`,
      `app:iconTint="@null"`, change text to `@string/login_btn_google`
- [x] 5.2 Fix touch targets in both fragments: `btnBack` →
      `android:layout_width="@dimen/touch_target_min"`,
      `android:layout_height="@dimen/touch_target_min"`,
      `android:background="?attr/selectableItemBackgroundBorderless"`,
      `android:contentDescription="@string/navigate_up"`. `tvForgotPassword` →
      `android:minHeight="@dimen/touch_target_min"`.
      `tvNeedAccount`/`tvHaveAccount` →
      `android:minHeight="@dimen/touch_target_min"`
- [x] 5.3 Accessibility fixes: `imgLogo` →
      `android:importantForAccessibility="no"` (remove contentDescription).
      `tvForgotPassword` → add `android:focusable="true"`,
      `android:clickable="true"`
- [x] 5.4 Terms checkbox ClickableSpan in `RegisterFragment.java`: build
      SpannableString from `getString(R.string.register_terms)`, find "Terms of
      Service" and "Privacy Policy" substrings, set ClickableSpan on each (open
      browser TODO or Snackbar placeholder), call `cbTerms.setText(spannable)`,
      `cbTerms.setMovementMethod(LinkMovementMethod.getInstance())`. Remove
      hardcoded text from `fragment_register.xml` checkbox `android:text`
      attribute (set programmatically)
- [x] 5.5 "Forgot password?" graceful disable in `LoginFragment.java`: replace
      Toast with `tvForgotPassword.setAlpha(0.5f)` in `onViewCreated()`, click
      listener →
      `Snackbar.make(view, R.string.login_forgot_password_coming_soon, Snackbar.LENGTH_SHORT).show()`
- [x] 5.6 Remove redundant labels in `fragment_register.xml`: delete
      `lblFullName`, `lblUsername`, `lblEmail`, `lblPassword`,
      `lblConfirmPassword` TextViews. Re-chain ConstraintLayout constraints:
      each `TextInputLayout`'s `app:layout_constraintTop_toBottomOf` points to
      the previous `TextInputLayout` (or `btnBack` for the first). Verify
      floating hints display correctly as labels
- [x] 5.7 Error recovery TextWatcher in `LoginFragment.java` and
      `RegisterFragment.java`: add `doOnTextChanged` (or
      `TextWatcher.onTextChanged`) on each EditText — when text changes, clear
      that field's TIL error and hide `tvGeneralError` if visible.
      LoginFragment: `edtEmail` clears `tilEmail`, `edtPassword` clears
      `tilPassword`. RegisterFragment: `edtFullName`→`tilFullName`,
      `edtUsername`→`tilUsername`, `edtEmail`→`tilEmail`,
      `edtPassword`→`tilPassword`, `edtConfirmPassword`→`tilConfirmPassword`
- [x] 5.8 Login success feedback in `LoginFragment.java`: on `UiState.Success`,
      set `btnLoginSubmit.setText("✓")` and
      `btnLoginSubmit.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(android.R.color.holo_green_dark, null)))`,
      then
      `new Handler(Looper.getMainLooper()).postDelayed(() -> navigateToDashboard(), 400)`.
      Register success in `RegisterFragment.java`: on `UiState.Success`, show
      `Snackbar.make(requireView(), R.string.register_success_message, Snackbar.LENGTH_SHORT).show()`,
      then
      `new Handler(Looper.getMainLooper()).postDelayed(() -> navigateToLogin(), 800)`
- [x] 5.9 Material typography in both layout XMLs: replace
      `android:textSize="28sp"` →
      `android:textAppearance="?attr/textAppearanceHeadlineMedium"`,
      `android:textSize="20sp"` →
      `android:textAppearance="?attr/textAppearanceTitleLarge"`,
      `android:textSize="16sp"` →
      `android:textAppearance="?attr/textAppearanceBodyLarge"`,
      `android:textSize="14sp"` →
      `android:textAppearance="?attr/textAppearanceBodyMedium"`,
      `android:textSize="12sp"` →
      `android:textAppearance="?attr/textAppearanceLabelMedium"`. Remove
      explicit `android:textSize` when textAppearance is set (textAppearance
      includes size)

## 6. Final Verification

- [x] 6.1 Build verification: `./gradlew :app:assembleDebug` passes with zero
      errors
- [x] 6.2 Update `app/codemap.md`: add `AndroidErrorTranslator.java` to
      data/remote/interceptor section, add `res/values-vi/strings.xml` and
      `res/values/dimens.xml` to resources section, note FieldError
      dual-constructor pattern
