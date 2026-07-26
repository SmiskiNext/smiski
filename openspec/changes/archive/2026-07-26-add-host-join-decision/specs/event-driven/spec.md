## ADDED Requirements

### Requirement: Meeting join-decision event contract

The meet service SHALL publish a join-approved event when a host accepts a
pending join request and a join-denied event when a host declines one. The
approved event SHALL be defined by a shared Protocol Buffers message
`JoinApproved` in package `io.github.smiskinext.event.meet.v1` (file
`io/github/smiskinext/event/meet/v1/join_approved.proto`), carrying the meeting
id, join request id, tenant id, account id, device id, LiveKit token, room name,
approving account id, and occurrence time. The denied event SHALL be defined by
`JoinDenied` (file `io/github/smiskinext/event/meet/v1/join_denied.proto`),
carrying the meeting id, join request id, tenant id, account id, device id,
denying account id, and occurrence time. Fields with no value at event time
SHALL use their proto3 default (empty string).

The approved event SHALL be published to Kafka topic `meet.join.approved` with
CloudEvent type `io.github.smiskinext.meet.join.approved.v1`, and the denied
event to topic `meet.join.denied` with CloudEvent type
`io.github.smiskinext.meet.join.denied.v1`. Both SHALL be enqueued through the
existing transactional outbox in the same transaction as the join request state
change, rendered as a CloudEvents 1.0 structured-JSON payload whose `data` is
the proto message as proto-JSON, and published through a registered per-event
proto mapper; an unmapped event SHALL fail enqueue without a partial write. Both
events SHALL be marked as SSE-triggering so their outbox rows are relayed
immediately after commit. Both protos SHALL satisfy the module's Buf `STANDARD`
lint rules.

#### Scenario: Join-approved event published on accept

- **WHEN** a host accepts a pending join request
- **THEN** exactly one `meet.join.approved` outbox row is enqueued in the same
  transaction, keyed by the meeting id, with CloudEvent type
  `io.github.smiskinext.meet.join.approved.v1`, whose `data` carries the join
  request id, account id, device id, LiveKit token, and room name

#### Scenario: Join-denied event published on decline

- **WHEN** a host declines a pending join request
- **THEN** exactly one `meet.join.denied` outbox row is enqueued in the same
  transaction, keyed by the meeting id, with CloudEvent type
  `io.github.smiskinext.meet.join.denied.v1`, whose `data` carries the join
  request id and account id

#### Scenario: Missing proto mapper fails without partial write

- **WHEN** a join-approved or join-denied event has no registered proto mapper
- **THEN** enqueue fails and no `outbox_event` row is written for that event

#### Scenario: Decision events relayed immediately after commit

- **WHEN** a join-approved or join-denied outbox row is committed
- **THEN** the post-commit relay publishes it without waiting for the scheduled
  outbox poll, because the event is marked SSE-triggering

#### Scenario: Protos pass standard lint

- **WHEN** the proto module is linted
- **THEN** `join_approved.proto` and `join_denied.proto` pass the Buf `STANDARD`
  ruleset
