## ADDED Requirements

### Requirement: Meeting invitee-response event contract

The meet service SHALL publish an invitee-response event when an invitee
accepts, declines, or tentatively responds to their invitation. The events SHALL
be defined by shared Protocol Buffers messages in package
`io.github.smiskinext.event.meet.v1` (file
`io/github/smiskinext/event/meet/v1/invitee_response.proto`): `InviteeAccepted`,
`InviteeDeclined`, and `InviteeTentative`. Each message SHALL carry the event
id, tenant id, meeting id, inviter id, invitee id, invitee email, resolved
status, and response timestamp, together with the meeting context required to
build an iCalendar reply without a follow-up lookup: meeting title, start time,
end time, timezone, organizer email, organizer display name, invitee display
name, calendar UID, and calendar sequence. Fields with no value at event time
SHALL use their proto3 default (empty string). New fields SHALL be added with
new field numbers so the change is backward compatible for existing consumers.

The accepted event SHALL be published to Kafka topic `meet.invitee.accepted`
with CloudEvent type `io.github.smiskinext.meet.invitee.accepted.v1`, the
declined event to `meet.invitee.declined` with type
`io.github.smiskinext.meet.invitee.declined.v1`, and the tentative event to
`meet.invitee.tentative` with type
`io.github.smiskinext.meet.invitee.tentative.v1`. Each SHALL be enqueued through
the existing transactional outbox in the same transaction as the invitee state
change, rendered as a CloudEvents 1.0 structured-JSON payload whose `data` is
the proto message as proto-JSON, and published through a registered per-event
proto mapper; an unmapped event SHALL fail enqueue without a partial write. The
protos SHALL satisfy the module's Buf `STANDARD` lint rules.

#### Scenario: Accepted event published on accept

- **WHEN** an invitee accepts their invitation and the transaction commits
- **THEN** exactly one `meet.invitee.accepted` outbox row is enqueued in the
  same transaction, keyed by the invitee aggregate id, with CloudEvent type
  `io.github.smiskinext.meet.invitee.accepted.v1`

#### Scenario: Tentative event published on tentative response

- **WHEN** an invitee marks their invitation tentative and the transaction
  commits
- **THEN** exactly one `meet.invitee.tentative` outbox row is enqueued in the
  same transaction with CloudEvent type
  `io.github.smiskinext.meet.invitee.tentative.v1`

#### Scenario: Response event data carries the meeting context

- **WHEN** an invitee-response CloudEvent is built
- **THEN** its `data` carries the invitee email, status, and response timestamp
  together with the meeting title, start time, end time, timezone, organizer
  email and display name, calendar UID, and calendar sequence, with
  `datacontenttype` `application/json`

#### Scenario: Missing proto mapper fails without partial write

- **WHEN** an invitee-response event has no registered proto mapper
- **THEN** enqueue fails and no `outbox_event` row is written for that event

#### Scenario: Added fields keep existing consumers working

- **WHEN** a consumer built against the previous accepted/declined schema reads
  an event produced with the extended schema
- **THEN** the consumer deserializes successfully because the added
  meeting-context fields use new field numbers

#### Scenario: Proto passes standard lint

- **WHEN** the proto module is linted
- **THEN** `invitee_response.proto` passes the Buf `STANDARD` ruleset
