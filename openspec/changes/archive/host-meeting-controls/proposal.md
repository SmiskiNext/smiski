# Why

Meeting hosts currently have no way to control participant audio or video
remotely. This creates disruption in meetings when participants have background
noise or accidental video on, forcing hosts to rely on verbal requests with no
enforcement capability.

## What Changes

- Add `POST /v1.0/meetings/{id}/participants:muteAll` endpoint to mute all
  non-host participant microphones at once
- Add `POST /v1.0/meetings/{id}/participants/{identity}:muteTrack` endpoint to
  mute a specific participant's microphone or camera
- Add `MuteAllParticipantsUseCase` and `MuteParticipantTrackUseCase` in the
  application layer
- Add `muteParticipantTrack` and `muteAllParticipantTracks` operations to
  `LiveKitPort` and `LiveKitAdapter`
- Add `CanNotMuteSelf` and `TrackNotFound` error variants to `MeetingError`
- Update Android `ParticipantRepository` with `muteAll` and `muteTrack` methods
- Update Android `CallViewModel` to implement the previously stubbed
  `muteAllParticipants()` and add `muteParticipantTrack()`
- Enable the `btnMuteAll` control in `ParticipantsBottomSheet` (host-only
  visibility)
- Add per-participant mute actions in `ParticipantAdapter` for host users

## Capabilities

### New Capabilities

- `host-mute-all`: Host-initiated bulk mute of all active participant
  microphones via a single API call, with best-effort semantics
- `host-mute-participant-track`: Host-initiated per-participant microphone or
  camera mute via the moderation API, enforced server-side through LiveKit track
  muting

### Modified Capabilities

- `android-real-participant-list`: Per-participant host action affordances
  (mic/camera mute buttons) added to the participant list UI

## Impact

- **Backend**: `services/meeting-management` — new endpoints in
  `ParticipantController`, two new use cases, extended
  `LiveKitPort`/`LiveKitAdapter`, two new `MeetingError` variants
- **Android**: `CallViewModel`, `ParticipantRepositoryImpl`,
  `ParticipantRepository` (domain), `ParticipantsBottomSheet`,
  `ParticipantAdapter` — UI controls and repository wiring
- **OpenAPI spec**: New endpoints trigger OpenAPI YAML regeneration and Android
  API client regeneration (`ParticipantsApi`)
- **No new dependencies** — uses existing LiveKit Server SDK
  (`RoomServiceClient`) and existing patterns
