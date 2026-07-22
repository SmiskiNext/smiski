## Why

Hosts can create, update, and cancel meetings but have no way to remove a
meeting from their tenant's meeting list once it is no longer relevant. The
database and `Meeting` aggregate already carry soft-delete fields (`deleted_at`,
`deleted_by`, `purge_after`) and the meeting list already hides soft-deleted
rows, but no endpoint, use case, or domain behavior exists to trigger a
deletion.

## What Changes

- Add `DELETE /api/1/meetings/{id}` to soft-delete a single meeting owned by the
  acting host, returning `204 No Content`.
- Add `POST /api/1/meetings:batchDelete` to soft-delete multiple meetings in one
  atomic request (all-or-nothing), returning `204 No Content`.
- Restrict deletion to the meeting host only; non-hosts are rejected with an
  authorization error.
- Reject deletion of a meeting whose status is `RUNNING`; the meeting must be
  ended first. This introduces a new domain error.
- Treat an already soft-deleted meeting as not found (`404`), consistent with
  the list endpoint hiding deleted meetings.
- Mark meetings soft-deleted by setting `deleted_at` and `deleted_by`;
  `purge_after` is left null (a future purge job will compute retention).
- Publish a new `MeetingDeletedEvent` (via the transactional outbox → Kafka) for
  each successfully deleted meeting.

## Capabilities

### New Capabilities

- `delete-meeting`: Host-only soft deletion of meetings via a single-resource
  DELETE endpoint and an atomic batch action endpoint, including running-meeting
  protection, not-found handling for already-deleted meetings, and deletion
  event publication.

### Modified Capabilities

<!-- None: list-tenant-meetings already excludes soft-deleted meetings; no
     existing spec-level behavior changes. -->

## Impact

- **API**: New `DELETE /api/1/meetings/{id}` and
  `POST /api/1/meetings:batchDelete` endpoints on the `meet` service;
  regenerated `services/meet/openapi.yaml`.
- **Domain**: New `Meeting.delete(...)` behavior, new `MeetingDeletedEvent`, new
  `MeetingError` variant and `MeetingErrorCode` for running-meeting deletion.
- **Application**: New delete and batch-delete use cases, commands, and
  services.
- **Infrastructure**: New repository lookup that excludes soft-deleted meetings
  under a pessimistic lock.
- **i18n**: New localized error text in `meet.properties` and
  `meet_vi.properties`.
- **Events**: New `meet.meeting.deleted` Kafka topic consumers may subscribe to.
- No database migration required; columns and indexes already exist.
