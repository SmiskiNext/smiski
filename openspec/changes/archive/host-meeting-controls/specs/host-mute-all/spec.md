# ADDED Requirements

## Requirement: Host can mute all active participants at once

The system SHALL provide a host-only endpoint that mutes the microphone of every
active PARTICIPANT session in a live meeting in a single request. The operation
SHALL be best-effort: a failure to mute one participant SHALL NOT prevent muting
the remaining participants. HOST and GUEST sessions SHALL be excluded from the
operation.

### Scenario: Successful bulk mute

- **WHEN** the authenticated host sends
  `POST /v1.0/meetings/{id}/participants:muteAll` on a LIVE meeting that has
  active PARTICIPANT sessions
- **THEN** the system mutes the microphone track of each active PARTICIPANT via
  LiveKit and returns HTTP 204

### Scenario: Empty participant list

- **WHEN** the host sends `POST /v1.0/meetings/{id}/participants:muteAll` but
  there are no active PARTICIPANT sessions (only HOST or GUEST)
- **THEN** the system returns HTTP 204 without calling LiveKit

### Scenario: Non-host requester rejected

- **WHEN** a non-host authenticated user sends
  `POST /v1.0/meetings/{id}/participants:muteAll`
- **THEN** the system returns HTTP 403

### Scenario: Meeting not live

- **WHEN** the host sends `POST /v1.0/meetings/{id}/participants:muteAll` on a
  meeting that is not in LIVE status
- **THEN** the system returns HTTP 422

### Scenario: Single participant LiveKit failure does not abort

- **WHEN** the host sends `POST /v1.0/meetings/{id}/participants:muteAll` and
  one participant's LiveKit mute call fails (e.g., participant already left)
- **THEN** the system continues muting remaining participants and returns HTTP
  204 if at least one succeeded or the failure was a non-fatal skip

### Scenario: All LiveKit calls fail

- **WHEN** the host sends `POST /v1.0/meetings/{id}/participants:muteAll` and
  every LiveKit mute call fails due to server unavailability
- **THEN** the system returns HTTP 503

## Requirement: Mute state propagates to all meeting clients automatically

After the host triggers bulk mute, the LiveKit server SHALL emit `TrackMuted`
data-plane events to all connected clients without any additional server-sent
signaling from the meeting-management service.

### Scenario: Client receives mute event after bulk mute

- **WHEN** `muteAll` is executed successfully for a participant
- **THEN** all subscribed LiveKit clients (including Android) receive a
  `TrackMuted` event for the affected participant's audio track and update their
  local participant list accordingly
