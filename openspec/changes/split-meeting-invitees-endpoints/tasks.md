# Tasks

## 1. Domain: errors and cleanup

- [x] 1.1 Add `INVITEE_ALREADY_EXISTS(ErrorCategory.CONFLICT)` to
      `MeetingErrorCode`
- [x] 1.2 Add `MeetingError.InviteeAlreadyExists` record (message args identify
      the offending accountId)
- [x] 1.3 Add the `error.INVITEE_ALREADY_EXISTS.*` keys to the meet i18n message
      bundle(s)
- [x] 1.4 Remove `MeetingInvitee.updateDisplayName`
- [x] 1.5 Remove `Meeting.recordInviteesUpdated` ← (verify: no remaining
      references to updateDisplayName or recordInviteesUpdated compile-wide)

## 2. Domain events and proto

- [x] 2.1 Delete `MeetingInvitationsUpdatedEvent`
- [x] 2.2 Delete `MeetingInvitationsUpdatedEventProtoMapper`
- [x] 2.3 Delete `services/proto/.../meeting_invitations_updated.proto`
- [x] 2.4 Run buf format/generate so proto codegen drops the updated message ←
      (verify: proto module builds; no `MeetingInvitationsUpdated` symbol
      remains; created/deleted proto field numbers unchanged)

## 3. Application: split add use case

- [x] 3.1 Add `AddMeetingInviteesCommand` (meetingId, accountId, tenantId,
      invitees[email, accountId, displayName])
- [x] 3.2 Add `AddMeetingInviteesResult` (affected/created invitee snapshots
      incl. id)
- [x] 3.3 Add `AddMeetingInviteesUseCase` interface
- [x] 3.4 Add `AddMeetingInviteesApplicationService`: load-with-lock, host
      check, SCHEDULED check, in-request duplicate check, reject already-active
      accountId atomically with `InviteeAlreadyExists`, create invitees,
      register + publish `invitations.created` ← (verify:
      403/404/409/invalid-status paths persist nothing and publish no event;
      success publishes exactly one created event)

## 4. Application: split remove use case

- [x] 4.1 Add `RemoveMeetingInviteesCommand` (meetingId, accountId, tenantId,
      inviteeIds[])
- [x] 4.2 Add `RemoveMeetingInviteesResult` (removed invitee snapshots incl. id)
- [x] 4.3 Add `RemoveMeetingInviteesUseCase` interface
- [x] 4.4 Add `RemoveMeetingInviteesApplicationService`: load-with-lock, host
      check, SCHEDULED check, resolve all ids against active invitees, reject
      unknown/already-removed ids atomically with `InviteeNotFound`,
      soft-delete, register + publish `invitations.deleted` ← (verify: any
      unresolved id fails the whole batch with 404 and persists nothing; success
      publishes exactly one deleted event)

## 5. Application: remove old update use case

- [x] 5.1 Delete `UpdateMeetingInviteesUseCase`,
      `UpdateMeetingInviteesApplicationService`, `UpdateMeetingInviteesCommand`,
      `UpdateMeetingInviteesResult`

## 6. Read path: expose invitee id

- [x] 6.1 Add `id` to `InviteeSummary` projection
- [x] 6.2 Update the JPQL projection query in `MeetingInviteeJpaRepository` to
      select the id
- [x] 6.3 Add `id` to `GetMeetingResult.Invitee` and `GetMeetingMapper`
- [x] 6.4 Add `id` to `GetMeetingResponse.Invitee` (with `@Schema`) ← (verify:
      GET meeting detail returns a non-empty `id` per active invitee)

## 7. Presentation: controller and DTOs

- [x] 7.1 Add `AddMeetingInviteesRequest` (`invitees` @NotEmpty, per-entry
      @Email/@NotBlank, in-request duplicate accountId assertion) with
      `toCommand()`
- [x] 7.2 Add `RemoveMeetingInviteesRequest` (`inviteeIds` @NotEmpty list of
      UUID) with `toCommand()`
- [x] 7.3 Add `AddMeetingInviteesResponse` and `RemoveMeetingInviteesResponse`
      (affected invitee snapshots) with `from(...)` factories
- [x] 7.4 Replace `updateInvitees` PUT handler with
      `POST /meetings/{id}/invitees` (add) and
      `POST /meetings/{id}/invitees:batchDelete` (remove), each 200 OK; update
      OpenAPI annotations/examples
- [x] 7.5 Delete `UpdateMeetingInviteesRequest` and
      `UpdateMeetingInviteesResponse` ← (verify: no
      `PUT /meetings/{id}/invitees` route remains; both new routes return 200)

## 8. Tests: add-meeting-invitees (from spec scenarios)

- [x] 8.1 Host adds new invitees → 200 with created snapshots (id, status
      NEEDS_ACTION)
- [x] 8.2 Non-host addition → 403, no invitee, no event
- [x] 8.3 Missing account header → 400, no invitee
- [x] 8.4 Unknown meeting → 404 MEETING_NOT_FOUND, no event
- [x] 8.5 Non-scheduled meeting → invalid-status, no invitee, no event
- [x] 8.6 Invalid invitee field → 400 VALIDATION_ERROR, nothing persisted
- [x] 8.7 Empty invitees list → 400 VALIDATION_ERROR
- [x] 8.8 In-request duplicate accountId → 400 VALIDATION_ERROR
- [x] 8.9 Already-active accountId → 409 INVITEE_ALREADY_EXISTS, nothing
      persisted, no event
- [x] 8.10 Re-adding a previously soft-deleted account → creates fresh
      NEEDS_ACTION invitee
- [x] 8.11 Successful add publishes exactly one `invitations.created`; failed
      add publishes none ← (verify: event assertions match spec)

## 9. Tests: remove-meeting-invitees (from spec scenarios)

- [x] 9.1 Host removes invitees → 200 with removed snapshots
- [x] 9.2 Non-host removal → 403, no invitee removed, no event
- [x] 9.3 Missing account header → 400
- [x] 9.4 Unknown meeting → 404 MEETING_NOT_FOUND, no event
- [x] 9.5 Non-scheduled meeting → invalid-status, nothing removed, no event
- [x] 9.6 Empty inviteeIds → 400 VALIDATION_ERROR
- [x] 9.7 Unknown invitee id in batch → 404 INVITEE_NOT_FOUND, nothing removed
- [x] 9.8 Already-removed invitee id in batch → 404 INVITEE_NOT_FOUND, nothing
      removed
- [x] 9.9 Successful remove publishes exactly one `invitations.deleted`; failed
      remove publishes none ← (verify: atomicity + event assertions match spec)

## 10. Tests: cleanup and read path

- [x] 10.1 Delete `MeetingInviteeUpdateDisplayNameTest` and the updated-event
      assertions in `MeetingRecordInviteesChangeTest`
- [x] 10.2 Delete/rewrite `UpdateMeetingInviteesApplicationServiceTest` and
      `UpdateMeetingInviteesControllerIntegrationTest` into Add/Remove
      equivalents
- [x] 10.3 Update meeting-detail tests to assert the invitee `id` field is
      present

## 11. Spec regeneration and verification

- [x] 11.1 Run
      `./services/gradlew -p services/meet generateOpenApiDocsFromTests` and
      confirm `openapi.yaml` shows the two POST routes and no PUT invitees route
- [x] 11.2 Run `./services/gradlew bufFormatApply spotlessApply`
- [x] 11.3 Run `./services/gradlew -p services/meet test integrationTest` (and
      `pnpm run openapi` for lint) ← (verify: full meet suite green; OpenAPI
      lints clean)
