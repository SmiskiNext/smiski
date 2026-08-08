## Context

The `meet` backend already enforces meeting-lifecycle rules in the domain
aggregate (`Meeting.java`) and application services, and the Forge Custom UI
mirrors those rules in a pure policy function (`domain/meetingPolicy.ts`) plus
the surfaces that consume it. Today the rules are split unevenly:

- Invitee add/remove is gated to `SCHEDULED` only, in both the backend
  application services and the frontend `MeetingInviteeManager`.
- Meeting info update (`title`/`description`/`issueLink`) is allowed on
  `SCHEDULED`/`RUNNING` but rejected on `COMPLETED`/`CANCELED`; `zoneId`/
  `timeRange` are `SCHEDULED`-only.
- Settings replacement is already correctly gated to `SCHEDULED`/`RUNNING`.
- The project page has both an `EDIT` action (unified modal) and a separate
  `SETTINGS` action; the issue panel exposes the full action set including
  edit/cancel/start/end/settings.
- The edit modal has no control for the linked Jira issue.

This change is API-first: the `meet` service emits its own `openapi.yaml` from a
`@SpringBootTest`; no request/response shape changes here (only status-gate
behavior), so the regenerated spec should differ only where status rules are
documented. Per `api-convention`, endpoints keep the `/api/{version}` prefix and
existing RESTful/`:action` shapes — no new endpoints are introduced. No
`db-schema` change: no columns, tables, or migrations are added.

## Goals / Non-Goals

**Goals:**

- Add/remove invitees on `SCHEDULED` or `RUNNING`; reject
  `COMPLETED`/`CANCELED`.
- Update meeting info (`title`, `description`, `issueLink`) in every status;
  keep `zoneId`/`timeRange` editable only on `SCHEDULED`.
- Keep settings replacement on `SCHEDULED`/`RUNNING` (no behavior change).
- Fold settings + invitees into the project-page `Edit meeting` modal for
  `SCHEDULED`/`RUNNING`; remove the standalone project-page `SETTINGS` action.
- Add an issue-link picker to the edit modal.
- Narrow the issue panel to create, list, view detail/history, and join.
- Keep backend and frontend rules identical so the UI never offers an action the
  backend rejects.

**Non-Goals:**

- Editing an existing invitee's identity (`accountId`/`email`/`displayName`).
  Invitees stay add/remove only.
- Changing the in-room `Settings` button in `MeetingRoom` (unchanged).
- Any new endpoint, request/response schema field, manifest scope, or egress
  change.
- Reworking the create (instant/schedule) flows.

## Decisions

### D1: Widen invitee status gate to `SCHEDULED` or `RUNNING`

Change the two invitee application services from `status != SCHEDULED` → reject
to "reject only when `status == COMPLETED || status == CANCELED`". Rationale:
matches the user's requirement and mirrors the existing settings gate exactly,
so all mid-lifecycle mutations share one rule. The frontend
`MeetingInviteeManager` gate (`status !== 'SCHEDULED'`) changes to allow
`SCHEDULED`/`RUNNING`.

Alternative considered: keep the gate in the domain aggregate instead of the
application service. Rejected — the current check lives in the application
service for invitees (the aggregate does not own invitee entities), so we keep
the check where it already is to minimize surface area.

### D2: Allow info update in every status; keep time fields `SCHEDULED`-only

In `Meeting.updateInfo`, remove the `COMPLETED || CANCELED` early rejection so
`title`/`description`/`issueLink` changes are always accepted, while keeping the
existing `scheduledFieldsChanged && status != SCHEDULED` guard so `zoneId`/
`timeRange` remain locked once the meeting leaves `SCHEDULED`. The
`MeetingInfoUpdatedEvent` continues to publish on effective change.

Trade-off: editing a `COMPLETED`/`CANCELED` meeting's title still bumps
`calendarSequence` and emits an info-updated event, which can trigger a calendar
update email to invitees. This is accepted per the user's explicit choice to
allow info edits in all statuses. Documented as a risk below.

Alternative considered: restrict "all statuses" to only `title`/`description`
and forbid `issueLink` changes post-`SCHEDULED`. Rejected — the user explicitly
asked for issue link to always be updatable.

### D3: Issue-link editing reuses the existing Jira issue source

The edit modal gains an issue picker backed by the existing
`useProjectIssues`/`getProjectIssues` (Jira `requestJira` search). No new API.
`buildUpdateMeetingPayload` currently derives `issueLink` from `input.detail`;
it changes to accept the chosen issue (`issueId`, `issueKey`, `projectKey`) so a
new selection is sent. When the host does not change the issue, the current link
is preserved.

### D4: Project-page unified edit modal, no separate `SETTINGS` action

`getAvailableMeetingActions` returns `EDIT` for the host in every status and no
longer returns a separate `SETTINGS` action for the project page. The
`EditMeetingModal` renders sections conditionally by status:

- Info (title/description/issue): always shown and editable.
- Time/zone: shown but disabled when `status != SCHEDULED`.
- Settings + invitees: shown for `SCHEDULED`/`RUNNING`; hidden for
  `COMPLETED`/`CANCELED`.

`MeetingActionMenu`/`MeetingListTable` keep JOIN/START as the primary buttons.
The in-room settings button is unaffected because it does not use this policy.

Alternative considered: keep `SETTINGS` as a distinct action and add a parallel
running-meeting edit modal. Rejected — duplicates UI and diverges from the "one
place to edit" goal.

### D5: Issue panel action set narrowed

The issue panel stops handling `EDIT`, `CANCEL`, `START`, `END`, and `SETTINGS`.
It keeps create (instant/schedule buttons), list, `VIEW_DETAIL`/`VIEW_HISTORY`,
and `JOIN` (when `RUNNING`). Because the shared `MeetingActionMenu` derives
actions from the policy, the issue panel passes `hiddenActions` (or filters) to
suppress the management actions on that surface, and the now-unused issue-panel
edit/settings modal hooks and their handler branches are removed.

Rationale: the policy stays a single source of truth for what the backend
allows; the issue panel simply presents a subset. This avoids forking the policy
per surface.

## Risks / Trade-offs

- Editing info on `COMPLETED`/`CANCELED` meetings emits an info-updated event
  and may send calendar-update emails to invitees → Accepted per user decision;
  documented so reviewers and ops expect the email. Mitigation: no code change
  suppresses it now; can be revisited if emails prove noisy.
- Widening the invitee gate to `RUNNING` means invitees can be added
  mid-meeting; RSVP/notification side effects fire as usual → Mitigation: reuse
  the existing created/deleted events unchanged; no new notification path.
- Frontend/backend drift if only one side ships → Mitigation: this change ships
  both together and the policy tests assert the exact action sets per status.
- Removing issue-panel actions could surprise users who relied on editing there
  → Mitigation: project page remains the full management surface; issue panel
  keeps view/join.

## Migration Plan

1. Backend: update `Meeting.updateInfo` and the two invitee services; update
   domain/application tests; regenerate `services/meet/openapi.yaml`.
2. Frontend: update `meetingPolicy.ts` + tests, `EditMeetingModal`, `Dashboard`,
   `MeetingListTable`, `IssueMeetingsPanel`, and `buildUpdateMeetingPayload`;
   remove unused issue-panel edit/settings wiring.
3. Verify: `./services/gradlew -p services/meet test`; app `pnpm build`,
   `pnpm test`, `pnpm lint`.

Rollback: revert the change set; no data migration, so rollback is code-only.

## Open Questions

None — scope decisions were resolved with the user (invitees on RUNNING, info in
all statuses, add issue-link picker, issue panel keeps view/join, settings
folded into the edit modal on project page).
