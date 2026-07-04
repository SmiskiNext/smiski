# Context

The web frontend already has the reusable `MeetingSettingsForm`, generated SDK
clients, and meeting-room handoff flow needed to support settings management,
but the current implementation stops at meeting creation. Hosts cannot reopen
settings for an existing meeting, the meeting room does not preserve the backend
meeting identifier after navigation, and the workspace home screen still renders
localized mock items instead of real host meetings.

This change spans multiple web surfaces that must remain consistent: instant
meeting creation, join approval handoff, the in-room toolbar, and the workspace
home screen. The backend contract is already fixed to the replacement-style
meeting settings API, so the web implementation must align its request mapping
with the backend enum values and support loading existing settings into the
shared form without exposing any stored password value.

## Goals / Non-Goals

**Goals:**

- Add a reusable dialog-driven web flow for loading, editing, and saving meeting
  settings for an existing meeting.
- Reuse the existing `MeetingSettingsForm` and schema mapping layer so create
  and update flows share the same field semantics and validation behavior.
- Preserve the backend meeting identifier across instant meeting and join flows
  so the meeting room can target the active meeting for settings updates.
- Replace workspace home mock upcoming meetings with real `listHostMeetings`
  data and expose a settings entry point from each item.
- Keep error handling and localization aligned with current web patterns,
  especially the inline API error treatment used by instant meeting creation.

**Non-Goals:**

- Adding client-side host-role gating beyond the existing server-side
  authorization.
- Changing backend meeting settings APIs, validation rules, or response
  payloads.
- Introducing optimistic updates, live refresh, or pagination for the
  home-screen meeting list.
- Expanding meeting-room controls beyond opening the settings dialog.

## Decisions

### Reuse the shared meeting settings form and add bidirectional schema mapping

The update flow will use the existing `MeetingSettingsForm` and extend
`frontends/web/src/lib/schemas/meeting.ts` with reverse mapping from
`MeetingManagementMeetingSettingsResponse` into `MeetingSettingsValues`. This
keeps one source of truth for waiting-room semantics, default field names, and
submit payload shape.

Rationale:

- Prevents drift between create and update screens.
- Lets the existing `mapSettingsToRequest` remain the single request serializer
  after fixing the enum constants.
- Keeps password handling explicit by mapping `requirePassword` to the form
  toggle while leaving the actual password value empty.

Alternatives considered:

- Build a separate edit-only form model: rejected because it would duplicate
  validation and increase the chance of mismatched behavior.
- Patch the backend response directly into form state without a mapper: rejected
  because the response and form shapes differ on waiting-room and password
  semantics.

### Persist the meeting identifier in session-scoped handoff state

The room launch handoff already uses `sessionStorage` for token and room name,
so the change will add a shared `meeting_id` key and write the resolved meeting
identifier during instant meeting success and approved join flows.
`consumeSessionCredentials()` in the meeting room will read this value together
with the existing credentials.

Rationale:

- Matches the current tab-scoped handoff model and avoids introducing new global
  state.
- Works for both instant-meeting redirects and join-approval redirects with
  minimal surface-area changes.
- Keeps the meeting room self-sufficient after navigation.

Alternatives considered:

- Pass the meeting ID in the meeting-room URL: rejected because the current room
  route already relies on session handoff and changing routing would expand
  scope.
- Store the meeting ID in a global client store: rejected because the value only
  needs to survive a single tab navigation and session storage already exists
  for this purpose.

### Use dialog-local fetch and submit states instead of preloading meeting settings globally

`MeetingSettingsDialog` will fetch `getMeeting` data when it opens, initialize
the form from the response mapper, and submit `putMeetingSettings` from within
the dialog. Both meeting room and home screen will only manage whether the
dialog is open and which meeting ID is active.

Rationale:

- Keeps API lifecycle concerns close to the UI that needs them.
- Avoids loading meeting settings for every listed meeting on the home screen.
- Ensures each open reflects the latest server state instead of stale cached
  defaults.

Alternatives considered:

- Preload full settings for all host meetings on the home screen: rejected
  because the list view only needs summary data and preloading would add
  unnecessary network work.
- Centralize settings state in a parent provider: rejected because the feature
  currently has only two entry points and a dialog-local model is simpler.

### Replace localized mock meeting cards with summary list data and explicit UI states

The workspace home screen will call `listHostMeetings` on mount and render real
meeting summary items, including title, start time, status, and a settings
action that opens the shared dialog for that meeting. The screen will present
distinct loading, empty, and retryable error states using localized copy.

Rationale:

- Removes dependence on fake translation-backed content.
- Aligns the host landing screen with backend-managed meeting data.
- Keeps settings management accessible from scheduled meetings without routing
  to a separate details page.

Alternatives considered:

- Leave the mock list and add only a settings entry elsewhere: rejected because
  the request explicitly requires real API data and host meeting management from
  the home screen.
- Redirect from the home screen into a dedicated settings page: rejected because
  the requested UX is dialog-based and should reuse the same component as the
  meeting room.

## Risks / Trade-offs

- [Dialog opens before data loads, creating a confusing blank state] → Show
  explicit loading UI inside the dialog until `getMeeting` completes and only
  render the form with mapped values once ready.
- [Session storage can become stale if users open multiple meetings in one tab]
  → Continue using the existing tab-scoped handoff pattern and overwrite the
  stored meeting ID whenever a new room handoff occurs.
- [Home screen list summaries may not contain every field needed for editing] →
  Use `listHostMeetings` only for list rendering and fetch full settings lazily
  with `getMeeting` when the dialog opens.
- [Password semantics can be misinterpreted during edit] → Preserve backend
  behavior by using `requirePassword` only to toggle whether a password is
  required and keep the password input empty unless the host enters a
  replacement value.

## Migration Plan

No backend or data migration is required. Deploy the frontend changes together
so the corrected admission-policy mapping, session handoff updates, and new
dialog entry points ship as one coherent web feature. Rollback can revert the
frontend bundle to the previous release because the backend API remains backward
compatible for non-updating clients.

## Open Questions

- None. The backend contract, entry points, and host-authorization behavior are
  already defined for this change.
