## Why

Scheduled meetings need a cancellation mechanism distinct from deletion.
Currently, hosts can only delete meetings (soft-delete that hides the record),
but there's no way to cancel a scheduled meeting and notify invitees.
Additionally, meetings that are scheduled but never started (no-shows) should be
automatically marked as canceled to keep data clean and accurate.

## What Changes

- Add `POST /meetings/{id}:cancel` endpoint for hosts to manually cancel
  SCHEDULED meetings
- Implement background job to auto-cancel no-show meetings (SCHEDULED past
  end_time with no participants)
- Publish `MeetingCanceledEvent` with invitee list for future notification
  integration
- Add protobuf message definition `MeetingCanceled` and mapper to support event
  publishing
- Wire existing domain `cancel()` method through
  application/presentation/infrastructure layers

## Capabilities

### New Capabilities

- `cancel-meeting`: Manual and automatic cancellation of scheduled meetings with
  reason tracking (HOST_CANCELED, NO_SHOW) and invitee notification support

### Modified Capabilities

<!-- No existing capabilities are being modified at the requirement level -->

## Impact

**Affected Services:**

- `services/meet` — main implementation (application layer, presentation layer,
  infrastructure)
- `services/proto` — new protobuf message definition for `MeetingCanceled` event

**Code Areas:**

- Application layer: new use case, service, command, result, mapper
- Presentation layer: new controller endpoint with OpenAPI documentation
- Infrastructure: proto mapper (mandatory for outbox publishing), scheduled job
  for no-shows
- Tests: domain, application service, controller integration, job tests

**Database:**

- No migration needed — `cancel_reason` column and CANCELED status already exist
  in baseline schema

**APIs:**

- New endpoint: `POST /api/1/meetings/{id}:cancel` (host-only, SCHEDULED
  meetings only)

**External Dependencies:**

- OpenSpec change artifacts must be created before implementation begins
