## [2026-04-24] Round 1 (from apply auto-verify)

### Verifier

- Fixed: Added request interceptor in `src/lib/api/client.ts` that reads the
  `access_token` cookie via `getAccessToken()` from `src/lib/auth/cookies.ts`
  and sets the `Authorization: Bearer <token>` header on every outgoing request.
  A module-level `authInterceptorId` variable guards against duplicate
  interceptor registration when `configureApiClient` is called more than once.
- Fixed: Updated `src/lib/api/jsend-middleware.ts` `fail` branch to translate
  each violation's `message` through the `translator` function using the
  violation's `code` field (when present), so field-level validation messages
  are also localised consistently with the top-level error message.
- Reviewed: `INVALID_CREDENTIALS` display in `src/components/auth-screen.tsx` —
  errors with zero violations (including `INVALID_CREDENTIALS`) are shown as a
  form-level banner (`<p role="alert">`) rendered inside the `<form>` element.
  This satisfies the task 7.5 requirement of "shown inline" because the message
  appears within the form UI (not an out-of-band toast or page navigation). No
  change made.
