# Tasks

## 1. Domain: split update into information and settings behaviors

- [x] 1.1 Split `Meeting#update` into
      `updateInfo(actor, title, description,     issueLink, timeZone, timeRange)`
      that mutates only information fields, keeps the host-auth and
      `COMPLETED`/`CANCELED` rejection, keeps the scheduled-field
      (`zoneId`/`timeRange`) `SCHEDULED`-only gate, and registers only
      `MeetingInfoUpdatedEvent`
- [x] 1.2 Add `Meeting#updateSettings(actor, newSettings)` that enforces
      host-auth and rejects `COMPLETED`/`CANCELED`, replaces the settings VO,
      and registers only `MeetingSettingsUpdatedEvent`; no-op when new settings
      equal current ← (verify: info path emits only info event, settings path
      emits only settings event, no-op emits none, COMPLETED/CANCELED rejected)

## 2. Domain: per-source and chat-preserving grants

- [x] 2.1 Ensure `ParticipantGrants.buildAllowedSources` returns explicit
      sources (`microphone`, `camera`, `screen_share`, `screen_share_audio`)
      from settings
- [x] 2.2 Provide a runtime-sync grant derivation that carries the media
      decision (canPublish + allowed sources) and leaves chat (`canPublishData`)
      to be preserved at the adapter boundary, and yields `canPublish=false`
      with no sources when no media source is enabled ← (verify: no-media ⇒
      canPublish false; each enabled flag maps to its source; host ⇒ full
      permission)

## 3. Infrastructure: LiveKit token and runtime permission

- [x] 3.1 Update `LiveKitAdapter#generateToken` so `PARTICIPANT` tokens add an
      explicit `CanPublishSources` derived from settings and grant no publish
      when no source is allowed; leave `HOST` tokens fully permissioned
- [x] 3.2 Implement `LiveKitAdapter#updateParticipantPermissions` using
      `RoomServiceClient.updateParticipant` with a `ParticipantPermission` that
      sets `canPublish`/`canPublishSources` from the grant while preserving the
      participant's current `canPublishData` (read via `listParticipants` /
      `ParticipantInfo#getPermission`) ← (verify: screen_share excluded when
      disabled; chat permission preserved; network failure returns a
      `LiveKitUnavailable` failure rather than throwing)

## 4. Application: information-only update path

- [x] 4.1 Remove `Settings` from `UpdateMeetingCommand`
- [x] 4.2 Update `UpdateMeetingApplicationService` to call `Meeting#updateInfo`
      and stop constructing/persisting settings; keep returning the meeting
      snapshot
- [x] 4.3 Ensure `UpdateMeetingResult` no longer sources settings from this path
      (retain settings in the snapshot read from the persisted meeting) ←
      (verify: info update never publishes `meeting.settings.update`)

## 5. Application: settings replacement use case with enforcement

- [x] 5.1 Add `UpdateMeetingSettingsCommand` (meetingId, tenantId, accountId,
      settings block)
- [x] 5.2 Add `UpdateMeetingSettingsResult` (updated settings snapshot) and its
      mapper
- [x] 5.3 Add `UpdateMeetingSettingsUseCase` interface
- [x] 5.4 Add `UpdateMeetingSettingsApplicationService`: load-with-lock, build
      and validate settings VO, call `Meeting#updateSettings`, save + publish
      when changed, then enumerate
      `ParticipationLogRepository#findActiveByMeetingId`, skip `role == HOST`,
      and best-effort call `LiveKitPort#updateParticipantPermissions` per
      participant (log warn on failure, no rollback) ← (verify:
      403/404/invalid-status persist nothing and publish no event; success
      publishes exactly one settings event; LiveKit failure does not roll back;
      host skipped)

## 6. Presentation: split endpoints and DTOs

- [x] 6.1 Remove `Settings` from `UpdateMeetingRequest` (and its `toCommand`)
      and from `UpdateMeetingResponse` request examples/schema as needed for the
      information-only contract
- [x] 6.2 Add `UpdateMeetingSettingsRequest` (full settings block with the same
      validation as creation: `admissionPolicy` pattern, `maxParticipants`
      `[2..100]`, boolean flags) with `toCommand()`
- [x] 6.3 Add `UpdateMeetingSettingsResponse` with a `from(...)` factory
- [x] 6.4 Change `MeetingController#update` to the information-only contract and
      add a `PUT /meetings/{id}/settings` handler that resolves account/tenant,
      calls the settings use case, and maps the result via `ResultResponder`;
      update OpenAPI annotations/examples ← (verify: `PUT /meetings/{id}`
      rejects a body carrying settings-only changes as no info change; new
      settings route returns 200 with the settings snapshot)

## 7. Tests: update-meeting information endpoint (from spec scenarios)

- [x] 7.1 Scheduled meeting accepts title/description/issueLink/zoneId/timeRange
- [x] 7.2 Running meeting accepts information; rejects zoneId/timeRange change
- [x] 7.3 Completed and canceled meetings reject updates
- [x] 7.4 Invalid zoneId / invalid time range / blank field → 400
      VALIDATION_ERROR, nothing persisted
- [x] 7.5 Information change publishes exactly one `meeting.info.update`; no-op
      publishes none; failed update publishes none; no `meeting.settings.update`
      is published by this endpoint ← (verify: event assertions match the
      modified `update-meeting` spec)

## 8. Tests: update-meeting-settings endpoint (from spec scenarios)

- [x] 8.1 Host replaces settings → 200 with updated settings snapshot
- [x] 8.2 Non-host → 403; missing account header → 400; unknown meeting → 404
      MEETING_NOT_FOUND with no event
- [x] 8.3 Completed and canceled meetings reject replacement with no event
- [x] 8.4 Invalid `maxParticipants` and invalid `admissionPolicy` → 400
      VALIDATION_ERROR, nothing persisted
- [x] 8.5 Effective change publishes exactly one `meeting.settings.update`;
      no-op publishes none; failed replacement publishes none ← (verify: event
      assertions match the `update-meeting-settings` spec)

## 9. Tests: runtime enforcement and join token (from spec scenarios)

- [x] 9.1 Application test with mocked `LiveKitPort`: disabling screen share
      updates each connected non-host participant to exclude screen_share; host
      is skipped
- [x] 9.2 Application test: enabling a previously disabled source updates
      connected non-host participants to include it
- [x] 9.3 Application test: LiveKit failure is best-effort — settings and event
      stay committed, no exception surfaces
- [x] 9.4 Adapter/unit test: `updateParticipantPermissions` preserves the
      participant's current `canPublishData`
- [x] 9.5 Token test: participant token excludes screen share when disabled;
      grants no publish when all media disabled; host token stays fully
      permissioned ← (verify: token-source assertions match the `join-meeting`
      added requirement)

## 10. Spec regeneration and verification

- [x] 10.1 Run
      `./services/gradlew -p services/meet generateOpenApiDocsFromTests` and
      confirm `openapi.yaml` shows `PUT /meetings/{id}` without settings and the
      new `PUT /meetings/{id}/settings` route
- [x] 10.2 Run `./services/gradlew spotlessApply`
- [x] 10.3 Run `./services/gradlew -p services/meet test integrationTest` (and
      `pnpm run openapi` for lint) ← (verify: full meet suite green; OpenAPI
      lints clean)
