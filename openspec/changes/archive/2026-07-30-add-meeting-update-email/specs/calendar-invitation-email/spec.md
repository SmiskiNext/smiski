## ADDED Requirements

### Requirement: Calendar update email on meeting time change

The notification service SHALL consume the `meet.meeting.info.updated` event
from Kafka, decoding it from its CloudEvents JSON representation, and SHALL send
each invitee carried by the event an email containing an iCalendar attachment
with `METHOD:REQUEST` ONLY WHEN the meeting's start time, end time, or time zone
differs between the event's old snapshot and new snapshot. The calendar object
SHALL be built from the new snapshot: the event's `calendarUid` as the `UID`,
the new snapshot's `calendarSequence` as the `SEQUENCE`, the new title as the
`SUMMARY`, the new start and end times as `DTSTART`/`DTEND`, the organizer email
and display name as the `ORGANIZER`, and one `ATTENDEE` entry per invitee
carrying the invitee email, display name, and participation status. The
attachment SHALL be sent with the `text/calendar` content type and the `REQUEST`
method parameter. When only non-time fields (title, description, or issue link)
change, the service SHALL send no email.

#### Scenario: Time change produces an updated invite per invitee

- **WHEN** the notification service consumes a `meet.meeting.info.updated` event
  whose new snapshot has a different start time, end time, or time zone than its
  old snapshot
- **THEN** it sends an email to each invitee's address with a `METHOD:REQUEST`
  iCalendar attachment whose `UID` equals the event's `calendarUid` and whose
  `SEQUENCE` equals the new snapshot's `calendarSequence`

#### Scenario: Non-time change produces no email

- **WHEN** the notification service consumes a `meet.meeting.info.updated` event
  whose old and new snapshots share the same start time, end time, and time zone
  but differ in title, description, or issue link
- **THEN** the service sends no email

#### Scenario: Event without invitees produces no email

- **WHEN** the notification service consumes a `meet.meeting.info.updated` event
  that carries a time change but no invitees
- **THEN** the service sends no email and continues processing subsequent events

#### Scenario: Malformed info-updated event does not break the consumer

- **WHEN** a consumed message cannot be decoded into a valid info-updated event
- **THEN** the failure is handled without terminating the consumer and
  subsequent well-formed events continue to be processed

### Requirement: Single-delivery consumer group for update emails

The meeting-update email consumer SHALL subscribe under a fixed, externally
configured consumer group so that, when the notification service runs as
multiple replicas, each `meet.meeting.info.updated` event is delivered to and
emailed by exactly one replica rather than broadcast to every replica.

#### Scenario: One replica sends each update email

- **WHEN** the notification service runs as multiple replicas and a single
  `meet.meeting.info.updated` event carrying a time change is published
- **THEN** exactly one replica consumes the event and sends the corresponding
  invitee emails, so no duplicate email is produced by the replica set
