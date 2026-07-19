## Why

Meeting scheduling in the Jira Forge integration is worthless across regions if
invitees cannot see the meeting time in a well-defined zone. The backend
currently stores only UTC `Instant` start/end and carries no time-zone context,
so the notification service has no authoritative zone to render invitation
emails against. Capturing the host's time zone at creation time gives every
downstream consumer a single, correct reference zone.

## What Changes

- Every meeting (both `SCHEDULED` and `INSTANT`) SHALL capture a required host
  time zone as an IANA zone id (e.g. `Asia/Ho_Chi_Minh`) at creation time.
- The scheduled and instant creation endpoints SHALL accept a required top-level
  `zoneId` and echo it in the meeting snapshot response.
- `zoneId` SHALL be validated as a real IANA zone id and persisted **NOT NULL**
  on the `meetings` table; UTC `startTime`/`endTime` remain unchanged as the
  authoritative moment.
- The `meeting-created` snapshot and `meeting-invitations-sent` events SHALL
  carry `zoneId`, and the `meeting-invitations-sent` event SHALL additionally
  carry `endTime` so an invitation can render the full start–end range.
- The shared proto contracts `meeting_snapshot` and `meeting_invitations_sent`
  SHALL gain the new fields (new field numbers, backward-compatible) so the
  notification service can consume them.

## Capabilities

### New Capabilities

- _None._ This change extends existing meeting-creation behaviour only.

### Modified Capabilities

- `create-schedule-meeting`: creation request/response gains a required
  `zoneId`; a new host-time-zone validation-and-persistence requirement; the
  created and invitations events carry `zoneId`, and the invitations event
  carries `endTime`.
- `create-instant-meeting`: creation request/response gains a required `zoneId`;
  a new host-time-zone validation-and-persistence requirement; the created,
  started, and invitations events carry `zoneId` (times null for instant).

## Impact

- **API (breaking)**: `POST /api/1/meetings:schedule` and
  `POST /api/1/meetings:instant` now require `zoneId`; existing clients omitting
  it receive `400 VALIDATION_ERROR`. OpenAPI specs regenerate.
- **Domain**: new `MeetingTimeZone` value object; `Meeting` aggregate,
  `MeetingCreatedEvent`, and `MeetingInvitationsCreatedEvent` gain zone/time
  fields.
- **Persistence**: new `V2__add_meetings_zone_id.sql` migration adds
  `zone_id NOT NULL`; `MeetingJpaEntity` and `MeetingPersistenceMapper` updated.
- **Messaging (cross-service contract)**: `meeting_snapshot.proto` and
  `meeting_invitations_created.proto` gain fields; proto mappers updated. The
  `notification` service can then read the zone/time (email rendering itself is
  out of scope here).
- **Tests**: meet unit/integration suites and request fixtures updated to supply
  `zoneId`; OpenAPI regeneration.
