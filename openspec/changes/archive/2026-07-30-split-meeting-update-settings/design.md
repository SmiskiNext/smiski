## Context

The meet service exposes `PUT /api/1/meetings/{id}` (`MeetingController#update`
→ `UpdateMeetingApplicationService` → `Meeting#update`). One call mutates both
information and settings and registers up to two events
(`MeetingInfoUpdatedEvent`, `MeetingSettingsUpdatedEvent`).

Investigation of the codebase established:

- `Meeting#update` guards host authorization and rejects `COMPLETED`/`CANCELED`;
  scheduled fields (`zoneId`, `timeRange`) are mutable only while `SCHEDULED`.
  It registers `MeetingInfoUpdatedEvent` when information changes and
  `MeetingSettingsUpdatedEvent` when settings change.
- `MeetingSettingsUpdatedEvent` is published to Kafka
  (`meeting.settings.update`) but **no consumer enforces it at runtime**; no
  connected participant is affected by a settings change today.
- `LiveKitAdapter#generateToken` grants participants a bare
  `CanPublish(mic || video || screenShare)` with **no** `CanPublishSources`.
  Because LiveKit treats an empty source list as "all sources allowed", a
  participant can publish a screen share whenever any media source is enabled —
  the `allowScreenShare=false` setting is not enforced.
- `LiveKitAdapter#updateParticipantPermissions` and `#updateRoomMetadata`
  currently throw `UnsupportedOperationException` (create-instant-meeting slice
  stubs). `#deleteRoom` is implemented and is the reference for best-effort
  LiveKit calls.
- `ParticipantGrants` already provides `fromSettingsForRuntimeSync` and
  `buildAllowedSources` (mapping settings → `microphone`, `camera`,
  `screen_share`, `screen_share_audio`) but nothing wires them in yet.
- `ParticipationLogRepository#findActiveByMeetingId` returns active (not-left)
  `ParticipationLog` rows; each exposes `getRole()` and `getLivekitIdentity()`,
  giving the connected-participant set and letting the host be skipped.
- LiveKit server SDK 0.13.0 (`RoomServiceClient`) provides
  `updateParticipant(room, identity, name, metadata, ParticipantPermission)`,
  `listParticipants(room)` (whose `ParticipantInfo#getPermission` exposes the
  current `canPublishData`), and the `TrackSource` enum (`MICROPHONE`, `CAMERA`,
  `SCREEN_SHARE`, `SCREEN_SHARE_AUDIO`). The `CanPublishSources(List<String>)`
  video grant sets sources on the join token.
- Errors flow as `Result<T, MeetingError>`; categories map to HTTP status
  (`FORBIDDEN`→403, `NOT_FOUND`→404, `VALIDATION`→400) and invalid transitions
  reuse `InvalidStatusTransition`.
- `MeetingController#update` and `UpdateMeetingUseCase` are the only callers of
  `Meeting#update`; no other internal caller depends on the combined behavior.

## Goals / Non-Goals

**Goals:**

- Split the combined update into `PUT /meetings/{id}` (information only) and
  `PUT /meetings/{id}/settings` (full settings block), each publishing only its
  own event.
- Enforce media publish permissions (microphone, camera, screen share) on
  connected non-host participants in real time when settings change.
- Fix the participant join token to restrict per source via `canPublishSources`,
  closing the screen-share bypass, with `canPublish=false` when no source is
  allowed.
- Preserve host-only authorization, status gating, and change-sensitive events.

**Non-Goals:**

- No runtime enforcement of chat (`canPublishData`); connected participants keep
  their current chat permission and only new tokens reflect the new value.
- No runtime effect of `admissionPolicy` or `maxParticipants` on connected
  participants (they still persist and gate future joins as today).
- No room-metadata signaling channel (LiveKit natively emits
  `ParticipantPermissionsChanged` to the affected client).
- No client/Forge app changes, no database schema migration (settings stay JSONB
  on the existing column).

## Decisions

### D1: Two endpoints — `PUT /meetings/{id}` (info) and `PUT /meetings/{id}/settings`

Information update stays a resource `PUT` and drops `settings` from its body.
Settings update is a full replacement of the settings sub-resource:
`PUT /meetings/{id}/settings`.

- **Why `PUT` on a `/settings` sub-resource**: The whole settings block is
  replaced (idempotent full representation), which is exactly `PUT` semantics on
  a sub-resource, consistent with the api-convention resource scheme. A
  `:action` suffix is reserved for non-CRUD operations, which this is not.
- **Alternative considered**: `PATCH` of individual flags. Rejected — the user
  chose full-block replacement, and it keeps one source of truth for settings
  validation identical to creation.

### D2: `PUT /meetings/{id}` becomes information-only (BREAKING)

Remove `settings` from `UpdateMeetingRequest`/`UpdateMeetingCommand` and from
the information update path. `Meeting#update` is split into `updateInfo(...)`
(registers only `MeetingInfoUpdatedEvent`, keeps the scheduled-field gate) and
`updateSettings(actor, newSettings)` (registers only
`MeetingSettingsUpdatedEvent`). The information endpoint calls `updateInfo`.

- **Why BREAKING is acceptable**: The combined body forced clients to resend
  full settings on any info edit and vice versa. The split is documented as
  BREAKING in `update-meeting`; the OpenAPI regeneration reflects the new
  contract.

### D3: Settings endpoint enforces media permissions on connected participants

`UpdateMeetingSettingsApplicationService` runs, within one transaction:
load-with-lock → host check → `updateSettings` → save + publish. After the
successful persist it enumerates
`participationLogRepository.findActiveByMeetingId(meetingId)`, skips the host
(`role == HOST`), and for each remaining participant calls
`liveKitPort.updateParticipantPermissions(room, identity, grants)` where
`grants` is derived from the new settings via
`ParticipantGrants.fromSettingsForRuntimeSync`.

- **Why enforce server-side over room metadata**: Room metadata is advisory —
  enforcement would live in the client and be bypassable. Updating the
  participant permission makes LiveKit block the track at the server and
  natively notify the affected client (`ParticipantPermissionsChanged`), so no
  separate signaling channel is needed. The user chose server-side enforcement.
- **Why skip the host**: The host always retains full permission (`speaker()`),
  matching the join-token policy.

### D4: Best-effort enforcement; settings are the source of truth

Persisted settings commit regardless of LiveKit enforcement outcome. A failed or
partial `updateParticipant` call is logged at warn and swallowed (mirroring the
`EndMeeting#deleteRoom` best-effort pattern). Connected participants re-sync on
their next reconnect because new tokens are derived from the persisted settings.

- **Why not roll back on LiveKit failure**: The database is the durable source
  of truth for future joins; coupling settings persistence to a best-effort
  media call would make the API brittle. The user accepted this trade-off.

### D5: Preserve chat permission during runtime enforcement

`updateParticipant` replaces the participant's whole `ParticipantPermission`, so
enforcement MUST NOT clobber `canPublishData`. The adapter reads the
participant's current permission (via `listParticipants` /
`ParticipantInfo#getPermission#getCanPublishData`) and preserves it while
setting `canPublish` and `canPublishSources` from the new settings. The domain
grant used for runtime sync therefore carries only the media decision; chat is
preserved at the adapter boundary.

- **Alternative considered**: Also push `canPublishData = chatEnabled`. Rejected
  — the user chose to exclude chat from runtime enforcement.

### D6: Join token uses explicit `canPublishSources`

`generateToken` adds `CanPublishSources(buildAllowedSources(settings))` for
participant tokens. When no media source is allowed, it grants
`CanPublish(false)` and omits sources so nothing is publishable. The host token
is unchanged (full permission).

- **Why**: An empty source list means "all allowed" in LiveKit; explicit sources
  are required to enforce `allowScreenShare=false` for new joiners.

### Settings update + enforcement flow

```mermaid
sequenceDiagram
    participant C as Client (host)
    participant Ctrl as MeetingController
    participant UC as UpdateMeetingSettingsService
    participant MR as MeetingRepository
    participant PR as ParticipationLogRepository
    participant EP as EventPublisher
    participant LK as LiveKitPort
    C->>Ctrl: PUT /meetings/{id}/settings {settings}
    Ctrl->>UC: execute(UpdateMeetingSettingsCommand)
    UC->>MR: findByIdWithLock(id)
    alt not found
        UC-->>Ctrl: failure MEETING_NOT_FOUND (404)
    end
    UC->>UC: assert host; updateSettings() (rejects COMPLETED/CANCELED)
    alt not host
        UC-->>Ctrl: failure NOT_AUTHORIZED (403)
    end
    UC->>MR: save(meeting) with meeting.settings.update
    UC->>EP: publishEventsOf(meeting)
    UC->>PR: findActiveByMeetingId(id)
    loop each active participant where role != HOST
        UC->>LK: updateParticipantPermissions(room, identity, grants)
        Note over LK: preserves canPublishData; best-effort (warn on failure)
    end
    UC-->>Ctrl: success (settings snapshot)
    Ctrl-->>C: 200 OK {settings}
```

## Risks / Trade-offs

- **Breaking API change on `PUT /meetings/{id}`** → Mitigation: documented as
  BREAKING in `update-meeting`; OpenAPI regenerated; only the update chain calls
  the split domain method, so no internal caller silently breaks.
- **Best-effort enforcement leaves a window** → A connected participant may keep
  publishing a now-disallowed source until the LiveKit call lands or they
  reconnect. Mitigation: enforcement runs immediately after commit; failures are
  logged and self-heal on reconnect since tokens derive from persisted settings.
- **`updateParticipant` replaces the whole permission** → Risk of clobbering
  chat. Mitigation (D5): read and preserve the participant's current
  `canPublishData`.
- **Extra LiveKit round-trips per participant** → For large rooms, per-identity
  `updateParticipant` calls add latency after commit. Mitigation: calls run
  after the response-relevant work and failures are non-fatal; batching is out
  of scope and can be revisited if room sizes grow.
- **Host detection depends on `ParticipationLog.role`** → If role is
  mis-recorded, the host could be demoted. Mitigation: role is set at token
  issue time from the same source as the join token; enforcement skips `HOST`
  and the host token is always full-permission.

## Migration Plan

- Deploy is backward-incompatible for the `PUT /meetings/{id}` request shape.
  Clients that previously sent `settings` in that body MUST move settings
  changes to `PUT /meetings/{id}/settings`. No data migration is required.
- Rollback: revert the change set; persisted settings JSONB is unchanged in
  shape, so no schema rollback is needed.

## Open Questions

- None. Scope, endpoint shapes, runtime-enforcement mechanism, chat exclusion,
  and best-effort semantics were confirmed with the user during planning.
