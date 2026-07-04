# Context

The web app has a functioning `MeetingDetailDialog` with scheduling and settings
sections but no invite management. The backend invite REST API is fully
implemented and its OpenAPI contract is already merged into
`openapi/unified-openapi.yaml`. The web SDK generator (`@hey-ai/openapi-ts`) has
not been re-run since the invite endpoints were added, so
`src/generated/sdk.gen.ts` is missing those functions. The guest join page at
`/{locale}/join/{code}` handles path-based codes but ignores the `?token=` query
parameter that an invite link would carry.

Android parity: the Android implementation uses a chip-based multi-email input
for creation and a status-badged list for editing, with `preApproved` driving
wait-room bypass. The web implementation follows the same business rules with
web-specific UI patterns (shadcn Dialog section, hooks, Tailwind).

## Goals / Non-Goals

**Goals:**

- Expose invite CRUD actions (list, add, resend, revoke) in
  `MeetingDetailDialog` for authenticated hosts of SCHEDULED meetings
- Validate invite tokens on the guest join page and auto-fill or auto-submit the
  join form accordingly
- Regenerate the web SDK so generated types and client functions are current
- Achieve full i18n coverage (English + Vietnamese) for all new copy

**Non-Goals:**

- No backend changes; all API contracts are already implemented and stable
- No multi-step invite creation wizard (single-email input only, consistent with
  the existing web form patterns)
- No real-time push updates to the invitee list (poll-on-open is sufficient for
  this phase)
- No invite analytics or bulk export

## Decisions

### SDK regeneration as a prerequisite step

Re-running `pnpm --dir frontends/web run generate:sdk` before writing any
feature code ensures all TypeScript types and client functions (`getInvitees`,
`addInvitee`, `resendInvite`, `revokeInvite`, `validateToken`) exist at
compilation time. Doing this first prevents type errors and keeps the
implementation straightforward.

Alternative considered: hand-crafting typed wrappers around `fetch`. Rejected
because the generated SDK is already the established pattern, and out-of-band
types diverge from the spec over time.

### Single hook (`useInviteManagement`) owns all invite state

All invite CRUD state (list, per-action loading, per-action error) lives in one
custom hook. The component (`InviteManagementSection`) is purely presentational.

Alternative considered: co-locating fetch calls inside the component. Rejected
for testability and consistency with existing hooks (`useJoinMeeting`,
`useMeetingSettings`).

### Per-action loading and error state instead of a single shared state

Each action (add, resend, revoke) tracks its own `isLoading` and `error`. This
enables granular button-level feedback (disabling only the clicked row's
buttons) without blocking the whole section.

Alternative considered: a single `actionState` discriminated union. Rejected as
overly complex for three simple mutations.

### Token validation inside `useJoinMeeting`

Invite-token validation logic is added to the existing `useJoinMeeting` hook
rather than a separate hook, because it participates in the same join-flow state
machine (resolving meeting code, deciding pre-approval, submitting or entering
waiting room).

Alternative considered: a standalone `useInviteToken` hook called from the page.
Rejected because it duplicates join-flow state and creates two sources of truth
for the meeting code field.

### Auto-submit when `preApproved: true`

When token validation returns `preApproved: true`, the hook auto-submits the
join request after a successful code resolution and password check (none
required for pre-approved tokens if the host has not set a password). This
mirrors Android behavior and removes one manual tap for invited users.

### `inviteToken` passed as prop to `JoinMeetingContainer`

The guest join page (`src/app/[locale]/join/[code]/page.tsx`) extracts the
`token` search param and passes it as an optional `inviteToken` prop. This keeps
the page component thin and the join container self-contained.

### Add-invitee cap: 10

Capped at 10 invitees to match Android. The Add button and email input are
hidden once the list reaches 10 entries.

## Risks / Trade-offs

- SDK regeneration may produce minor type drift if the OpenAPI spec has been
  partially hand-edited. Mitigation: run `pnpm run openapi:unified` first to
  ensure the merged spec is current before regenerating.
- Token auto-submit could create a race if the user is simultaneously editing
  the meeting code field. Mitigation: disable manual input while token
  validation is in progress and clear the token state if the user manually
  changes the code.
- `preApproved` bypass skips the waiting room but the host can still have a
  password requirement. The flow must still pass the password gate if
  `requirePassword` is true, regardless of `preApproved`. Mitigation: handled in
  `useJoinMeeting` — the pre-approval flag only skips the join-request waiting
  state, not the password gate.
