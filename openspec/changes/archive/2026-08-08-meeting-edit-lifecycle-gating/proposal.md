## Why

Meeting editing rules are inconsistent across the meeting lifecycle and between
the two Forge surfaces. Hosts cannot manage invitees on a running meeting, the
issue panel exposes edit/cancel/end/settings actions that overlap the project
page, and the edit modal cannot change the meeting's linked issue. This change
makes the edit rules explicit and status-aware, and narrows the issue panel to
the operations it should own.

## What Changes

- Allow the host to add/remove invitees when the meeting is `SCHEDULED` **or**
  `RUNNING` (today: `SCHEDULED` only). Invitees remain add/remove only — editing
  an existing invitee's identity is not supported. **BREAKING** backend status
  gate for the two invitee endpoints.
- Allow the host to update meeting information (`title`, `description`,
  `issueLink`) in **any** meeting status, including `COMPLETED` and `CANCELED`
  (today: rejected for `COMPLETED`/`CANCELED`). Time fields (`zoneId`,
  `timeRange`) remain editable only when `SCHEDULED`. **BREAKING** backend and
  domain status gate for `updateInfo`.
- Keep settings replacement gated to `SCHEDULED` or `RUNNING` (already correct;
  no backend change).
- Project page: fold settings and invitees into the single `Edit meeting` modal
  for `SCHEDULED` and `RUNNING`; remove the standalone `SETTINGS` menu action on
  the project page dashboard. Add an issue-link picker to the edit modal.
  Disable time/zone fields when the meeting is not `SCHEDULED`; hide settings
  and invitees sections when the meeting is `COMPLETED`/`CANCELED`.
- Issue panel: restrict to create (instant/schedule), list, view detail/history,
  and join (when `RUNNING`). Remove `EDIT`, `CANCEL`, `START`, `END`, and
  `SETTINGS` from the issue panel action set and drop the now-unused issue-panel
  edit/settings modal wiring.
- Frontend action policy (`getAvailableMeetingActions`) updated so `EDIT` covers
  every status for the host and the project page exposes settings/invitee
  management through `EDIT` rather than a separate `SETTINGS` action.

## Capabilities

### New Capabilities

<!-- None. This change modifies existing capabilities only. -->

### Modified Capabilities

- `add-meeting-invitees`: status gate widens from `SCHEDULED`-only to
  `SCHEDULED` or `RUNNING`; `COMPLETED`/`CANCELED` still rejected.
- `remove-meeting-invitees`: status gate widens from `SCHEDULED`-only to
  `SCHEDULED` or `RUNNING`; `COMPLETED`/`CANCELED` still rejected.
- `update-meeting`: information fields (`title`, `description`, `issueLink`)
  become editable in every status; `zoneId`/`timeRange` stay `SCHEDULED`-only;
  the previous blanket rejection of `COMPLETED`/`CANCELED` is removed.
- `ui-backend-interaction`: adds requirements for the status-aware meeting edit
  surface (unified project-page edit modal with issue-link picker, disabled time
  fields off-`SCHEDULED`, settings/invitees folded in and hidden when
  `COMPLETED`/`CANCELED`), the narrowed issue-panel action set, and the frontend
  meeting-action policy that drives them.

## Impact

- Backend (`services/meet`):
    - `domain/model/Meeting.java` — `updateInfo` status gate.
    - `application/service/AddMeetingInviteesApplicationService.java`,
      `RemoveMeetingInviteesApplicationService.java` — status gate.
    - Corresponding domain/application tests and `openapi.yaml` regeneration.
- Frontend (`app/static/smiski-ui`):
    - `domain/meetingPolicy.ts` (+ `meetingPolicy.test.ts`).
    - `features/project-page/dashboard/EditMeetingModal.tsx`, `Dashboard.tsx`,
      `MeetingListTable.tsx`.
    - `features/issue-panel/IssueMeetingsPanel.tsx` and the removed issue-panel
      edit/settings modal hooks.
    - `api/meetings.ts` (`buildUpdateMeetingPayload` accepts a chosen issue
      link).
- No new capability, no new endpoint, no manifest scope/egress change.
