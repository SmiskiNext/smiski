## Why

Under `MANUAL_APPROVAL` admission a participant's join creates a `PENDING`
request and the host is notified over SSE, but there is no way for the host to
act on it: the meet service exposes no accept/decline endpoint, the
`JoinRequestApprovedEvent`/`JoinRequestDeniedEvent` are never published, the
`JoinRequestResultStore` port has no adapter, and the notification service
cannot deliver an outcome back to the waiting participant (its
`meet.join.created` consumer is also missing on this branch). This change
completes the manual-approval loop end to end.

## What Changes

- Add host decision endpoints on the meet service:
  `POST /meetings/{id}/join-requests:accept` and
  `POST /meetings/{id}/join-requests:decline`, each taking a list body
  `{ "requestIds": [...] }` (a single id = one decision; all ids = decide the
  whole queue). Only the meeting host may call them.
- Accept processing is **best-effort per item**: for each request the meet
  service enforces `maxParticipants` capacity under a meeting-row lock,
  generates a `PARTICIPANT` LiveKit token, transitions the request to
  `APPROVED`, persists the terminal outcome, removes it from the pending queue,
  and publishes an approved event. Decline transitions to `DENIED`, persists the
  outcome, removes it, and publishes a denied event. The response returns a
  per-item result list.
- Add the missing `JoinRequestResultStore` Redis adapter so terminal outcomes
  (including the LiveKit token for approvals) are retained with a TTL for
  replay.
- **BREAKING** (internal event contract only, no live consumer yet): rename the
  approved/denied event topics and CloudEvent types from
  `meet.join-request.approved`/`.denied` to `meet.join.approved`/
  `meet.join.denied` to match the `meet.join.created` style, mark them as
  SSE-triggering, and add `join_approved`/`join_denied` protos plus their outbox
  proto mappers.
- Restore the notification service's missing `meet.join.created` consumer (Kafka
  → host SSE + pending store), then add a consumer for the approved/denied
  events and a new requester-facing SSE stream
  `GET /meetings/{id}/join-requests/{requestId}/events` keyed by `requestId`,
  with a result-replay store so a late-connecting participant still receives
  their outcome.

## Capabilities

### New Capabilities

- `host-join-decision`: The meet service host accept/decline endpoints,
  authorization, best-effort per-item processing, capacity enforcement on
  accept, LiveKit token issuance, terminal-outcome persistence, and queue
  removal.
- `join-decision-notification`: The requester-facing SSE stream that delivers an
  accept/decline outcome (token + room for approved, denied signal otherwise) to
  the waiting participant, including late-subscribe replay.

### Modified Capabilities

- `event-driven`: Add the `meet.join.approved` and `meet.join.denied` event
  contracts (protos, topics, CloudEvent types, proto mappers) and align the
  previously-defined approved/denied event names.

Note: the existing `join-request-notification` capability's `meet.join.created`
consumer is absent in the code on this branch but is already required by its
spec; restoring it is an implementation task (see tasks.md), not a spec change.

## Impact

- **meet service**: new presentation endpoints + request/response DTOs; new
  application use cases, services, commands, results, mappers; domain event
  renames + `SseTriggeringEvent`; new `JoinRequestResultStoreRedisAdapter` and
  approved/denied proto mappers.
- **proto module**: new `join_approved.proto`, `join_denied.proto` in
  `io.github.smiskinext.event.meet.v1` (must pass Buf `STANDARD`).
- **notification service**: new `meet.join.created` consumer (restored),
  approved/denied consumer, requester SSE stream + controller, requester
  result-replay store + Redis adapter, SSE manager extension.
- **APIs**: meet `openapi.yaml` regenerated with the two new endpoints.
- **specs**: `event-driven`, `join-request-notification` deltas; two new
  capability specs.
- Out of scope: guest join, meeting auto-start, TTL-expiry cleanup job, email.
