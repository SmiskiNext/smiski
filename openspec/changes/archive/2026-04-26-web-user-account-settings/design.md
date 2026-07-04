# Context

The web workspace already has authenticated backend support for reading,
updating, deleting, and logging out the current user, but the current profile
screen remains static and does not participate in the real account lifecycle.
The implementation will span the workspace profile screen, reusable account
settings components, session/logout behavior, localized copy, and destructive
confirmation patterns, so a design document is warranted to align UI structure,
API usage, and failure handling before implementation.

The existing frontend conventions shape the solution:

- Generated SDK functions are the source of truth for API calls.
- The web app uses bearer-token auth derived from cookies and centralized API
  error types.
- Existing hooks prefer explicit UI phases and callback actions over implicit
  query libraries.
- Forms use `react-hook-form` with `zod` validation and shadcn/ui primitives.
- The profile screen lives inside the workspace shell and already exposes
  settings-related sections that can be converted from static content to
  functional account controls.

A key constraint is avatar persistence. The product wants local file selection
with preview and upload on save, but the current confirmed web-ready contract
only guarantees `avatarUrl` on `PUT /api/v1/me`. There may be a dedicated upload
endpoint elsewhere in the system, but that dependency is not yet confirmed for
this change.

## Goals / Non-Goals

**Goals:**

- Replace the static web profile screen with a real account settings experience
  backed by `getMe()`, `putMe()`, `deleteMe()`, and `logout()`.
- Present the authenticated user profile with proper loading, success,
  empty-data fallback, and recoverable error states.
- Provide an edit flow for full name, username, and avatar data with frontend
  validation matching backend constraints.
- Support logout and account deletion as explicit actions that terminate the
  current authenticated session and redirect users to the login screen.
- Localize all new labels, helper text, validation feedback, button text, and
  destructive action messaging in English and Vietnamese.
- Keep the implementation aligned with existing web frontend patterns so future
  workspace settings features can reuse the same structure.

**Non-Goals:**

- Introducing new backend account APIs or changing the payload shape of the
  existing user endpoints.
- Building a full standalone avatar media-management system beyond what is
  required to preview and persist the profile avatar for this screen.
- Editing account preferences, authentication provider settings, password
  changes, notification settings, or meeting history features.
- Modifying native Android account settings behavior except as a reference for
  UX parity.
- Changing workspace-level routing architecture outside of what is needed to
  render and navigate the web account settings surface.

## Decisions

### 1. Keep the feature inside the existing workspace profile route instead of introducing a separate settings route

The current web workspace already exposes a profile screen entry point, so the
implementation will convert that screen from static content into the account
settings experience. This minimizes routing churn, preserves existing navigation
placement, and keeps account management co-located with related profile and
support items already visible to users.

Alternatives considered:

- Create a dedicated `/settings/account` route. Rejected because the existing
  profile screen already occupies the intended navigation slot and the feature
  scope does not require a larger settings IA change.
- Use modal-only editing without improving the screen itself. Rejected because
  the page still needs to load and present real profile data, logout, and delete
  controls.

### 2. Use a feature-specific hook with an explicit phase-based state machine for profile data and mutations

The implementation will follow the established web pattern used by features such
as upcoming meetings: a custom hook will own profile fetch state, mutation
progress, derived UI flags, and action callbacks. The state model should
distinguish at minimum `LOADING`, `SUCCESS`, and `ERROR` for initial page load,
while separate mutation state tracks save, logout, and deletion progress to
avoid blocking unrelated actions.

This keeps screen components declarative, centralizes API error normalization,
and matches existing team conventions better than introducing a new
data-fetching abstraction.

Alternatives considered:

- Use direct API calls in the page component. Rejected because state handling
  for load, retry, save, delete, and logout would become difficult to reason
  about.
- Adopt a new query/mutation library pattern. Rejected because it would be
  inconsistent with the explored codebase patterns and unnecessary for this
  scoped feature.

### 3. Split the page into focused account settings components with clear responsibility boundaries

The screen will be composed from smaller sections:

- `account-profile-summary` for avatar, name, email, provider, and high-level
  account metadata.
- `account-profile-form` for editable fields and avatar picker preview.
- `logout-card` or action section for sign-out.
- `delete-account-dialog` for destructive confirmation and failure handling.
- Optional shared helper components for field-level status, loading
  placeholders, and error state treatment.

This breakdown supports readability, testing, and reuse while keeping dialog and
form logic isolated from the page shell.

Alternatives considered:

- Put all logic in one screen component. Rejected due to poor maintainability
  and harder testability.
- Make every control a separate route. Rejected because it adds unnecessary
  navigation complexity for a single account settings surface.

### 4. Match backend validation exactly in the form schema and preserve server errors for conflict or validation mismatches

The edit form will use `zod` and `react-hook-form` with the same baseline rules
as `PutUserRequest`:

- `fullName`: required, maximum 255 characters.
- `username`: required, 3 to 30 characters, pattern `^[a-zA-Z0-9_-]+$`.
- `avatarUrl`: optional, maximum 2048 characters when persisted as a URL.

Client validation gives immediate feedback, while API failures remain
authoritative for cases such as duplicate usernames or backend-only invariants.
Server-side failures should surface as localized form-level or field-level
messaging without discarding unsaved user input.

Alternatives considered:

- Looser client validation. Rejected because it increases avoidable round trips
  and creates UX mismatch with backend rules.
- Only rely on backend validation. Rejected because it degrades responsiveness
  and accessibility.

### 5. Treat avatar selection as a staged local change and upload or persist only on Save

Selecting an avatar file will update local form state and render a preview via
`URL.createObjectURL()` without immediately calling the backend. This matches
the stated product intent and prevents accidental partial updates when the user
cancels editing.

Implementation behavior depends on API availability:

- If a dedicated avatar upload endpoint is confirmed during implementation, the
  save flow uploads the file first, obtains a persistent URL, and then sends
  `putMe()` with the resolved `avatarUrl` alongside other edited fields.
- If no upload endpoint is available, the form still supports previewing a
  chosen file, but persistent avatar-file upload is out of scope. In that case
  the implementation must either disable file save with clear messaging or limit
  persistence to direct `avatarUrl` editing if the UX includes a URL field. The
  final implementation choice must be documented in code and release notes.

Alternatives considered:

- Immediate upload on file selection. Rejected because it violates the requested
  interaction pattern and complicates cancellation.
- Silent in-memory preview with fake persistence. Rejected because it risks
  misleading users about whether the avatar was actually saved.

### 6. Use distinct mutation flows for save, logout, and delete account

Account deletion and logout have different security and UX expectations from
profile editing, so they will not share a generic mutation state. The design
uses separate flows:

- Save profile: preserve form input, show inline pending state, surface
  recoverable errors, refresh profile data on success.
- Logout: disable repeat submission, call `logout()`, clear client-auth session
  artifacts, and redirect to login even if server-side logout returns a
  recoverable auth/session error that indicates the user is effectively signed
  out.
- Delete account: drive dialog states `IDLE -> CONFIRMING -> DELETING -> ERROR`
  and require the user to type `DELETE` exactly before enabling confirmation.

This avoids coupling destructive actions to the edit form and supports precise
user feedback.

Alternatives considered:

- Single global busy flag for the entire screen. Rejected because it would
  unnecessarily freeze unrelated actions and make error recovery confusing.

### 7. After logout or successful deletion, redirect to the locale-aware login route and invalidate visible account state

Once the session is terminated, the UI must not continue rendering stale
authenticated data. The implementation will clear in-memory state for the
profile screen and navigate to the login experience in the active locale. Any
cookie/session cleanup available to the frontend runtime should be performed in
addition to calling the logout API.

For successful account deletion, the same redirect behavior applies after the
backend confirms deletion.

Alternatives considered:

- Stay on the profile page and wait for middleware to reject the next
  navigation. Rejected because it creates stale authenticated UI and weakens
  user trust.

### 8. Use responsive card-based layout with rounded-xl form controls and mobile-safe destructive dialogs

The account settings page will retain the workspace visual language but adapt
content into stacked cards or sections. On larger screens, profile summary and
edit form can appear in a balanced two-column arrangement if the existing shell
width permits; on smaller screens, all content stacks vertically with full-width
actions. Inputs use `rounded-xl` styling to match the settings context rather
than auth-pill styling.

Dialogs and action buttons must remain reachable and readable on small screens,
including long validation errors and destructive messaging.

Alternatives considered:

- Reuse auth-page form layout directly. Rejected because workspace pages have
  different spacing, navigation, and context.

## Risks / Trade-offs

- [Avatar upload endpoint remains unconfirmed] -> Mitigation: implement the edit
  flow so avatar preview is isolated from persistence; gate final file-upload
  behavior behind the confirmed API contract and document fallback limitations
  in the proposal, spec, and tasks.
- [Profile save can fail after the user changes multiple fields] -> Mitigation:
  preserve unsaved form state, map server errors into actionable feedback, and
  avoid resetting the form until a successful response returns.
- [Session cleanup behavior can differ between server cookies, interceptors, and
  client state] -> Mitigation: use the existing logout endpoint first, clear
  local account state immediately after success, and redirect to the login
  screen so protected routes re-evaluate auth on next load.
- [Delete account is irreversible and high risk] -> Mitigation: require exact
  `DELETE` confirmation text, isolate the flow in a dialog, disable duplicate
  submissions, and keep the dialog open with visible errors on failure.
- [Fetching `/me` may return partial or nullable data such as missing avatar or
  username edge cases] -> Mitigation: define safe display fallbacks for optional
  fields and keep editable required fields validated before submission.
- [Responsive layout may become crowded if all actions are shown equally] ->
  Mitigation: separate primary profile editing from secondary logout and
  destructive actions using distinct cards, spacing, and visual hierarchy.

## Migration Plan

1. Replace the static data bindings in the existing web profile screen with a
   feature hook and live profile state.
2. Introduce account settings components and i18n keys behind the existing
   authenticated profile route.
3. Wire save, logout, and delete actions to the generated SDK and existing
   auth/session utilities.
4. Validate the flow across supported locales and viewport sizes.
5. If the avatar upload endpoint is available, integrate it in the save
   sequence; otherwise ship the documented fallback behavior and track the
   upload integration separately.

Rollback strategy:

- Revert the web account settings components and restore the previous static
  profile rendering if release-blocking issues appear.
- Because the backend APIs already exist and no backend schema changes are
  introduced, rollback is frontend-only.

## Open Questions

- Is there a dedicated backend avatar upload endpoint already available for the
  web app, and if so, what is the generated SDK function and response contract?
- If no upload endpoint exists, should the first release omit file-based avatar
  persistence entirely, or should the UI expose a manual avatar URL input as a
  temporary supported path?
- What is the canonical locale-aware login route for redirect after logout or
  deletion in the current Next.js app?
- Are there existing shared utilities for clearing auth cookies or cached user
  state that the account settings flow should reuse instead of adding new
  session cleanup logic?
