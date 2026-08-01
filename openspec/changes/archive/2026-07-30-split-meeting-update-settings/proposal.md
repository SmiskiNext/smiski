## Why

The single `PUT /api/1/meetings/{id}` endpoint updates meeting information and
settings together, coupling two distinct host intents behind one full-body call.
Worse, changing settings has no runtime effect: participant permissions are
never re-derived, and the participant join token grants a coarse `canPublish`
boolean without per-source restriction, so a participant can share their screen
even when the host has disabled screen sharing. Splitting update into an info
endpoint and a settings endpoint makes each intent first-class and lets the
settings endpoint enforce media policy on connected participants in real time.

## What Changes

- **BREAKING** `PUT /api/1/meetings/{id}` no longer accepts or updates
  `settings`. It updates information fields only (`title`, `description`,
  `issueLink`, `zoneId`, `timeRange`) and publishes only `meeting.info.update`.
- Add `PUT /api/1/meetings/{id}/settings` that replaces the entire meeting
  settings block (`admissionPolicy`, `maxParticipants`, `allowMicrophone`,
  `allowVideo`, `allowScreenShare`, `chatEnabled`), is host-only, permitted
  while `SCHEDULED` or `RUNNING`, and publishes `meeting.settings.update`.
- The settings endpoint enforces media policy on **connected** participants in
  real time: for every active (not-left) participant that is not the host, it
  updates their LiveKit publish permission so the allowed track sources
  (microphone, camera, screen_share, screen_share_audio) match the new settings.
  Enforcement is best-effort and does not roll back the persisted settings.
- Chat permission (`canPublishData`) is persisted in settings but **not**
  enforced at runtime: connected participants keep their current chat
  permission; only newly issued tokens reflect the new chat value.
- Fix the participant join token to grant explicit `canPublishSources` derived
  from settings instead of a bare `canPublish` flag, so a newly joining
  participant is restricted per source (closing the "microphone on ⇒ screen
  share allowed" gap). When no media source is allowed, the token grants
  `canPublish=false`.
- The host always retains full publish permission regardless of settings, both
  in the join token and during runtime enforcement.

## Capabilities

### New Capabilities

- `update-meeting-settings`: Host-only replacement of the full meeting settings
  block on a `SCHEDULED` or `RUNNING` meeting, publishing
  `meeting.settings.update` and enforcing media publish permissions on connected
  non-host participants in real time via LiveKit (chat excluded from runtime
  enforcement).

### Modified Capabilities

- `update-meeting`: `PUT /api/1/meetings/{id}` becomes information-only. Remove
  the `settings` field from its request contract, remove settings from its
  mutable-field and validation requirements, and reduce its event contract to
  `meeting.info.update` only.
- `join-meeting`: The participant join token SHALL restrict publishing per track
  source using `canPublishSources` derived from settings (microphone, camera,
  screen share), rather than a single `canPublish` flag; the host token remains
  fully permissioned.

## Impact

- **meet service (backend)**:
    - domain: `Meeting` (split `update` into information-only and settings-only
      behaviors), `ParticipantGrants` (per-source derivation and chat-preserving
      runtime grants).
    - infrastructure: `LiveKitAdapter` (`generateToken` uses
      `CanPublishSources`; implement `updateParticipantPermissions` via
      `RoomServiceClient.updateParticipant` with a `ParticipantPermission` that
      preserves the participant's current `canPublishData`).
    - application: keep `UpdateMeeting*` as information-only; add
      `UpdateMeetingSettings*` (use case, service, command, result, mapper) that
      enumerates active non-host participants and enforces permissions
      best-effort.
    - presentation: `MeetingController` (info handler drops settings; new
      settings handler), `UpdateMeetingRequest`/`UpdateMeetingResponse` (drop
      settings), new
      `UpdateMeetingSettingsRequest`/`UpdateMeetingSettingsResponse`.
- **meet OpenAPI spec** — regenerated `services/meet/openapi.yaml` (info route
  loses settings; new settings route added).
- **meet tests** — unit (domain split, grants), application (settings service +
  enforcement with mocked `LiveKitPort`), integration (info route without
  settings, new settings route, event assertions).
- **Out of scope** — no room-metadata banner channel; no chat runtime
  enforcement; no `admissionPolicy`/`maxParticipants` runtime effect on
  connected participants; no client/Forge app changes; no database schema
  migration (settings remain JSONB on the existing column).
