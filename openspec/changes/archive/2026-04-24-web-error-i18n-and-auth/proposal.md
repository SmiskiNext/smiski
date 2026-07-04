# Why

The web frontend has a complete login UI shell but no working authentication
logic, and its API error handling infrastructure exists in code but is never
wired up. Users cannot sign in, and any API errors would be swallowed silently.
Both gaps must be closed before the web app becomes usable.

## What Changes

- Add all error code translations to `en.json` and `vi.json`, mirroring the 13
  top-level codes and 5 violation codes from the Android
  `AndroidErrorTranslator` implementation.
- Implement a static `WebErrorTranslator` that maps JSend error codes to i18n
  message keys and resolves them from loaded message files — no React hook
  required, compatible with the middleware layer.
- Wire `configureApiClient()` at app startup (Next.js root layout or provider)
  using the `WebErrorTranslator`.
- Implement email/password login in `AuthScreen` by calling the generated SDK
  `login()` function, storing `accessToken` and `refreshToken` in cookies.
- Implement Google Sign-In in `AuthScreen` using Firebase Auth
  `signInWithPopup`, obtaining a Firebase ID token, then calling the generated
  SDK `googleLogin()` function.
- Map `ApiFailError` violations to inline field errors on the login form;
  surface `ApiError` and network errors as a top-level form banner.
- Add Next.js `middleware.ts` cookie check that redirects unauthenticated
  requests for `/workspace/*` to `/login`.
- Add `firebase` and `@hey-api/client-fetch` as runtime dependencies.

## Capabilities

### New Capabilities

- `web-error-i18n`: Translated error message keys for all API error codes
  (top-level and violation) in English and Vietnamese, plus a static
  `WebErrorTranslator` that resolves them outside the React component tree.
- `web-auth-login`: Email/password and Google Sign-In flows on the web login
  screen, cookie-based token storage, and Next.js middleware route protection.

### Modified Capabilities

- None

## Impact

- **Files modified**: `frontends/web/src/messages/en.json`, `vi.json`,
  `frontends/web/src/lib/api/client.ts`,
  `frontends/web/src/components/auth-screen.tsx`, `frontends/web/middleware.ts`,
  `frontends/web/src/app/[locale]/layout.tsx`.
- **New files**: `frontends/web/src/lib/api/error-translator.ts`,
  `frontends/web/src/lib/firebase.ts`, `frontends/web/src/lib/auth/cookies.ts`.
- **Dependencies added**: `firebase`, `@hey-api/client-fetch`.
- **Environment variables**: `NEXT_PUBLIC_FIREBASE_*` config keys required for
  Firebase initialization.
- **No backend changes required** — all auth endpoints already exist in the
  OpenAPI spec.
