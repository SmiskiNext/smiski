# Why

The web workspace currently exposes a static profile screen with hardcoded
account information and a non-functional sign-out action, which prevents
authenticated users from managing their own account lifecycle in the browser.
Adding a complete account settings flow now aligns the web experience with the
already-implemented backend APIs and closes a major gap in user self-service.

## What Changes

- Add a web account settings capability that loads the authenticated user
  profile from the backend and displays real account data.
- Add profile editing for full name, username, and avatar with frontend
  validation aligned to backend constraints.
- Add logout behavior that calls the existing logout endpoint, clears the
  authenticated web session, and redirects users to the login experience.
- Add a delete account flow with explicit destructive confirmation, API
  integration, error handling, and post-deletion sign-out behavior.
- Add localized web copy for account settings screens, forms, validation,
  destructive actions, loading states, and error states.
- Document avatar upload dependency and define fallback behavior if a dedicated
  avatar upload endpoint is unavailable during implementation.

## Capabilities

### New Capabilities

- `web-user-account-settings`: Web account settings experience covering profile
  display, profile editing, avatar handling, logout, and account deletion flows
  for authenticated users.

### Modified Capabilities

- None.

## Impact

- Affected frontend areas: workspace profile screen, workspace shell
  navigation/actions, account settings components, form validation, auth session
  handling, and localized message bundles in `frontends/web`.
- Backend APIs used: `GET /api/v1/me`, `PUT /api/v1/me`, `DELETE /api/v1/me`,
  and `POST /api/v1/auth/logout`.
- Dependency: availability and confirmed contract of an avatar upload API
  endpoint. If no dedicated upload endpoint exists, implementation must either
  defer persistent file upload or use the profile `avatarUrl` field only with a
  documented limitation.
- Related systems: generated SDK usage, bearer-token cookie auth flow, error
  handling via `ApiError` and `ApiFailError`, and responsive shadcn/ui-based
  workspace screens.
