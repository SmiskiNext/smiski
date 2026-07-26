## ADDED Requirements

### Requirement: Immediate post-commit relay of SSE-triggering events

The meet service SHALL relay SSE-triggering domain events to the message
transport near-immediately after their originating database transaction commits,
rather than waiting for the scheduled outbox poll. An event that requires
real-time host delivery SHALL be identified by a meet-internal marker
(`SseTriggeringEvent`); a publishable event that does not carry the marker SHALL
NOT trigger an immediate relay and SHALL continue to be delivered by the
scheduled poll only. When a transaction that registered at least one marked
event commits successfully, the meet service SHALL invoke its own outbox relay
so the marked event's already-persisted `outbox_event` row is published without
waiting for the poll interval. The trigger SHALL rely on the existing outbox
write path: the `outbox_event` row SHALL still be enqueued within the
transaction (pre-commit) exactly as before, and the immediate relay SHALL run
only after that transaction commits.

#### Scenario: Marked event published without waiting for the poll

- **WHEN** a pending join request is created under `MANUAL_APPROVAL` and its
  transaction commits, registering a marked `JoinRequestCreated` event
- **THEN** the corresponding `meet.join.created` CloudEvent is published to
  Kafka within a short bound well under the scheduled poll interval, without
  waiting for the next scheduled poll

#### Scenario: Unmarked event is not relayed immediately

- **WHEN** a publishable event that does not carry the SSE marker is enqueued
  and its transaction commits
- **THEN** no immediate relay is triggered by that event and the row is
  published by the scheduled poll as before

#### Scenario: Immediate relay runs only after commit

- **WHEN** a transaction registers a marked event but is rolled back before
  commit
- **THEN** no `outbox_event` row exists for that event and no immediate relay is
  triggered

### Requirement: Non-blocking trigger on the request path

The immediate relay triggered by a committed SSE-triggering event SHALL execute
asynchronously so the original request thread is not blocked awaiting
message-transport acknowledgment. The response to the request that created the
event (for join, `POST /meetings/{id}:join`) SHALL be returned to the caller
without waiting for the relay to complete its transport send.

#### Scenario: Join response is not blocked by transport publish

- **WHEN** a caller submits a join request that creates a marked pending join
  request
- **THEN** the HTTP response is returned once the transaction commits, without
  blocking on the Kafka publish of the join-created event

### Requirement: Immediate relay preserves delivery guarantees

The immediate relay SHALL reuse the same service-local outbox relay used by the
scheduled poll, so at-least-once delivery, aggregate-id keying, published-row
marking, and failure/retry recording are preserved and identical to the
scheduled path. The immediate relay and the scheduled poll SHALL be safe to run
concurrently: rows SHALL be claimed with `FOR UPDATE SKIP LOCKED` so a row is
published by at most one of them, and a row SHALL never be double-published. If
the immediate relay fails for any reason (including asynchronous dispatch never
running or erroring before publishing), the row SHALL remain unpublished
(`published_at` null) and SHALL remain eligible for the scheduled poll to
publish it later.

#### Scenario: Immediate relay and poll do not double-publish

- **WHEN** the immediate relay and the scheduled poll run against the same
  unpublished marked row concurrently
- **THEN** exactly one of them claims and publishes the row, the other skips it,
  and the row is published exactly once

#### Scenario: Failed immediate relay falls back to the poll

- **WHEN** the immediate relay is triggered but fails to publish the marked row
  (transport error or dispatch failure)
- **THEN** the row's `published_at` remains null, its failure handling matches
  the scheduled path, and the next scheduled poll publishes the row

#### Scenario: Marked row is published exactly once across both paths

- **WHEN** a marked row is successfully published by the immediate relay and a
  later scheduled poll runs
- **THEN** the poll does not reselect or resend the row because its
  `published_at` is already set
