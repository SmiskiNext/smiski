## Why

A running meeting can only end today when LiveKit emits the `room_finished`
webhook — there is no way for a host to end the meeting deliberately from the
application. Hosts need an explicit, immediate "End meeting" action that closes
the session for everyone rather than waiting for the media server to decide the
room is empty.

## What Changes

- Add a host-only endpoint `POST /api/1/meetings/{id}:end` that transitions a
  `RUNNING` meeting to `COMPLETED` synchronously within the request.
- Close every still-active participation log for the meeting at end time,
  reusing the same lifecycle the `room_finished` webhook already performs.
- Publish the existing `MeetingCompletedEvent` through the transactional outbox
  (no new event, proto, or mapper is introduced).
- Instruct LiveKit to delete the room as a best-effort side effect; a failure is
  logged but does NOT roll back the completed status.
- Keep the `room_finished` webhook path unchanged: because the meeting is
  already `COMPLETED`, the later webhook is a natural idempotent no-op through
  the existing `MeetingStatus` transition guard.
- No notification behavior is added; downstream notification handling is out of
  scope for this change.

## Capabilities

### New Capabilities

- `complete-meeting`: Host-initiated completion of a running meeting — the
  synchronous status transition, active-session closure, meeting-completed event
  emission, best-effort LiveKit room deletion, and idempotency against the
  subsequent `room_finished` webhook.

### Modified Capabilities

<!-- None. The livekit-webhook capability's existing idempotency guarantees
     already cover the redundant room_finished event; no requirement changes. -->

## Impact

- **meet service (new code)**: `EndMeetingCommand`, `EndMeetingUseCase`,
  `EndMeetingApplicationService`, `EndMeetingResult`, `EndedMeetingMapper`,
  `EndMeetingResponse`.
- **meet service (modified)**: `MeetingController` gains the `:end` action
  endpoint and wiring for the new use case.
- **API surface**: new action endpoint `POST /api/1/meetings/{id}:end`; the
  service `openapi.yaml` is regenerated.
- **Reused, unchanged**: `Meeting.complete()`, `MeetingCompletedEvent`,
  `MeetingCompletedEventProtoMapper`, `ParticipationLog.leave(...)`,
  `LiveKitPort.deleteRoom(...)`, and the LiveKit webhook processing path.
- **No database migration**, no notification-service change, no `MeetingStatus`
  change.
