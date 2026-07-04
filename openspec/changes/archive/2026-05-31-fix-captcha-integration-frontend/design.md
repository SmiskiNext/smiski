## Context

The backend forgot-password flow (redesigned in the
`2026-05-30-redesign-forgot-password-fullstack` change) returns error code
`CAPTCHA_REQUIRED` after 2 requests from the same email within 1 hour. The
`RequestPasswordResetUseCase` checks `attemptTracker.countAttemptsSince >= 2`
and rejects requests missing a valid `captchaToken`.

**Current state:**

- Backend: Fully implemented. `GoogleRecaptchaVerifier` (production, calls
  Google siteverify API) and `NoOpCaptchaVerifier` (dev, always returns true)
  are conditionally loaded via `app.recaptcha.enabled` property.
- Web SDK: `UserManagementForgotPasswordRequest` already has
  `captchaToken?: string` field.
- Web hook (`use-forgot-password.ts`): Catches errors generically via
  `error.message` — does not inspect `ApiFailError.code`.
- Android: `AuthRepositoryImpl.forgotPassword(email)` sends no captchaToken.
  `ForgotPasswordViewModel` handles `ApiFailException` but has no
  CAPTCHA-specific logic.
- K8s/Docker: No `RECAPTCHA_*` env vars provisioned.

**Constraints:**

- Must not change backend behavior (already correct and deployed).
- Must follow existing error handling patterns (JSend middleware on web,
  JsendUnwrapInterceptor on Android).
- Android uses auto-generated Retrofit API from OpenAPI spec — the generated
  `UserManagementForgotPasswordRequest` DTO already has `captchaToken` setter.
- Web uses Biome for formatting (not ESLint/Prettier).
- Android uses Hilt DI and MVVM pattern.

## Goals / Non-Goals

**Goals:**

- Unblock users stuck on `CAPTCHA_REQUIRED` error in forgot-password flow
- Integrate reCAPTCHA v3 (invisible) for zero-friction captcha resolution
- Provide reCAPTCHA v2 (checkbox) fallback when v3 score is too low
- Provision infrastructure env vars for production reCAPTCHA activation
- Add i18n error messages for captcha-related errors

**Non-Goals:**

- Modify backend captcha logic or thresholds
- Implement reCAPTCHA Enterprise
- Add captcha to other flows (login, register)
- Handle captcha for the verify-OTP or reset-password endpoints (only
  forgot-password triggers captcha)

## Decisions

### 1. Auto-retry Pattern on CAPTCHA_REQUIRED

**Decision:** When `submitEmail` or `resendOtp` receives `CAPTCHA_REQUIRED`
error, automatically execute reCAPTCHA v3 and retry the same request with the
obtained token — without user interaction.

**Rationale:** reCAPTCHA v3 is invisible and score-based. Legitimate users
should never see a captcha challenge. Auto-retry provides seamless UX while
satisfying the backend's captcha requirement.

**Alternatives considered:**

- Always include captcha token on every request: Adds unnecessary latency and
  Google API calls for the majority of requests that don't need captcha.
- Show captcha widget preemptively after first request: Adds friction before
  it's needed.

### 2. V2 Checkbox Fallback on CAPTCHA_INVALID

**Decision:** If the retry with v3 token returns `CAPTCHA_INVALID` (score below
0.5), render a visible reCAPTCHA v2 checkbox widget. User solves it manually,
then the request retries with the v2 token.

**Rationale:** v3 can reject legitimate users in certain environments (VPN,
shared IPs, browser privacy settings). v2 checkbox provides a deterministic
fallback that the user can always solve.

**Flow:**

```
Request → CAPTCHA_REQUIRED → execute v3 → retry
                                            ↓
                              CAPTCHA_INVALID → show v2 checkbox
                                                      ↓
                                              user solves → retry with v2 token
                                                      ↓
                                              success or show error
```

### 3. Web: Custom Hook Architecture

**Decision:** Create `useRecaptcha` hook that manages script loading, v3
execution, and v2 widget rendering. Modify `useForgotPassword` to detect
`CAPTCHA_REQUIRED` via `ApiFailError.code` and coordinate with `useRecaptcha`.

**Implementation:**

- `useRecaptcha(siteKey)` returns `{ executeV3, renderV2, v2Token, isReady }`.
- `useForgotPassword` gains internal state:
  `captchaState: 'none' | 'v3-pending' | 'v2-required'`.
- On `CAPTCHA_REQUIRED`: set state to `v3-pending`, call `executeV3()`, retry.
- On `CAPTCHA_INVALID` after v3 retry: set state to `v2-required`, render
  checkbox in modal.
- On v2 solve: retry with v2 token, reset captcha state on success.

**Script loading:** Load `https://www.google.com/recaptcha/api.js` dynamically
on first need (not on page load) to avoid performance impact.

### 4. Android: SafetyNet reCAPTCHA API

**Decision:** Use `com.google.android.gms:play-services-safetynet` for reCAPTCHA
on Android. Execute via
`SafetyNet.getClient(activity).verifyWithRecaptcha(siteKey)`.

**Rationale:** This is Google's recommended approach for Android reCAPTCHA
integration. It handles both invisible (v3-like) and interactive (v2-like)
challenges natively.

**Implementation:**

- Add dependency:
  `implementation("com.google.android.gms:play-services-safetynet:18.1.0")`
- `ForgotPasswordViewModel` detects `CAPTCHA_REQUIRED` from
  `ApiFailException.getCode()`.
- Posts a `SingleLiveEvent` to Fragment requesting captcha execution.
- Fragment calls SafetyNet API (requires Activity context), returns token to
  ViewModel.
- ViewModel retries `forgotPassword(email, captchaToken)`.
- On `CAPTCHA_INVALID`: SafetyNet automatically shows interactive challenge.

**Layer changes:**

- `AuthRepository.forgotPassword(email)` → `forgotPassword(email, captchaToken)`
  (nullable param)
- `RequestPasswordResetUseCase.execute(email)` → `execute(email, captchaToken)`
  (nullable param)
- `ForgotPasswordViewModel` gains captcha state management.

### 5. Environment Variable Provisioning

**Decision:** Add reCAPTCHA env vars to K8s secrets, deployment manifest, and
Docker `.env.example`. Default `RECAPTCHA_ENABLED=false` so deployment is safe.

**Variables:**

| Variable                         | Where              | Purpose                            |
| -------------------------------- | ------------------ | ---------------------------------- |
| `RECAPTCHA_ENABLED`              | K8s deployment env | Toggle verifier bean               |
| `RECAPTCHA_SITE_KEY`             | K8s deployment env | Public key (safe in env)           |
| `RECAPTCHA_SECRET_KEY`           | K8s secret         | Private key for server-side verify |
| `NEXT_PUBLIC_RECAPTCHA_SITE_KEY` | Web `.env.local`   | Client-side site key               |

## Risks / Trade-offs

### Risk: reCAPTCHA Script Blocking

**Description:** Google's reCAPTCHA script could be blocked by ad blockers or
corporate firewalls.

**Mitigation:** If script fails to load, skip captcha entirely and show a
user-friendly error message suggesting the user disable ad blockers or try a
different network. The `useRecaptcha` hook handles load failures gracefully.

### Risk: SafetyNet Deprecation

**Description:** Google has deprecated SafetyNet in favor of Play Integrity API.
However, the reCAPTCHA component (`verifyWithRecaptcha`) remains supported and
is separate from the attestation APIs.

**Mitigation:** `play-services-safetynet:18.1.0` reCAPTCHA functionality is
still actively maintained. Migration to Play Integrity reCAPTCHA can be done
later without architectural changes.

### Risk: Race Condition on Auto-retry

**Description:** If user triggers another action while v3 auto-retry is
in-flight, could cause duplicate requests.

**Mitigation:** `isLoading` state prevents user interaction during retry. Both
web hook and Android ViewModel set loading state before retry.

### Trade-off: Script Load Latency

**Description:** Loading reCAPTCHA script on-demand adds ~200-500ms latency on
first captcha execution.

**Mitigation:** Acceptable because captcha is only needed after 2+ failed
attempts — not on the critical path for normal users. Preloading would add
unnecessary weight to every page load.
