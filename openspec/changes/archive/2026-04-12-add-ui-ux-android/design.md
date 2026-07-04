# Context

The `add-auth` change delivered functional auth screens (Login, Register, Google
Sign-In) but deferred UI polish, i18n, and accessibility. The Android app uses
`Theme.Material3.DayNight.NoActionBar` yet no Material color attributes are
configured — every layout hardcodes hex colors and English strings. The
`ErrorTranslator` i18n hook exists at the interceptor layer
(`JsendUnwrapInterceptor`) but is wired to a pass-through default.

Key files (per `codemap.md`):

- `res/values/colors.xml` — only 2 entries (black, white)
- `res/values/themes.xml` — `colorPrimary` commented out
- `res/values/strings.xml` — only `app_name`
- `ErrorTranslator.java` — `@FunctionalInterface` with
  `translate(code, defaultMessage)`
- `JsendUnwrapInterceptor.java` — calls `translator.translate(code, message)` on
  top-level error code only; violation messages pass through raw
- `NetworkModule.java` — provides `ErrorTranslator.DEFAULT` (pass-through)
- `FieldError.java` — `record(String field, String message, String code)`
- `LoginViewModel.java` / `RegisterViewModel.java` — hardcoded English strings
  for validation messages, error messages, and `mapApiFail()` switch overrides

Backend error code enums (already sending machine-readable codes):

- `AuthErrorCode`: INVALID_CREDENTIALS, EMAIL_ALREADY_EXISTS,
  USERNAME_ALREADY_EXISTS, USER_DELETED, INVALID_FIREBASE_TOKEN,
  FIREBASE_AUTH_ERROR, etc.
- `ViolationCode`: REQUIRED, INVALID_FORMAT, TOO_SHORT, TOO_LONG, INVALID_VALUE
- `CommonErrorCode`: VALIDATION_ERROR

## Goals / Non-Goals

**Goals:**

- Establish a Material 3 color system (light + dark) eliminating all hardcoded
  hex colors in auth layouts
- Extract all hardcoded English strings from auth XML layouts, Fragments, and
  ViewModels into `strings.xml` with Vietnamese translations (`values-vi/`)
- Implement `AndroidErrorTranslator` that translates both top-level error codes
  and field-level violation codes to locale-specific strings
- Update `JsendUnwrapInterceptor.handleFail()` to translate violation messages
  through `ErrorTranslator`
- Refactor ViewModels to remove hardcoded English error messages; client-side
  validation uses code-only `FieldError`, Fragment resolves localized strings
- Fix all identified UX issues: Google button branding, touch targets (48dp),
  contrast (WCAG AA), clickable Terms/Privacy, error recovery, success feedback
- Achieve dark-mode readiness for auth screens

**Non-Goals:**

- Extracting strings from non-auth screens (Dashboard, Schedule, Meeting Room,
  etc.) — project-wide i18n is a separate future change
- Dark-mode fixes for non-auth screens — same scope boundary
- Implementing Forgot Password flow — stays as gracefully disabled stub
- Adding Lottie animations — dependency available but not used in this change
- Unit/integration tests — no test infrastructure in the Android app yet
- Web frontend changes — not in scope

## Decisions

### D1: Single `ErrorTranslator` for both top-level and violation codes

**Choice:** Use one `Map<String, @StringRes Integer>` inside
`AndroidErrorTranslator` mapping both `AuthErrorCode` names and `ViolationCode`
names to Android string resources. The `translate(code, defaultMessage)` method
looks up the code; if found, returns `context.getString(resId)`; otherwise
returns `defaultMessage`.

**Why:** Top-level code namespace (INVALID_CREDENTIALS, EMAIL_ALREADY_EXISTS...)
and violation code namespace (REQUIRED, INVALID_FORMAT...) do not overlap.
Verified by inspection of all backend error code enums. A single map avoids the
complexity of two separate translation mechanisms.

**Alternatives considered:**

- Separate `ViolationTranslator` interface — rejected because the existing
  `ErrorTranslator` contract (`translate(String code, String defaultMessage)`)
  already accommodates both; adding a new interface increases surface area
  without benefit.
- Translate in ViewModel instead of interceptor — rejected because it would
  require `AndroidViewModel` (coupling to Android framework) or injecting
  `Context` into ViewModels, violating Clean Architecture. Interceptor already
  has `Context` via Hilt `@ApplicationContext`.

### D2: Violation messages translated at interceptor level

**Choice:** Modify `JsendUnwrapInterceptor.handleFail()` to call
`translator.translate(violationCode, rawMessage)` for each violation before
constructing `ApiFailException.Violation`. This means all error messages
arriving at the ViewModel are already locale-translated.

**Why:** Centralizes translation in one place (interceptor). ViewModels become
pure pass-through — no `mapApiFail()` switch needed. Consistent with web
frontend pattern where `createJsendMiddleware(translator)` translates at the
middleware layer.

**Risk:** Parameterized messages from backend (e.g., "size must be between 3 and
50") lose their specific bounds when replaced by a generic translation like
"Value is too short". Mitigation: for codes where we have a translation, use it;
for unknown codes, `translate()` falls back to `defaultMessage` (the backend's
English message), preserving specifics.

### D3: FieldError dual-constructor pattern (code-only for client-side)

**Choice:** Add a convenience constructor
`FieldError(String field, String code)` that sets `message = null`. Fragment
checks: if `message != null`, display it (backend-translated); if
`message == null`, resolve from code via `resolveValidationMessage(code)` →
`getString(R.string.*)`.

**Why:** Keeps `FieldError` as a single type (no sealed hierarchy needed). The
null-message convention clearly signals "client-side, resolve locally" vs
"backend-provided, already translated". Backward compatible — existing 3-arg
constructor unchanged.

**Alternatives considered:**

- Sealed type `ErrorMessage { record Raw(String), record Resource(int) }` —
  rejected as overkill for this use case; adds type complexity for a simple
  null-check.
- `@StringRes int messageResId` field — rejected because FieldError is shared
  code (presentation/common) and mixing resource IDs with String messages
  creates awkward dual handling everywhere.

### D4: Fragment resolves ServerError/NetworkError/Unknown messages

**Choice:** ViewModels continue to set a fallback English string in
`UiError.ServerError(message)`, `UiError.NetworkError(message)`, and
`UiError.Unknown(message)`. But Fragments **ignore** this message and instead
display their own localized string based on the UiError subtype:

```java
case UiError.ServerError s  -> showGeneralError(getString(R.string.error_server));
case UiError.NetworkError n -> showGeneralError(getString(R.string.error_network));
case UiError.Unknown u      -> showGeneralError(getString(R.string.error_unknown));
```

**Why:** Avoids changing the `UiError` sealed interface signature (which would
affect all consumers). ViewModels don't need `Context`. The Fragment is the
natural place to resolve locale-specific strings since it has `getString()`.

### D5: Color system — `#1877F2` as primary, `#4285F4` reserved for Google

**Choice:** App-wide primary color is `#1877F2`. Auth screens switch from
`#4285F4` to `?attr/colorPrimary`. The Google Sign-In button keeps `#4285F4` via
a dedicated `@color/google_blue` resource (per Google branding guidelines).

**Why:** Consistency. Welcome, Dashboard, Schedule already use `#1877F2`. Auth
was the outlier.

### D6: Dark mode gradient for login header

**Choice:** Create `res/drawable-night/bg_login_header.xml` with dark gradient
(`#002A5A` → `@color/md_theme_dark_surface`). Light version references
`@color/md_theme_light_primary` tints.

**Why:** Drawable XML cannot use `?attr/` theme attributes directly in gradient
`startColor`/`endColor`. Must use `@color/` references with separate light/dark
resource qualifiers.

## Risks / Trade-offs

- **Risk:** `ErrorTranslator` is a singleton (provided in `NetworkModule` as
  `@Singleton`). It uses `@ApplicationContext Context`, which uses the app's
  default locale. If the user changes language at runtime without restarting the
  app, cached translations may be stale. → **Mitigation:** `Context.getString()`
  respects the current configuration at call time. Since `translate()` calls
  `context.getString(resId)` on each invocation (not caching the result), locale
  changes take effect on the next API call.

- **Risk:** Register form simplification (removing redundant labels) changes
  visual appearance. Users accustomed to the current layout may notice. →
  **Mitigation:** Material `TextInputLayout` floating hints provide the same
  label functionality with less visual clutter. This is standard Material Design
  practice.

- **Risk:** Success feedback delay (400ms) on login may feel slow to some users.
  → **Mitigation:** Keep it brief (400ms, not seconds). Can be tuned or removed
  later without architectural impact.
