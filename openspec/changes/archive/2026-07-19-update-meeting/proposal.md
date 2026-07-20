## Why

Meeting hosts currently cannot correct or adjust meeting metadata after
creation. This blocks ordinary changes to titles, descriptions, issue links,
settings, and scheduled details, while also leaving no explicit authorization or
lifecycle contract for updates.

## What Changes

- Add an authenticated meeting update capability for the meeting service.
- Allow only the meeting host to update a meeting.
- Allow `title`, `description`, `issueLink`, and `settings` updates while the
  meeting is `SCHEDULED` or `RUNNING`.
- Allow `zoneId` and `timeRange` updates only while the meeting is `SCHEDULED`.
- Reject every update to `COMPLETED` or `CANCELED` meetings.
- Suppress events when submitted values are equal to the persisted values.
- Publish `meeting.info.update` for changed meeting information and
  `meeting.settings.update` for changed settings.
- Add automated coverage for domain rules, application authorization, HTTP
  behavior, persistence, and event publication.

## Capabilities

### New Capabilities

- `update-meeting`: Update meeting information and settings with host
  authorization and lifecycle-aware field mutability.

### Modified Capabilities

- None.

## Impact

- Meeting domain aggregate, value objects, errors, and publishable domain
  events.
- Meeting application command, use case, service, result, and mapping layers.
- Meeting REST controller, request/response contracts, and generated OpenAPI
  documentation.
- Meeting persistence mapping for mutable zone and time-range fields.
- Transactional outbox event serialization and Kafka event metadata.
- Unit and integration test suites for the meet service.
