# calendar-invitation-email Specification

## Purpose

TBD - created by archiving change add-rsvp-ics-email. Update Purpose after
archive.

## Requirements

### Requirement: Calendar invitation email on invitation creation

The notification service SHALL consume the `meet.meeting.invitations.created`
event from Kafka, decoding it from its CloudEvents JSON representation, and
SHALL send each invitee an email containing an iCalendar attachment with
`METHOD:REQUEST`. The calendar object SHALL use the event's `calendarUid` as the
`UID`, the event's `calendarSequence` as the `SEQUENCE`, the meeting title as
the `SUMMARY`, the start and end times interpreted in the event's timezone as
`DTSTART`/`DTEND`, the organizer email and display name as the `ORGANIZER`, and
one `ATTENDEE` entry per invitee carrying the invitee email, display name, and
participation status. The attachment SHALL be sent with the `text/calendar`
content type and the `REQUEST` method parameter.

#### Scenario: Invitation event produces an invite email per invitee

- **WHEN** the notification service consumes a
  `meet.meeting.invitations.created` event carrying one or more invitees
- **THEN** it sends an email to each invitee's address with a `METHOD:REQUEST`
  iCalendar attachment whose `UID` equals the event's `calendarUid` and whose
  `SEQUENCE` equals the event's `calendarSequence`

#### Scenario: Malformed invitation event does not break the consumer

- **WHEN** a consumed message cannot be decoded into a valid invitations-created
  event
- **THEN** the failure is handled without terminating the consumer and
  subsequent well-formed events continue to be processed

### Requirement: Calendar reply email on invitee response

The notification service SHALL consume the invitee response events
(`meet.invitee.accepted`, `meet.invitee.declined`, `meet.invitee.tentative`)
from Kafka and SHALL send the meeting organizer an email containing an iCalendar
attachment with `METHOD:REPLY`. The reply calendar object SHALL use the same
`UID` and `SEQUENCE` carried in the response event, SHALL set the `ORGANIZER` to
the meeting organizer, and SHALL contain a single `ATTENDEE` entry for the
responding invitee whose `PARTSTAT` maps the response to the iCalendar
participation status: accepted to `ACCEPTED`, declined to `DECLINED`, and
tentative to `TENTATIVE`. The attachment SHALL be sent with the `text/calendar`
content type and the `REPLY` method parameter.

#### Scenario: Accepted response produces an organizer reply email

- **WHEN** the notification service consumes a `meet.invitee.accepted` event
- **THEN** it sends the organizer an email with a `METHOD:REPLY` iCalendar
  attachment whose single `ATTENDEE` carries `PARTSTAT=ACCEPTED` and the
  responding invitee's email

#### Scenario: Tentative response maps to tentative participation status

- **WHEN** the notification service consumes a `meet.invitee.tentative` event
- **THEN** the reply attachment's `ATTENDEE` carries `PARTSTAT=TENTATIVE`

#### Scenario: Declined response maps to declined participation status

- **WHEN** the notification service consumes a `meet.invitee.declined` event
- **THEN** the reply attachment's `ATTENDEE` carries `PARTSTAT=DECLINED`

### Requirement: Single-delivery consumer groups

The calendar email consumers SHALL each subscribe under a fixed, externally
configured consumer group so that, when the notification service runs as
multiple replicas, each event is delivered to and emailed by exactly one replica
rather than broadcast to every replica.

#### Scenario: One replica sends each email

- **WHEN** the notification service runs as multiple replicas and a single
  invitation or response event is published
- **THEN** exactly one replica consumes the event and sends the corresponding
  email, so no duplicate email is produced by the replica set

### Requirement: Email delivery retry and dead-letter handling

The notification service SHALL send calendar emails through a configured email
provider. When a send fails, the consumer SHALL retry the delivery a bounded
number of times, and after the retries are exhausted the message SHALL be routed
to a dead-letter topic rather than blocking further processing of the partition.

#### Scenario: Transient send failure is retried

- **WHEN** an email send fails transiently
- **THEN** the consumer retries the delivery up to the configured retry limit
  before considering it failed

#### Scenario: Exhausted retries route to the dead-letter topic

- **WHEN** an email send continues to fail after the retry limit is reached
- **THEN** the message is routed to the dead-letter topic and the consumer
  continues processing subsequent events

### Requirement: Fail-fast email provider configuration

The notification service SHALL bind the email provider API key and sender
address from externalized configuration and SHALL fail application startup when
the API key is absent or blank, so that a misconfigured deployment cannot
silently drop calendar mail.

#### Scenario: Missing API key fails startup

- **WHEN** the notification service starts without a configured email provider
  API key
- **THEN** application startup fails with a configuration error rather than
  starting in a state where emails are silently dropped

#### Scenario: Configured provider sends mail

- **WHEN** the API key and sender address are configured and an email is
  produced
- **THEN** the service sends the email through the configured provider from the
  configured sender address
