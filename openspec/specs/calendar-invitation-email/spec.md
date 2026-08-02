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

The email subject and body SHALL conform to the `email-content-templates` spec:
subject resolved from message bundle, body in both HTML and plain-text formats,
including meeting title, formatted time, organiser, short code, meeting ID,
invitee list, and an optional Jira issue deep-link composed from the tenant's
`site_url` and the event's `issue_key`.

#### Scenario: Invitation event produces an invite email per invitee

- **WHEN** the notification service consumes a
  `meet.meeting.invitations.created` event carrying one or more invitees
- **THEN** it sends an email to each invitee's address with a `METHOD:REQUEST`
  iCalendar attachment whose `UID` equals the event's `calendarUid` and whose
  `SEQUENCE` equals the event's `calendarSequence`

#### Scenario: Invitation email contains structured body and Jira link

- **WHEN** the tenant's `site_url` is known and the event carries a non-blank
  `issue_key`
- **THEN** the invitation email body includes meeting title, formatted time,
  organiser, short code, meeting ID, invitee list, and a deep-link to
  `{siteUrl}/browse/{issueKey}`

#### Scenario: Invitation email omits Jira link when site_url is unknown

- **WHEN** the tenant has no `site_url` in the notification service's tenant
  projection or the event carries no `issue_key`
- **THEN** the invitation email body is sent without a Jira deep-link; all other
  fields are still included

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

The email subject and body SHALL conform to the `email-content-templates` spec:
subject identifies the invitee and meeting title; body in HTML and plain-text
includes invitee name, response status, meeting details, and an optional Jira
deep-link.

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
