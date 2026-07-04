## MODIFIED Requirements

### Requirement: LiveKit permissions are synchronized for active live participants

The system SHALL asynchronously reconcile LiveKit participant permissions after
a LIVE meeting's permission-related settings are changed, propagating both
boolean publish grants and the source-level publishable list, and SHALL forcibly
mute any in-flight publication that the new settings disallow.

#### Scenario: Runtime sync runs only for live meetings with relevant setting changes

- **WHEN** `MeetingSettingsUpdatedEvent` is handled for a meeting that is not
  LIVE
- **THEN** the system SHALL NOT attempt LiveKit participant permission updates
- **THEN** the system SHALL complete without scanning active sessions

#### Scenario: Runtime sync ignores non-permission setting changes

- **WHEN** `MeetingSettingsUpdatedEvent` changes only fields outside
  `allowMicrophone`, `allowVideo`, `allowScreenShare`, and `chatEnabled`
- **THEN** the system SHALL NOT attempt LiveKit participant permission updates

#### Scenario: Runtime sync updates only active participant sessions

- **WHEN** a LIVE meeting changes one or more permission-related settings
- **THEN** the system SHALL load active sessions using
  `ParticipationLogRepository.findActiveByMeetingId()`
- **THEN** the system SHALL skip HOST sessions
- **THEN** the system SHALL skip GUEST sessions
- **THEN** the system SHALL call `LiveKitPort.updateParticipantPermissions()`
  for each active PARTICIPANT session using grants derived from the new meeting
  settings

#### Scenario: Runtime sync propagates source-level publishable list

- **WHEN** `LiveKitPort.updateParticipantPermissions()` is called for a
  PARTICIPANT during runtime sync
- **THEN** the call SHALL carry the same set of allowed publish sources that the
  system would derive for a fresh PARTICIPANT token under the new settings
  (subset of `microphone`, `camera`, `screen_share`, `screen_share_audio`)
- **THEN** the underlying LiveKit `ParticipantPermission` proto SHALL set
  `canPublishSources` from that list

#### Scenario: Runtime sync mutes outstanding screen-share track when revoked

- **WHEN** the new settings flip `allowScreenShare` from true to false on a LIVE
  meeting
- **THEN** for each active PARTICIPANT session, the system SHALL request
  `LiveKitPort.muteParticipantTrack(roomName, identity, "screen_share")`
- **THEN** the system SHALL ignore failures of type "track not found" or
  "participant not found" and continue to the next session
- **THEN** HOST and GUEST sessions SHALL NOT be muted

#### Scenario: Runtime sync is best effort per participant

- **WHEN** updating permissions fails for one active PARTICIPANT session
- **THEN** the system SHALL log the failure with meeting and participant context
- **THEN** the system SHALL continue attempting updates for the remaining active
  PARTICIPANT sessions
