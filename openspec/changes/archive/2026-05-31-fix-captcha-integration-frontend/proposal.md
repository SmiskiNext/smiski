## Why

The backend forgot-password flow returns `CAPTCHA_REQUIRED` after 2 requests
within 1 hour (per the OWASP 2025 hardening from the redesign-forgot-password
spec). Neither the web nor Android frontend handles this error code — they
display a raw error message and the user is permanently stuck with no way to
proceed. This blocks legitimate users who resend the OTP code even once.

## What Changes

- **Web**: Integrate Google reCAPTCHA v3 (invisible) into the forgot-password
  modal. On `CAPTCHA_REQUIRED`, auto-execute v3 and retry the request. On
  `CAPTCHA_INVALID` (score too low), fall back to a visible reCAPTCHA v2
  checkbox for manual verification.
- **Android**: Same integration using Google Play Services SafetyNet/reCAPTCHA
  API. Detect `CAPTCHA_REQUIRED` in ViewModel, execute invisible challenge,
  retry. Fall back to v2 widget on `CAPTCHA_INVALID`.
- **Infrastructure**: Add `RECAPTCHA_ENABLED`, `RECAPTCHA_SITE_KEY`,
  `RECAPTCHA_SECRET_KEY` environment variables to K8s secrets/deployments and
  Docker dev `.env.example` so production can be enabled without code changes.
- **i18n**: Add `CAPTCHA_REQUIRED` and `CAPTCHA_INVALID` error message
  translations for both platforms.

## Capabilities

### New Capabilities

- `web-recaptcha-forgot-password`: reCAPTCHA v3/v2 integration in the web
  forgot-password modal — auto-retry on CAPTCHA_REQUIRED, v2 fallback on
  CAPTCHA_INVALID.
- `android-recaptcha-forgot-password`: reCAPTCHA v3/v2 integration in the
  Android forgot-password flow — auto-retry on CAPTCHA_REQUIRED, v2 fallback on
  CAPTCHA_INVALID.
- `infra-recaptcha-env`: K8s and Docker environment variable provisioning for
  reCAPTCHA configuration.

### Modified Capabilities

(none — backend behavior is unchanged, only frontend clients are updated)

## Impact

- **Web** (`frontends/web`): New hook (`use-recaptcha.ts`), modified
  `use-forgot-password.ts` and `forgot-password-modal.tsx`, updated i18n
  messages, new env var in `.env.local.example`.
- **Android** (`frontends/android-app`): Modified `AuthRepository`,
  `RequestPasswordResetUseCase`, `ForgotPasswordViewModel`,
  `ForgotPasswordFragment`; new Gradle dependency for reCAPTCHA.
- **K8s** (`services/k8s`): Updated secrets and deployment manifests for
  `user-management`.
- **Docker** (`services/docker`): Updated `.env.example`.
- **External dependency**: Google reCAPTCHA API (script load on web, Play
  Services on Android).
