# Tasks

## 1. Dependencies and Environment Setup

- [x] 1.1 Add `firebase` and `@hey-api/client-fetch` to
      `frontends/web/package.json` and install
- [x] 1.2 Add `NEXT_PUBLIC_API_BASE_URL`, `NEXT_PUBLIC_FIREBASE_API_KEY`,
      `NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN`, `NEXT_PUBLIC_FIREBASE_PROJECT_ID`, and
      `NEXT_PUBLIC_FIREBASE_APP_ID` to `.env.local.example` and document
      required values
- [x] 1.3 Re-run `openapi-ts` code generation to confirm generated SDK types
      include `login()` and `googleLogin()` ← (verify: generated types exist in
      `src/client/` or equivalent output, functions are typed with correct
      request/response shapes)

## 2. i18n Error Message Keys

- [x] 2.1 Add all 13 top-level error code keys, 5 violation keys, 4 general
      error keys, and 6 client-side validation keys under the `errors` namespace
      in `src/messages/en.json` with English strings mirroring Android's
      `strings.xml`
- [x] 2.2 Add the same key set with Vietnamese translations in
      `src/messages/vi.json` mirroring Android's `values-vi/strings.xml`
- [x] 2.3 Verify TypeScript types for message keys (next-intl generates types
      from the message files) compile without errors ← (verify: all error keys
      present in both locale files, TypeScript type-check passes, no key missing
      relative to the Android CODE_MAP)

## 3. WebErrorTranslator

- [x] 3.1 Create `src/lib/api/error-translator.ts` — import `en.json` and
      `vi.json` statically, read the `NEXT_LOCALE` cookie to determine active
      locale (default `en`), implement the `ErrorTranslator` type
      `(code: string, defaultMessage: string) => string` with a key lookup under
      `errors[code]`
- [x] 3.2 Handle the fallback case: if the key is not found in the messages map,
      return `defaultMessage` unchanged
- [x] 3.3 Export the `webErrorTranslator` function as the default export ←
      (verify: calling `webErrorTranslator("INVALID_CREDENTIALS", "fallback")`
      returns a non-empty English string; calling with an unknown code returns
      the fallback string; no React hook is used)

## 4. API Client Wiring

- [x] 4.1 Update `src/lib/api/client.ts` to ensure `configureApiClient` sets up
      the hey-api fetch client with `baseUrl` and `translator`
- [x] 4.2 Call
      `configureApiClient(process.env.NEXT_PUBLIC_API_BASE_URL!, webErrorTranslator)`
      in `src/app/[locale]/layout.tsx` (or a dedicated provider component) so it
      executes once at startup ← (verify: a real API call from the browser goes
      to the correct base URL; errors from the API surface localised messages)

## 5. Firebase Initialisation

- [x] 5.1 Create `src/lib/firebase.ts` — initialise Firebase app using
      `getApps()` guard, export `auth` (Firebase Auth instance) and
      `googleProvider` (GoogleAuthProvider instance)
- [x] 5.2 Add a startup assertion that throws a descriptive error if any
      required `NEXT_PUBLIC_FIREBASE_*` variable is undefined ← (verify:
      importing the module twice does not create a second Firebase app; missing
      env var causes early failure with a clear message)

## 6. Cookie Utilities

- [x] 6.1 Create `src/lib/auth/cookies.ts` — implement
      `setAuthCookies(accessToken: string, refreshToken: string): void` that
      writes `access_token` and `refresh_token` cookies with `SameSite=Lax` and
      `Secure` conditional on `NODE_ENV === 'production'`
- [x] 6.2 Implement `clearAuthCookies(): void` that removes both cookies (for
      future logout use)
- [x] 6.3 Implement `getAccessToken(): string | undefined` that reads the
      `access_token` cookie value ← (verify: in dev `NODE_ENV=development`,
      cookies are set without Secure flag; in production build, Secure flag is
      present; both cookies are written with correct names)

## 7. Email and Password Login

- [x] 7.1 Wire the login form submit handler in `src/components/auth-screen.tsx`
      to call the generated SDK `login({body: {email, password}})`
- [x] 7.2 On success, call `setAuthCookies(accessToken, refreshToken)` then
      `router.push('/workspace')`
- [x] 7.3 Map `ApiFailError` violations: iterate `error.violations`, match each
      `field` to the corresponding form field, set the translated violation
      `message` as the field error using the form library's error API
- [x] 7.4 For `ApiError` and network errors (`catch` block), display the error
      message as a form-level banner (e.g., a `<p role="alert">` above the
      submit button)
- [x] 7.5 Set the submit button to disabled while the request is in flight and
      restore it on completion ← (verify: valid credentials → cookies set +
      redirect to /workspace; invalid credentials → `INVALID_CREDENTIALS`
      message shown inline; 500 response → banner shown; button is disabled
      during loading)

## 8. Google Sign-In

- [x] 8.1 Wire the "Sign in with Google" button in `auth-screen.tsx` to call
      `signInWithPopup(auth, googleProvider)` from `src/lib/firebase.ts`
- [x] 8.2 On Firebase success, call `user.getIdToken()` then call the generated
      SDK `googleLogin({body: {idToken}})`
- [x] 8.3 On backend success, call `setAuthCookies` and navigate to `/workspace`
- [x] 8.4 Handle popup-closed / popup-blocked errors from Firebase silently (no
      banner shown)
- [x] 8.5 Handle `INVALID_FIREBASE_TOKEN` and `FIREBASE_AUTH_ERROR` from the
      backend by displaying the translated message as a form-level banner
- [x] 8.6 Handle any other `signInWithPopup` error by displaying the translated
      `error_google_signin_failed` message ← (verify: successful Google sign-in
      → cookies set + redirect; popup dismissed → no error shown; backend token
      rejection → banner with translated message)

## 9. Route Protection Middleware

- [x] 9.1 Update `frontends/web/middleware.ts` to add a route protection check:
      if the request pathname (locale-stripped) matches `/workspace/*` and
      `access_token` cookie is absent, redirect to `/[locale]/login`
- [x] 9.2 Ensure the existing `next-intl` locale routing middleware is composed
      with the auth check (auth check runs first, then locale routing for
      non-protected routes)
- [x] 9.3 Confirm that `/login`, `/register`, and other public routes are not
      affected by the auth check ← (verify: navigating to `/workspace/meetings`
      without a cookie redirects to `/login`; navigating with cookie proceeds
      normally; `/login` is accessible without a cookie)
