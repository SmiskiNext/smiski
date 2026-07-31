# update-meeting-settings Specification

## Purpose

TBD - created by archiving change split-meeting-update-settings. Update Purpose
after archive.

## Requirements

### Requirement: Host-only meeting settings replacement endpoint

The system SHALL expose `PUT /api/1/meetings/{id}/settings` for replacing the
entire meeting settings block. The endpoint SHALL require the `edit-meeting`
project permission — if the caller's permission context does not contain
`edit-meeting`, the endpoint SHALL reject the request with
`403 application/problem+json` and code `NOT_AUTHORIZED` before executing the
use case. The acting account SHALL be resolved from the configured account
header, the tenant SHALL be resolved from the tenant context, and only the
meeting host SHALL be authorized to change settings. The request SHALL carry the
full settings representation (`admissionPolicy`, `maxParticipants`,
`allowMicrophone`, `allowVideo`, `allowScreenShare`, `chatEnabled`). A
successful replacement SHALL return `200 OK` with the updated settings snapshot.

#### Scenario: Host replaces settings

- **WHEN** the host sends a valid `PUT /api/1/meetings/{id}/settings` request
  with the account header and has `edit-meeting` permission
- **THEN** the system persists the new settings and returns `200 OK` with the
  updated settings snapshot

#### Scenario: Missing edit-meeting permission is rejected

- **WHEN** a caller without `edit-meeting` sends
  `PUT /api/1/meetings/{id}/settings`
- **THEN** the response is `403 application/problem+json` with code
  `NOT_AUTHORIZED` and the settings are not changed

#### Scenario: Non-host settings change is rejected

- **WHEN** an account that is not the meeting host sends a settings request even
  with `edit-meeting` permission
- **THEN** the system returns an RFC 9457 Problem Details response with the
  authorization error code and does not change the settings

### Requirement: Status-gated settings replacement

The system SHALL allow the host to replace settings when the meeting status is
`SCHEDULED` or `RUNNING`, and SHALL reject settings replacement when the meeting
status is `COMPLETED` or `CANCELED`.

#### Scenario: Scheduled meeting accepts settings replacement

- **WHEN** the host replaces settings on a `SCHEDULED` meeting
- **THEN** the new settings are persisted and the meeting remains `SCHEDULED`

#### Scenario: Running meeting accepts settings replacement

- **WHEN** the host replaces settings on a `RUNNING` meeting
- **THEN** the new settings are persisted and the meeting remains `RUNNING`

#### Scenario: Completed meeting rejects settings replacement

- **WHEN** the host attempts to replace settings on a `COMPLETED` meeting
- **THEN** the replacement is rejected, the settings remain unchanged, and no
  event is published

#### Scenario: Canceled meeting rejects settings replacement

- **WHEN** the host attempts to replace settings on a `CANCELED` meeting
- **THEN** the replacement is rejected, the settings remain unchanged, and no
  event is published

### Requirement: Settings input validation

The system SHALL validate the settings representation using the same constraints
as meeting creation: a valid `admissionPolicy`, `maxParticipants` in `[2..100]`,
and boolean media and chat flags. Invalid requests SHALL return `400` Problem
Details with `VALIDATION_ERROR` and SHALL NOT change the settings.

#### Scenario: Invalid participant limit is rejected

- **WHEN** the request contains `maxParticipants` outside `[2..100]`
- **THEN** the system returns `400` validation Problem Details and persists no
  part of the change

#### Scenario: Invalid admission policy is rejected

- **WHEN** the request contains an admission policy outside the allowed set
- **THEN** the system returns `400` validation Problem Details and leaves the
  settings unchanged

### Requirement: Settings change publishes a settings event

The system SHALL publish `meeting.settings.update` when the replacement changes
the persisted settings. The event SHALL include the meeting identity, host,
acting account, status, old settings snapshot, new settings snapshot, and update
timestamp. A replacement whose values equal the persisted settings SHALL publish
no event and SHALL return the unchanged settings snapshot.

#### Scenario: Effective settings change publishes the event

- **WHEN** an authorized replacement changes one or more settings values
- **THEN** exactly one `meeting.settings.update` event is persisted atomically
  with the settings change

#### Scenario: No-op settings replacement publishes no event

- **WHEN** an authorized replacement contains values equal to the persisted
  settings
- **THEN** the system returns the unchanged settings snapshot and persists no
  event

#### Scenario: Failed settings replacement publishes no event

- **WHEN** authorization, status, or validation checks reject the replacement
- **THEN** no settings change and no event is persisted

### Requirement: Real-time media permission enforcement on connected participants

The system SHALL, after a settings replacement that changes media permissions is
persisted, update the LiveKit publish permission of every connected (active,
not-left) participant that is not the host so that the allowed track sources
(microphone, camera, screen share and screen-share audio) match the new
settings. The host's permission SHALL NOT be reduced. Chat publishing permission
(`canPublishData`) of connected participants SHALL be preserved and SHALL NOT be
changed by this enforcement. Enforcement SHALL be best-effort: a failure to
update one or more participants SHALL NOT roll back the persisted settings and
SHALL be logged rather than surfaced as a request error.

#### Scenario: Disabling screen share revokes the source for participants

- **WHEN** the host replaces settings to set `allowScreenShare=false` on a
  `RUNNING` meeting with connected non-host participants
- **THEN** each connected non-host participant's LiveKit publish sources are
  updated to exclude screen share, and the host's permission is unchanged

#### Scenario: Enabling a source restores it for participants

- **WHEN** the host replaces settings to set a previously disabled media source
  to enabled on a `RUNNING` meeting with connected non-host participants
- **THEN** each connected non-host participant's LiveKit publish sources are
  updated to include that source

#### Scenario: Chat permission is preserved during enforcement

- **WHEN** media enforcement updates a connected participant's publish
  permission
- **THEN** that participant's existing chat publish permission is preserved

#### Scenario: LiveKit failure does not roll back persisted settings

- **WHEN** the settings are persisted but updating a connected participant's
  LiveKit permission fails
- **THEN** the persisted settings and the published event remain committed and
  the failure is logged rather than returned as a request error

#### Scenario: Host is skipped during enforcement

- **WHEN** media enforcement runs for a meeting whose connected set includes the
  host
- **THEN** no permission update is applied that reduces the host's publish
  permission
