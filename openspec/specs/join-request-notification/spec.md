# join-request-notification Specification

## Purpose

TBD - created by archiving change add-meeting-join-endpoint. Update Purpose
after archive.

## Requirements

### Requirement: Host subscribes to meeting join events over SSE

The notification service SHALL expose `GET /meetings/{id}/events` returning a
`text/event-stream` connection through which a meeting host receives
join-related notifications in real time. On subscription the service SHALL
replay any currently pending join requests for that meeting so a host that
connects after requests have arrived still sees them. The connection SHALL send
an initial heartbeat, SHALL emit periodic heartbeat comments to keep
intermediaries from closing an idle stream, and SHALL apply a configurable
connection timeout after which the stream completes.

#### Scenario: Host receives a live join request

- **WHEN** a host holds an open SSE stream for a meeting and a join request for
  that meeting is consumed
- **THEN** the host receives a `join_request_created` event carrying the request
  id, account id, display name, and (when supplied) avatar url

#### Scenario: Late-subscribing host replays pending requests

- **WHEN** a host subscribes to a meeting's SSE stream after one or more join
  requests for that meeting have already arrived and are still pending
- **THEN** the host immediately receives a `join_request_created` event for each
  still-pending request

#### Scenario: Idle stream is kept alive by heartbeat

- **WHEN** an SSE stream has no join events for longer than the heartbeat
  interval
- **THEN** the service sends a heartbeat comment on the stream so the connection
  is not closed as idle

#### Scenario: Stream completes on timeout and is cleaned up

- **WHEN** an SSE stream reaches its configured timeout
- **THEN** the stream completes and its emitter and heartbeat task are removed
  so no resources leak

### Requirement: Consume join-created events and deliver to hosts

The notification service SHALL consume the `meet.join.created` event from Kafka,
decoding it from its CloudEvents JSON representation and reading the join fields
(including the requester's avatar url, treating an empty value as absent) from
the event data. On consumption it SHALL push a `join_request_created`
notification carrying the requester's avatar url to the emitters held for the
event's meeting and SHALL record the pending request, including its avatar url,
in its own store so it can be replayed to a host subscribing later. Consumption
SHALL be scoped so that, when the service runs as multiple replicas, every
replica receives every join event and delivers to the emitters it holds locally.

#### Scenario: Consumed event reaches locally held host emitters

- **WHEN** a replica consumes a `meet.join.created` event and holds an open host
  emitter for that meeting
- **THEN** that emitter receives a `join_request_created` notification with the
  event's request id, account id, display name, and (when supplied) avatar url

#### Scenario: Every replica receives every join event

- **WHEN** the notification service runs as multiple replicas and a single join
  event is published
- **THEN** each replica receives the event and delivers it to any host emitters
  it holds, so a host is notified regardless of which replica holds its
  connection

#### Scenario: Consumed event is retained for replay

- **WHEN** a replica consumes a join event but holds no emitter for that meeting
- **THEN** the pending request is stored so that a host subscribing afterward,
  on any replica, still receives it via replay

#### Scenario: Malformed event does not break the consumer

- **WHEN** a consumed message cannot be decoded into a valid join event
- **THEN** the failure is handled without terminating the consumer, and
  subsequent well-formed events continue to be delivered

### Requirement: Pending join request replay store

The notification service SHALL maintain a store of pending join requests, keyed
per meeting, with a time-to-live aligned to the join request lifetime, so that a
host subscribing after requests arrive can be shown the current pending set. The
store SHALL be shared across replicas so replay works regardless of which
replica handled the original event or holds the host connection.

#### Scenario: Replay reflects only unexpired requests

- **WHEN** a host subscribes and the store holds pending requests whose TTL has
  not elapsed
- **THEN** only those unexpired requests are replayed to the host

#### Scenario: Expired pending requests are not replayed

- **WHEN** a pending request's TTL has elapsed before a host subscribes
- **THEN** that request is not replayed
