## MODIFIED Requirements

### Requirement: Host LiveKit access token issuance

The system SHALL issue a LiveKit access token granting HOST permissions for the
room `meeting-<meetingId>`, using the host's LiveKit identity and display name,
and SHALL set the host's `role` and (when provided) `avatarUrl` as real LiveKit
participant attributes on the token. The token SHALL additionally carry a
LiveKit room configuration whose room name is `meeting-<meetingId>` and whose
room metadata carries the owning tenant identifier, so that the LiveKit room
created on first join exposes the tenant in its metadata for later
room/participant webhooks. The token SHALL be returned in the creation response.
The tenant identifier SHALL NOT appear in the creation response body. If the
LiveKit token cannot be generated, the creation SHALL fail and roll back rather
than returning a meeting the host cannot join.

#### Scenario: Host token issued for the meeting room

- **WHEN** an instant meeting is created successfully
- **THEN** the response `livekit.token` is a non-empty token scoped to room
  `meeting-<meetingId>` for the host identity with host-level permissions

#### Scenario: Host token embeds tenant in room metadata

- **WHEN** an instant meeting is created successfully for a given tenant
- **THEN** the issued LiveKit token carries a room configuration for room
  `meeting-<meetingId>` whose metadata carries that tenant identifier, and the
  tenant identifier is absent from the creation response body

#### Scenario: Host avatar attached as participant attribute

- **WHEN** a request includes `host.avatarUrl`
- **THEN** the issued LiveKit token carries a participant attribute `avatarUrl`
  with that value and a participant attribute `role` = `HOST`

#### Scenario: Host avatar omitted leaves no avatarUrl attribute

- **WHEN** a request omits `host.avatarUrl` or sends null
- **THEN** the issued LiveKit token carries a participant attribute `role` =
  `HOST` but no `avatarUrl` attribute

#### Scenario: LiveKit unavailable fails creation

- **WHEN** the LiveKit token cannot be generated during creation
- **THEN** the response is a Problem Details error indicating LiveKit is
  unavailable and the transaction rolls back so no meeting is persisted
