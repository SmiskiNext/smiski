# Implementation Tasks

## 1. Backend — invitee status gate (`services/meet`)

- [x] 1.1 In `AddMeetingInviteesApplicationService.java`, change the status gate
      from `status != SCHEDULED` to reject only when
      `status == COMPLETED ||     status == CANCELED` (allow `SCHEDULED` and
      `RUNNING`).
- [x] 1.2 In `RemoveMeetingInviteesApplicationService.java`, apply the same
      status gate change.
- [x] 1.3 Update application-service tests to cover: `SCHEDULED` accepts,
      `RUNNING` accepts, `COMPLETED` rejects, `CANCELED` rejects — for both add
      and remove. ← (verify: add/remove invitees on RUNNING succeed and publish
      the created/deleted event; COMPLETED/CANCELED rejected with invalid-status
      and no event, matching add-meeting-invitees and remove-meeting-invitees
      specs)

## 2. Backend — meeting info update status gate (`services/meet`)

- [x] 2.1 In `Meeting.java` `updateInfo`, remove the
      `status == COMPLETED ||     status == CANCELED` early rejection so
      `title`/`description`/`issueLink` changes are accepted in every status.
- [x] 2.2 Confirm the existing `scheduledFieldsChanged && status != SCHEDULED`
      guard remains so `zoneId`/`timeRange` changes are rejected
      off-`SCHEDULED`.
- [x] 2.3 Update `Meeting` domain tests and `UpdateMeetingApplicationService`
      tests: info-only update succeeds on `COMPLETED`/`CANCELED`; time/zone
      change on non-`SCHEDULED` is rejected with no event. ← (verify: matches
      the modified update-meeting Status-aware mutable fields scenarios,
      including that scheduled-field changes still reject off-SCHEDULED)

## 3. Backend — regenerate API spec

- [x] 3.1 Run `./services/gradlew -p services/meet generateOpenApiDocsFromTests`
      and commit the regenerated `services/meet/openapi.yaml` if it changes.
- [x] 3.2 Run `./services/gradlew -p services/meet test` and ensure the suite
      passes. ← (verify: full meet unit + application test suite green)

## 4. Frontend — action policy (`app/static/smiski-ui/src/domain`)

- [x] 4.1 In `meetingPolicy.ts`, expose `EDIT` to the host for `SCHEDULED`,
      `RUNNING`, `COMPLETED`, and `CANCELED`, and remove the separate `SETTINGS`
      action from the returned actions.
- [x] 4.2 Keep `START`/`CANCEL` for `SCHEDULED` and `JOIN`/`END` for `RUNNING`
      host-only; keep view actions for `COMPLETED`/`CANCELED`.
- [x] 4.3 Update `meetingPolicy.test.ts` expected action arrays for every status
      and for host vs non-host. ← (verify: matches the ui-backend-interaction
      meeting-action-policy scenarios — EDIT present across statuses, no
      SETTINGS action, non-host sees no management actions)

## 5. Frontend — project-page edit modal (`features/project-page/dashboard`)

- [x] 5.1 In `EditMeetingModal.tsx`, add a project issue picker backed by
      `useProjectIssues`, seeded from the meeting's current issue link.
- [x] 5.2 Disable the start date/time and time-zone controls when
      `meeting.status !== 'SCHEDULED'`.
- [x] 5.3 Hide the settings and invitee sections when the meeting status is
      `COMPLETED` or `CANCELED`; show them for `SCHEDULED`/`RUNNING`.
- [x] 5.4 In the submit handler, include the chosen issue link in the info
      update, skip the time fields when disabled, and skip settings/invitee
      operations when their sections are hidden; keep the "only changed
      operations" diff behavior.
- [x] 5.5 In `Dashboard.tsx`, remove the `SETTINGS` action branch and the
      standalone `MeetingSettingsModal` wiring driven by `settingsMeetingId`
      (keep `EditMeetingModal`). ← (verify: matches ui-backend-interaction
      status-aware edit surface + editable issue link scenarios; save sends only
      changed operations)

## 6. Frontend — issue panel narrowing (`features/issue-panel`)

- [x] 6.1 In `IssueMeetingsPanel.tsx`, restrict the surface to create, list,
      `VIEW_DETAIL`/`VIEW_HISTORY`, and `JOIN`; pass `hiddenActions` (or filter)
      so `EDIT`/`CANCEL`/`START`/`END`/`SETTINGS` are not offered, and remove
      their handler branches.
- [x] 6.2 Remove the now-unused issue-panel edit/settings modal usage
      (`useIssuePanelScheduleModal` edit mode, `useIssuePanelSettingsModal`) and
      any imports left dangling.
- [x] 6.3 Delete or stop exporting the issue-panel hooks/components that are no
      longer referenced after 6.2 (verify no other importers first). ← (verify:
      matches the issue-panel-limited scenarios — running shows join+view only,
      scheduled shows view only, create actions preserved; no dead exports
      remain)

## 7. Frontend — update payload (`api/meetings.ts`)

- [x] 7.1 Change `buildUpdateMeetingPayload` (and `UpdateMeetingInput`) so the
      issue link comes from the caller-selected issue rather than always from
      `input.detail`, defaulting to the current link when unchanged.
- [x] 7.2 Update `meetings.update.test.ts` to cover sending a changed issue link
      and preserving the current link when unchanged. ← (verify: matches the
      editable-issue-link scenarios in ui-backend-interaction)

## 8. Verification

- [x] 8.1 Run app checks from `app/`: `pnpm build`, `pnpm test`, `pnpm lint`.
- [x] 8.2 Run backend checks: `./services/gradlew -p services/meet test`.
- [x] 8.3 Confirm no remaining reference to a removed `SETTINGS` project-page
      action or removed issue-panel edit/settings wiring. ← (verify: whole
      change builds and lints clean on both surfaces; backend + frontend status
      rules agree)
