## MODIFIED Requirements

### Requirement: Immediate join under ALLOW_ALL admission

The join operation SHALL, when the target meeting's admission policy is
`ALLOW_ALL`, admit the caller immediately: it SHALL generate a LiveKit access
token for the caller as a `PARTICIPANT` whose participant attributes carry the
caller's `role` and, when supplied, `avatarUrl`, and return `200 OK` with
`status` `APPROVED`, a generated correlation `requestId`, the LiveKit token, and
the room name. The issued token SHALL additionally carry a LiveKit room
configuration whose room name is `meeting-<meetingId>` and whose room metadata
carries the owning tenant identifier, so the LiveKit room exposes the tenant in
its metadata for later room/participant webhooks. The join operation SHALL NOT
record a participation log; recording the participation session is deferred to
the `participant_joined` webhook (the source of truth), which prevents orphan
logs for callers who obtain a token but never connect. Capacity SHALL be
enforced so that the number of active participants never exceeds the meeting's
`maxParticipants`, evaluated so that concurrent joins cannot exceed the limit.

#### Scenario: Open meeting admits the caller

- **WHEN** a caller joins an `ALLOW_ALL` meeting that has capacity
- **THEN** the response is `200` with `status` `APPROVED`, a generated
  `requestId`, a non-empty LiveKit token whose participant attributes carry
  `role` and (when supplied) `avatarUrl`, and the room name; no participation
  log is recorded by the join operation itself

#### Scenario: Participant token embeds tenant in room metadata

- **WHEN** a caller is admitted to an `ALLOW_ALL` meeting for a given tenant
- **THEN** the issued LiveKit token carries a room configuration for room
  `meeting-<meetingId>` whose metadata carries that tenant identifier

#### Scenario: Meeting at capacity is rejected

- **WHEN** a caller joins an `ALLOW_ALL` meeting whose active participants
  already equal `maxParticipants`
- **THEN** the response is `409` `application/problem+json` with a meeting-full
  code and no new participation log is created

#### Scenario: Concurrent joins do not exceed capacity

- **WHEN** multiple callers join simultaneously such that only one seat remains
- **THEN** at most one additional caller is admitted and the others receive the
  meeting-full error, so the active participant count never exceeds
  `maxParticipants`
