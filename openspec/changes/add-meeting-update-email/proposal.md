## Why

When a meeting host changes the meeting's schedule (start/end time or time
zone), invitees receive no updated calendar invite, so their calendar clients
keep the stale time. The meet service already publishes an info-update event and
bumps the iCalendar `SEQUENCE`, but the event carries no invitee list and no
consumer emails the reschedule, so the notification path is a dead end.

## What Changes

- Enrich the meeting info-update event so it carries the meeting's current
  invitee list (account id, email, display name, participation status), matching
  how the invitations-created and cancellation events already embed invitees.
- **BREAKING** Rename the info-update event topic from `meeting.info.update` to
  `meet.meeting.info.updated` and its CloudEvent type from `meeting.info.update`
  to `io.github.smiskinext.meet.meeting.info.updated.v1`, aligning it with every
  other meet event's `meet.*` / versioned-type convention.
- Load the meeting's invitees during the update use case and pass them into the
  domain event.
- Add a notification consumer that subscribes to `meet.meeting.info.updated`,
  sends an updated `METHOD:REQUEST` calendar invite to every invitee ONLY when
  the start time, end time, or time zone changed, and skips events where only
  title, description, or issue link changed.

## Capabilities

### New Capabilities

_None._

### Modified Capabilities

- `update-meeting`: The info-update event is renamed to the
  `meet.meeting.info.updated` topic with a versioned CloudEvent type, and now
  carries the meeting's current invitee list.
- `calendar-invitation-email`: A new requirement covers emailing invitees an
  updated `METHOD:REQUEST` calendar invite when a meeting's scheduled time
  changes, gated on a real start/end/time-zone change.

## Impact

- Proto: `services/proto/.../meeting_snapshot.proto` (`MeetingInfoUpdated`
  message gains a repeated invitee field).
- meet service: `MeetingInfoUpdatedEvent`, `MeetingInfoUpdatedEventProtoMapper`,
  `Meeting.updateInfo(...)`, `UpdateMeetingApplicationService`, and the update
  controller integration test that asserts the old topic string.
- notification service: new `MeetingInfoUpdatedEmailConsumer`,
  `EmailConsumerProperties`, and `application.yaml` consumer-group entry; reuses
  the existing `IcsGenerator`, `InvitationCalendar`, `EmailSender`, and the
  shared `emailKafkaListenerContainerFactory` (dead-letter + retry).
- Kafka: new topic `meet.meeting.info.updated` (and its `.dlt` dead-letter
  topic); the legacy `meeting.info.update` topic is retired.
