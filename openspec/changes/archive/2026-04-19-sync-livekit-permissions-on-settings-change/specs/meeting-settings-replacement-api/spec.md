# MODIFIED Requirements

## Requirement: Meeting settings PUT preserves side effects and event integrity

The system SHALL preserve the existing side effects triggered by successful
meeting settings updates and SHALL publish enough event context to support
downstream runtime permission synchronization for LIVE meetings.

### Scenario: Meeting settings update event still published

- **WHEN** `PUT /api/v1/meetings/{id}/settings` succeeds
- **THEN** the system SHALL publish `MeetingSettingsUpdatedEvent` with the same
  event type and topic used by the existing meeting settings update flow
- **THEN** the event payload SHALL include both `oldSettings` and `newSettings`
  snapshots for the updated meeting settings

### Scenario: Live meeting access opening auto-approves pending requests

- **WHEN** `PUT /api/v1/meetings/{id}/settings` makes a LIVE meeting more
  permissive by changing admission policy to `ALLOW_ALL` or changing
  `allowGuest` from `false` to `true`
- **THEN** the system SHALL auto-approve pending join requests using the same
  approval helper used by the existing meeting settings update flow

### Scenario: Live meeting permission changes trigger asynchronous participant sync

- **WHEN** `PUT /api/v1/meetings/{id}/settings` succeeds for a LIVE meeting and
  one or more of `allowMicrophone`, `allowVideo`, `allowScreenShare`, or
  `chatEnabled` changed
- **THEN** the published settings-updated event SHALL enable asynchronous
  reconciliation of connected participant permissions without blocking the API
  response
