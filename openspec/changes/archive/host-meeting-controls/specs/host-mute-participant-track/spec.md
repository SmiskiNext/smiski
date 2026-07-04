# ADDED Requirements

## Requirement: Host can mute a specific participant's microphone or camera

The system SHALL provide a host-only endpoint that mutes a single published
track (microphone or camera) for a specific participant in a live meeting. The
host SHALL NOT be able to mute their own tracks via this endpoint. If the target
participant has no published track of the requested source type, the system
SHALL return an error.

### Scenario: Successful per-participant microphone mute

- **WHEN** the authenticated host sends
  `POST /v1.0/meetings/{id}/participants/{identity}:muteTrack?source=microphone`
  on a LIVE meeting and the target participant has a published microphone track
- **THEN** the system resolves the track SID via LiveKit and mutes that track,
  then returns HTTP 204

### Scenario: Successful per-participant camera mute

- **WHEN** the authenticated host sends
  `POST /v1.0/meetings/{id}/participants/{identity}:muteTrack?source=camera` on
  a LIVE meeting and the target participant has a published camera track
- **THEN** the system resolves the track SID via LiveKit and mutes that track,
  then returns HTTP 204

### Scenario: Target participant has no published track of the requested source

- **WHEN** the host sends
  `POST /v1.0/meetings/{id}/participants/{identity}:muteTrack?source=camera` and
  the target participant has not published a camera track
- **THEN** the system returns HTTP 422 with error code `TRACK_NOT_FOUND`

### Scenario: Host attempts to mute themselves

- **WHEN** the host sends
  `POST /v1.0/meetings/{id}/participants/{identity}:muteTrack` where
  `{identity}` resolves to the host's own LiveKit identity
- **THEN** the system returns HTTP 422 with error code `CANNOT_MUTE_SELF`

### Scenario: Non-host requester rejected

- **WHEN** a non-host authenticated user sends
  `POST /v1.0/meetings/{id}/participants/{identity}:muteTrack`
- **THEN** the system returns HTTP 403

### Scenario: Meeting not live

- **WHEN** the host sends
  `POST /v1.0/meetings/{id}/participants/{identity}:muteTrack` on a non-LIVE
  meeting
- **THEN** the system returns HTTP 422

### Scenario: Target participant not in room

- **WHEN** the host sends
  `POST /v1.0/meetings/{id}/participants/{identity}:muteTrack` and LiveKit
  returns 404 for that identity
- **THEN** the system returns HTTP 404 with error code `PARTICIPANT_NOT_FOUND`

## Requirement: Track-level mute does not revoke publish permission

The system SHALL mute a track without revoking the participant's publish
permission. The participant SHALL retain the ability to unmute their own track
locally at any time after the host mutes it.

### Scenario: Participant can unmute after host mutes their microphone

- **WHEN** the host mutes a participant's microphone via `muteTrack`
- **THEN** the participant's publish permission remains unchanged and the
  participant can re-enable their microphone locally

## Requirement: Host cannot remotely unmute participants

The system SHALL NOT expose any endpoint or operation that allows the host to
enable a participant's microphone or camera remotely.

### Scenario: No unmute endpoint exists

- **WHEN** any client attempts to call a remote unmute operation
- **THEN** the system returns HTTP 405 or 404 (no such operation is defined)
