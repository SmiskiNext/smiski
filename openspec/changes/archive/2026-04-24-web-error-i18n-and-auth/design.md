# Context

The web frontend (`frontends/web`) is a Next.js app with TypeScript. It has a
complete login UI shell in `components/auth-screen.tsx` and a JSend API
middleware layer in `src/lib/api/` (jsend-middleware.ts, types.ts, client.ts).
Neither is wired up: `configureApiClient()` is never called, and the login form
submits by navigating directly to `/workspace` without any API interaction.

The app already uses `next-intl` for i18n routing, with `en.json` and `vi.json`
containing only 3 placeholder strings. The Android app has a fully implemented
`AndroidErrorTranslator` with 13 top-level error codes and 5 violation codes
mapped to localised strings — this is the established pattern to replicate on
web.

An OpenAPI spec at `openapi/unified-openapi.yaml` defines auth endpoints and an
`openapi-ts.config.ts` generates a typed SDK via `@hey-api/openapi-ts`. The
runtime client package `@hey-api/client-fetch` is not yet installed.

## Goals / Non-Goals

**Goals:**

- Populate i18n message files with all error code keys mirroring Android's
  translator, in both English and Vietnamese.
- Implement a `WebErrorTranslator` that resolves error codes to translated
  messages without using React hooks, making it usable in the API middleware
  layer.
- Call `configureApiClient()` at app startup with the translator wired in.
- Implement email/password login by calling the generated SDK `login()` function
  and storing tokens in cookies.
- Implement Google Sign-In via Firebase Auth `signInWithPopup`, obtaining a
  Firebase ID token, then calling `googleLogin()`.
- Display `ApiFailError` violations as inline field errors and
  `ApiError`/network errors as form-level banners.
- Protect `/workspace/*` routes with Next.js middleware that checks for the auth
  cookie.

**Non-Goals:**

- Implementing registration, password reset, or logout flows (outside this
  change).
- Server-side session management or JWT validation in middleware (cookie
  presence check only).
- Refresh token rotation logic (deferred to a follow-up change).
- Changes to backend services or the OpenAPI spec.

## Decisions

**1. Static message lookup for `WebErrorTranslator` instead of `useTranslations`
hook**

The JSend middleware (`jsend-middleware.ts`) runs outside the React component
tree, so React hooks are unavailable. Rather than using `useTranslations`, the
`WebErrorTranslator` will import the messages JSON files directly and perform a
key lookup at call time. The active locale is resolved from `next-intl`'s
server-side `getLocale()` or from a module-level variable set during app init.

Considered alternative: pass the hook result down as a prop from a client
component. Rejected because it requires threading the translator through every
component that makes API calls, creating tight coupling.

Chosen alternative: load the messages JSON once at module init and do a
two-level key lookup (`messages[locale][errorCode]`). This is stateless,
testable, and works anywhere in the call stack.

**2. Cookie storage for tokens, not localStorage**

Tokens stored in `httpOnly` cookies would prevent JS access entirely (XSS
protection), but the generated SDK client sends requests from the browser and
needs to read the access token to set the `Authorization` header. Since
`configureApiClient` accepts a translator and the hey-api client handles
headers, the access token must be readable by JS. Decision: store tokens in
non-httpOnly cookies with `SameSite=Lax` and `Secure` conditional on
`NODE_ENV === 'production'`.

Considered: `localStorage`. Rejected because Next.js middleware cannot read
localStorage, making server-side route protection impossible without an extra
API round-trip.

**3. Firebase Auth SDK for Google Sign-In (same flow as Android)**

Android uses Firebase Auth to get a Firebase ID token, which is then sent to the
backend `/api/v1/auth/google-login`. The web implementation mirrors this:
`signInWithPopup(auth, googleProvider)` → `getIdToken()` → call backend.
Firebase config values are injected via `NEXT_PUBLIC_FIREBASE_*` environment
variables.

Considered: Google Identity Services (`google.accounts.id`) directly. Rejected
because it diverges from the established Android pattern and requires a separate
OAuth client setup.

**4. Next.js middleware for route protection — cookie check only**

The middleware reads the access token cookie. If absent, it redirects to
`/login`. No JWT signature validation is performed in middleware (that happens
on the backend for each API request). This keeps middleware fast and avoids
shipping secret keys to the edge.

**5. No client-side auth context/store**

Auth state is derived entirely from the cookie. Protected page components do not
need to check auth; the middleware handles the redirect. This simplifies the
client component tree and avoids hydration mismatches.

## Risks / Trade-offs

- **Stale access token in cookie** — If the token expires, API calls will fail
  with 401. The app will need a refresh token interceptor (deferred). For now,
  401 errors surface as generic API errors. Risk is low for the initial feature
  launch given typical token lifetimes. → Mitigation: document the need for
  refresh token handling as a follow-up task.
- **Cookie readable by JS** — Non-httpOnly cookies are accessible to JavaScript,
  making the access token vulnerable to XSS. → Mitigation: Next.js CSP headers
  and input sanitisation (existing framework defaults) reduce but do not
  eliminate this risk; accepted trade-off given the need for client-side SDK
  auth headers.
- **Firebase config in `NEXT_PUBLIC_*` env vars** — These are exposed in the
  browser bundle. Firebase Web API keys are designed to be public (security
  enforced by Firebase rules), so this is acceptable.
- **Static message JSON import** — If the messages file schema changes (keys
  renamed), the error translator will silently return the default message
  instead of throwing. → Mitigation: TypeScript type the message key union; add
  a test that verifies all `AndroidErrorTranslator` codes have corresponding
  keys in both locale files.
