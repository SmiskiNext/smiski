# Purpose

Define how LiveKit participant permissions are derived from meeting settings for
token issuance and synchronized for active live participants after permission
changes.

# ADDED Requirements

## Requirement: LiveKit participant tokens honor meeting settings

The system SHALL derive LiveKit join-token permissions from both
`ParticipantRole` and the current `MeetingSettings` for the meeting.

### Scenario: Host token remains unrestricted

- **WHEN** the system generates a LiveKit token for a HOST
- **THEN** the token SHALL retain full publish, publish-data, subscribe, and
  admin capabilities regardless of meeting settings

### Scenario: Guest token remains subscribe-only

- **WHEN** the system generates a LiveKit token for a GUEST
- **THEN** the token SHALL allow subscribe-only participation regardless of
  meeting settings

### Scenario: Participant token filters publish sources from meeting settings

- **WHEN** the system generates a LiveKit token for a PARTICIPANT
- **THEN** `allowMicrophone=false` SHALL exclude the `microphone` source from
  the token's publishable sources
- **THEN** `allowVideo=false` SHALL exclude the `camera` source from the token's
  publishable sources
- **THEN** `allowScreenShare=false` SHALL exclude `screen_share` and
  `screen_share_audio` from the token's publishable sources
- **THEN** `chatEnabled=false` SHALL disable data publishing for that token

### Scenario: Participant token disables media publish when all sources are blocked

- **WHEN** the system generates a LiveKit token for a PARTICIPANT and meeting
  settings disable microphone, camera, and screen sharing
- **THEN** the token SHALL set media publishing to disabled
- **THEN** the token SHALL still allow subscribe access

## Requirement: LiveKit permissions are synchronized for active live participants

The system SHALL asynchronously reconcile LiveKit participant permissions after
a LIVE meeting's permission-related settings are changed.

### Scenario: Runtime sync runs only for live meetings with relevant setting changes

- **WHEN** `MeetingSettingsUpdatedEvent` is handled for a meeting that is not
  LIVE
- **THEN** the system SHALL NOT attempt LiveKit participant permission updates
- **THEN** the system SHALL complete without scanning active sessions

### Scenario: Runtime sync ignores non-permission setting changes

- **WHEN** `MeetingSettingsUpdatedEvent` changes only fields outside
  `allowMicrophone`, `allowVideo`, `allowScreenShare`, and `chatEnabled`
- **THEN** the system SHALL NOT attempt LiveKit participant permission updates

### Scenario: Runtime sync updates only active participant sessions

- **WHEN** a LIVE meeting changes one or more permission-related settings
- **THEN** the system SHALL load active sessions using
  `ParticipationLogRepository.findActiveByMeetingId()`
- **THEN** the system SHALL skip HOST sessions
- **THEN** the system SHALL skip GUEST sessions
- **THEN** the system SHALL call `LiveKitPort.updateParticipantPermissions()`
  for each active PARTICIPANT session using grants derived from the new meeting
  settings

### Scenario: Runtime sync is best effort per participant

- **WHEN** updating permissions fails for one active PARTICIPANT session
- **THEN** the system SHALL log the failure with meeting and participant context
- **THEN** the system SHALL continue attempting updates for the remaining active
  PARTICIPANT sessions
