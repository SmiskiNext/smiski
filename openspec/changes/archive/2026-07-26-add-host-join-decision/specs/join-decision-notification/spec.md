## ADDED Requirements

### Requirement: Requester subscribes to its join decision over SSE

The notification service SHALL expose
`GET /meetings/{id}/join-requests/{requestId}/events` returning a
`text/event-stream` connection through which the participant that submitted a
join request receives the host's accept/decline outcome in real time. The stream
SHALL be scoped by `requestId` so a subscriber receives only that request's
outcome. On subscription the service SHALL replay a decision that was already
recorded for that request before the subscriber connected. The connection SHALL
send an initial heartbeat, SHALL emit periodic heartbeat comments to keep an
idle stream open, and SHALL apply a configurable connection timeout after which
the stream completes and its emitter and heartbeat task are removed.

#### Scenario: Requester receives a live approval

- **WHEN** a requester holds an open stream for its request id and the host
  accepts that request
- **THEN** the requester receives a `join_request_approved` event carrying the
  LiveKit token and room name

#### Scenario: Requester receives a live denial

- **WHEN** a requester holds an open stream for its request id and the host
  declines that request
- **THEN** the requester receives a `join_request_denied` event and no token

#### Scenario: Late-subscribing requester replays a recorded decision

- **WHEN** a requester subscribes to its request stream after the host has
  already decided the request and the recorded outcome has not expired
- **THEN** the requester immediately receives the corresponding
  `join_request_approved` or `join_request_denied` event

#### Scenario: Idle stream is kept alive by heartbeat

- **WHEN** a requester stream has no decision event for longer than the
  heartbeat interval
- **THEN** the service sends a heartbeat comment so the connection is not closed
  as idle

#### Scenario: Stream completes on timeout and is cleaned up

- **WHEN** a requester stream reaches its configured timeout
- **THEN** the stream completes and its emitter and heartbeat task are removed
  so no resources leak

### Requirement: Consume join decision events and deliver to requesters

The notification service SHALL consume the `meet.join.approved` and
`meet.join.denied` events from Kafka, decoding each from its CloudEvents JSON
representation and reading the decision fields from the event data. On
consumption it SHALL push the corresponding decision notification to the
emitters held for the event's request id and SHALL record the decision in its
own store so it can be replayed to a requester subscribing later. Consumption
SHALL be scoped so that, when the service runs as multiple replicas, every
replica receives every decision event and delivers to the emitters it holds
locally. A message that cannot be decoded into a valid decision event SHALL be
handled without terminating the consumer.

#### Scenario: Consumed approval reaches locally held requester emitter

- **WHEN** a replica consumes a `meet.join.approved` event and holds an open
  emitter for that request id
- **THEN** that emitter receives a `join_request_approved` notification with the
  event's token and room name

#### Scenario: Consumed denial reaches locally held requester emitter

- **WHEN** a replica consumes a `meet.join.denied` event and holds an open
  emitter for that request id
- **THEN** that emitter receives a `join_request_denied` notification

#### Scenario: Every replica receives every decision event

- **WHEN** the notification service runs as multiple replicas and a single
  decision event is published
- **THEN** each replica receives the event and delivers it to any requester
  emitters it holds, so the requester is notified regardless of which replica
  holds its connection

#### Scenario: Decision retained for replay when no emitter is held

- **WHEN** a replica consumes a decision event but holds no emitter for that
  request id
- **THEN** the decision is stored so a requester subscribing afterward, on any
  replica, still receives it via replay

#### Scenario: Malformed event does not break the consumer

- **WHEN** a consumed message cannot be decoded into a valid decision event
- **THEN** the failure is handled without terminating the consumer, and
  subsequent well-formed events continue to be delivered

### Requirement: Requester decision replay store

The notification service SHALL maintain a store of join decisions keyed per
request id, with a time-to-live aligned to the requester notification window, so
that a requester subscribing after the decision arrives can be shown the
recorded outcome. The store SHALL be shared across replicas so replay works
regardless of which replica handled the original event or holds the requester
connection. An approved decision SHALL retain the LiveKit token and room name.

#### Scenario: Replay reflects an unexpired decision

- **WHEN** a requester subscribes and the store holds an unexpired decision for
  its request id
- **THEN** that decision is replayed to the requester

#### Scenario: Expired decision is not replayed

- **WHEN** a decision's TTL has elapsed before the requester subscribes
- **THEN** that decision is not replayed
