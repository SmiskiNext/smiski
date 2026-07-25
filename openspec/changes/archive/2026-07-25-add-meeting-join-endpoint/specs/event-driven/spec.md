## ADDED Requirements

### Requirement: Meeting join-created event contract

The meet service SHALL publish a join-created event when a pending join request
is created under `MANUAL_APPROVAL` admission. The event SHALL be defined by a
shared Protocol Buffers message `JoinCreated` in package
`io.github.smiskinext.event.meet.v1` (file
`io/github/smiskinext/event/meet/v1/join_created.proto`), carrying the meeting
id, join request id, tenant id, account id, display name, device id, occurrence
time, and avatar url. Fields with no value at event time SHALL use their proto3
default (empty string); a requester with no avatar url SHALL be represented by
an empty `avatar_url`. The event SHALL be published to Kafka topic
`meet.join.created` with CloudEvent type
`io.github.smiskinext.meet.join.created.v1`, enqueued through the existing
transactional outbox and rendered as a CloudEvents 1.0 structured-JSON payload
whose `data` is the proto message as proto-JSON. Publishing SHALL go through a
registered per-event proto mapper; an unmapped event SHALL fail enqueue without
a partial write. The proto SHALL satisfy the module's Buf `STANDARD` lint rules.

#### Scenario: Join-created event published on pending request creation

- **WHEN** a pending join request is created under `MANUAL_APPROVAL`
- **THEN** exactly one `meet.join.created` outbox row is enqueued in the same
  transaction, keyed by the meeting id, with CloudEvent type
  `io.github.smiskinext.meet.join.created.v1`

#### Scenario: Event data carries the join fields

- **WHEN** the join-created CloudEvent is built
- **THEN** its `data` is the `JoinCreated` proto rendered as JSON, carrying the
  meeting id, join request id, account id, display name, device id, and avatar
  url, with `datacontenttype` `application/json`

#### Scenario: Missing proto mapper fails without partial write

- **WHEN** the join-created event has no registered proto mapper
- **THEN** enqueue fails and no `outbox_event` row is written for that event

#### Scenario: Proto passes standard lint

- **WHEN** the proto module is linted
- **THEN** `join_created.proto` passes the Buf `STANDARD` ruleset
