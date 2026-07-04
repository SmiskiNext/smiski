## 1. Web — reCAPTCHA Hook

- [x] 1.1 Create `frontends/web/src/hooks/use-recaptcha.ts` hook with: dynamic
      script loading, `executeV3(action)` returning token, v2 widget
      render/callback, `isReady` state, error handling for script load failures
- [x] 1.2 Add `NEXT_PUBLIC_RECAPTCHA_SITE_KEY` to
      `frontends/web/.env.local.example` with documentation comment ← (verify:
      env var documented and hook reads it correctly)

## 2. Web — Forgot Password Captcha Integration

- [x] 2.1 Modify `frontends/web/src/hooks/use-forgot-password.ts`: detect
      `CAPTCHA_REQUIRED` from `ApiFailError.code` in `submitEmail` and
      `resendOtp`, add captcha state (`none` | `v3-pending` | `v2-required`),
      auto-execute v3 and retry, on `CAPTCHA_INVALID` set v2-required state
- [x] 2.2 Modify `frontends/web/src/components/auth/forgot-password-modal.tsx`:
      render reCAPTCHA v2 checkbox widget in email screen when captcha state is
      `v2-required`, disable submit until v2 solved
- [x] 2.3 Add `CAPTCHA_REQUIRED` and `CAPTCHA_INVALID` keys to
      `frontends/web/src/messages/en.json` and
      `frontends/web/src/messages/vi.json` error sections ← (verify: web build
      passes, captcha flow works end-to-end with auto-retry on CAPTCHA_REQUIRED
      and v2 fallback on CAPTCHA_INVALID)

## 3. Android — Domain and Data Layer

- [x] 3.1 Update `AuthRepository.forgotPassword` interface to accept nullable
      `captchaToken` parameter
- [x] 3.2 Update `AuthRepositoryImpl.forgotPassword` to set `captchaToken` on
      `UserManagementForgotPasswordRequest` when non-null
- [x] 3.3 Update `RequestPasswordResetUseCase.execute` to accept nullable
      `captchaToken` and pass to repository ← (verify: domain/data layer
      compiles, existing callers still work with null token)

## 4. Android — ViewModel Captcha Logic

- [x] 4.1 Add `play-services-safetynet` dependency to
      `frontends/android-app/app/build.gradle.kts`
- [x] 4.2 Add reCAPTCHA site key to `strings.xml` (or BuildConfig) with empty
      default
- [x] 4.3 Modify `ForgotPasswordViewModel`: detect `CAPTCHA_REQUIRED` from
      `ApiFailException.getCode()`, post captcha execution event
      (SingleLiveEvent or similar), handle token callback, retry with token,
      handle failure ← (verify: Android assembleDebug passes, ViewModel
      correctly detects CAPTCHA_REQUIRED and triggers captcha flow)

## 5. Android — Fragment reCAPTCHA Integration

- [x] 5.1 Modify `ForgotPasswordFragment`: observe captcha event from ViewModel,
      call `SafetyNet.getClient(activity).verifyWithRecaptcha(siteKey)`, return
      token to ViewModel on success, handle errors ← (verify: Fragment compiles,
      SafetyNet integration wired correctly)

## 6. Infrastructure — K8s and Docker

- [x] 6.1 Add `recaptcha-secret-key: 'MUST_CHANGE_IN_PRODUCTION'` to
      `services/k8s/overlays/prod/secrets/user-management-secrets.yaml`
- [x] 6.2 Add `recaptcha-secret-key: 'MUST_CHANGE_IN_PRODUCTION'` to
      `services/k8s/overlays/staging/secrets/user-management-secrets.yaml`
- [x] 6.3 Add `RECAPTCHA_ENABLED`, `RECAPTCHA_SITE_KEY`, `RECAPTCHA_SECRET_KEY`
      env vars to `services/k8s/base/services/user-management.yaml` deployment
      container spec
- [x] 6.4 Add `RECAPTCHA_ENABLED`, `RECAPTCHA_SITE_KEY`, `RECAPTCHA_SECRET_KEY`
      to `services/docker/.env.example` with comments ← (verify: K8s manifests
      are valid YAML, env vars correctly reference secrets)
