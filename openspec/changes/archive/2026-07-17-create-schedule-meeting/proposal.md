## Why

The meet service can only create INSTANT meetings that auto-start and require a
LiveKit host token. Users need to book meetings ahead of time — planning a Jira
issue discussion for a future slot — without joining a live room at creation.
The domain already models scheduled meetings (`Meeting.schedule()`,
`MeetingType.SCHEDULED`, `start_time`/`end_time` columns), but no application,
presentation, or API layer wires it up, so the capability is unreachable.

## What Changes

- Add `POST /api/1/meetings:schedule` to create a `SCHEDULED` meeting from a
  title, description, Jira issue link, room settings, a time range
  (`startTime` + `endTime`), and an optional invitees list.
- Resolve the host `accountId` from the `X-Account-Id` header (like instant);
  the request body carries **no** host object — display name, device id, and
  avatar url are not needed because no LiveKit token is issued at scheduling.
- The response returns only the meeting snapshot plus the scheduled
  `startTime`/`endTime`; it does **NOT** return a LiveKit token and the meeting
  stays in `SCHEDULED` status (it is not started).
- Validate the time range: `startTime` must be strictly before `endTime`
  (existing `MeetingTimeRange` invariant) and `startTime` must not be in the
  past, allowing a small clock-skew tolerance so valid requests are not rejected
  by network/processing latency. No maximum-duration cap is enforced because
  scheduled start/end are calendar metadata, not runtime enforcement (verified
  against Zoom, Google Meet, and Microsoft Teams behavior).
- Register invitees with single-use invite tokens (hash stored, raw token only
  in the event) and publish a meeting-invitations-sent event, reusing the
  instant-meeting invite mechanism.
- Publish a meeting-created event carrying the full snapshot including both
  `startTime` and `endTime`.
- **BREAKING (internal only)**: fix a latent bug in `Meeting.schedule()` where
  the created event carried `null` for `endTime` instead of the scheduled end.

## Capabilities

### New Capabilities

- `create-schedule-meeting`: Creating a future-dated `SCHEDULED` meeting via
  `POST /api/1/meetings:schedule` — header-resolved host, time-range validation
  with clock-skew tolerance, invitee registration with invite tokens, event
  publication, and a token-free meeting-snapshot response.

### Modified Capabilities

<!-- No requirement-level changes to existing capabilities. The endTime event
     bug fix in Meeting.schedule() does not alter any published create-instant-meeting
     requirement. -->

## Impact

- **Service**: `services/meet`
- **Domain**: `Meeting.schedule()` (validation + event endTime fix), new
  `MeetingError.StartTimeInPast`, new `MeetingErrorCode.MEETING_START_IN_PAST`
  (category `VALIDATION` → HTTP 400), clock-skew tolerance constant.
- **Application**: new `ScheduleMeetingCommand`, `ScheduleMeetingResult`,
  `ScheduleMeetingUseCase`, `ScheduleMeetingApplicationService` (reuses
  `ShortCodeAllocator`, `MeetingRepository`, `MeetingInviteeRepository`,
  `InviteTokenGenerator`, `EventPublisher`; no `LiveKitPort`).
- **Presentation**: new `ScheduleMeetingRequest`, `ScheduleMeetingResponse`, and
  a new endpoint on `MeetingController`.
- **Resources**: new `error.meeting-start-in-past.*` keys in `meet.properties`
  and `meet_vi.properties`.
- **API spec**: regenerated `services/meet/openapi.yaml`.
- **Events**: `meeting.created.v1` for scheduled meetings now carries `endTime`;
  `meeting.invitations-sent.v1` emitted when invitees are present.
- **Out of scope**: instant-meeting flow, starting/canceling/updating a
  scheduled meeting, max/min duration limits, schedule-ahead upper bound.
