## Context

The `meet` service (Spring Boot 4 / Java 25, hexagonal + DDD) issues LiveKit
access tokens today but never receives LiveKit's server webhooks. As a result
the participation-log lifecycle and meeting `SCHEDULED → RUNNING → COMPLETED`
transitions are never driven by real room activity, and three
`ParticipationLogRepository` methods still throw `UnsupportedOperationException`
with "later slice for webhook handler" TODOs. The domain is already shaped for
this: `ParticipationLog.join/assignSid/leave/supersede`,
`Meeting.start/complete`, and the
`ParticipantJoinedEvent`/`ParticipantLeftEvent` (published to Kafka for the chat
system) all exist. Infrastructure is partly wired: `application.yaml` carries
`app.livekit.webhook-url`, `services/docker` configures LiveKit's
`webhook.urls`, and Caddy routes the webhook path to the meet service. The
LiveKit server SDK (`io.livekit:livekit-server` 0.13.0) provides
`io.livekit.server.WebhookReceiver` and `livekit.LivekitWebhook.WebhookEvent`.

Two hard constraints dominate the design:

1. **Tenancy.** Every table uses Hibernate `@TenantId` discriminator
   multi-tenancy resolved from `TenantContext` (a `ThreadLocal` bound by
   `TenantFilter` from the `X-Tenant-ID` header). Webhooks arrive directly from
   LiveKit with no tenant header, so `TenantContext` defaults to `system` and
   every query/insert would be mis-scoped. The room name (`meeting-<uuid>`) and
   participant identity (`<accountId>:<deviceId>`) carry no tenant.
2. **At-least-once delivery.** LiveKit retries webhooks and gives no cross-retry
   ordering guarantee; behind a load balancer any meet instance may receive any
   event. `assignSid`/`leave`/`complete` currently throw if invoked twice.

## Goals / Non-Goals

**Goals:**

- Receive and authenticate LiveKit webhooks via the SDK `WebhookReceiver`.
- Resolve tenant per event without a request header, by embedding `tenantId` in
  the LiveKit room metadata at token-issue time and reading `room.metadata` in
  the consumer.
- Process the four core events (`room_started`, `room_finished`,
  `participant_joined`, `participant_left`) idempotently against DB state.
- Process asynchronously (verify → enqueue → 200; consumer does DB work) for
  horizontal scalability and fast acknowledgement.
- Implement the three deferred `ParticipationLogRepository` lookups.
- Publish existing meeting/participant domain events through the outbox.

**Non-Goals:**

- Egress/recording webhooks (`egress_*`, `ingress_*`) — owned by `record`.
- Track-level events and connection-quality signals.
- Chat message creation (owned by the chat consumer of the published events).
- Changing the shared `ApiPathPrefix` mechanism or the `CloseReason` enum.
- Pre-creating LiveKit rooms via the room service; rooms remain auto-created on
  first join, now carrying tenant metadata from the token's room configuration.

## Decisions

### D1 — Tenant via room metadata (token `RoomConfiguration`)

Set
`AccessToken.setRoomConfiguration(RoomConfiguration{ name = "meeting-<id>", metadata = tenantId })`
when issuing both HOST and PARTICIPANT tokens. LiveKit auto-creates the room
from the first joiner's token config, so `room.metadata` on every subsequent
room/participant webhook carries the tenant. The consumer reads
`event.getRoom().getMetadata()`, then binds
`TenantContext.setCurrentTenant(...)` for its transaction and clears it in a
`finally`.

- Requires adding `tenantId` to `LiveKitTokenRequest` and passing it from
  `CreateInstantMeetingApplicationService` and `RequestJoinApplicationService`.
- **Alternative — cross-tenant native lookup by meeting UUID** (like
  `OutboxEventJpaRepository.findByIdAcrossTenants`): works because `meetings.id`
  is globally unique UUIDv7, but leaks tenant-bypass native queries into a
  security-sensitive path and does not cover `room_started`/`room_finished`
  cleanly for future non-meeting rooms. Rejected in favor of self-describing
  payloads.
- **Alternative — tenant in participant attributes**: unavailable on
  `room_started`/`room_finished` (no participant). Rejected.

### D2 — Asynchronous processing over an internal Kafka topic

The controller verifies the signature synchronously and publishes the decoded
event to an internal Kafka topic keyed by room name, then returns `200`. A
`@KafkaListener` consumer performs the DB work. Room-name keying preserves
per-room ordering on a partition. This isolates LiveKit's fast-ack expectation
from DB latency and lets multiple meet instances share the consumer group.

- The tenant travels in the message payload (from `room.metadata`), so the
  consumer is not on an HTTP thread and binds `TenantContext` explicitly.
- **Trade-off**: adds a topic + consumer and cross-partition ordering is only
  per-room, not global — acceptable because handlers are idempotent and
  cross-room ordering is irrelevant.
- **Alternative — synchronous inline handling**: simpler, but couples ack
  latency to DB writes and to the outbox relay. Rejected given the explicit
  scale requirement; idempotency work is identical either way.

### D3 — Idempotency by current DB state

Each handler checks state before mutating: `room_started` only acts on
`SCHEDULED`; `room_finished` only on `RUNNING`; `participant_joined` skips if an
active log already has the SID (and supersedes an orphaned active session for
the same identity per the repository rejoin contract); `participant_left` skips
if the matching session is already closed or unknown. Domain-event enqueue
happens only when a real transition/mutation occurs, preventing duplicate outbox
rows on retries.

### D4 — Webhook path aligned to the integer-version convention

`ApiPathPrefixAutoConfiguration` prepends `/api/{version}` (integer) to every
application `@RestController`; the existing config value
`/api/v1/webhook/livekit` cannot resolve (`v1` is not an integer). Standardize
on `/api/1/webhooks/livekit`: the controller declares
`@PostMapping("/webhooks/livekit")` and the auto-prefix yields
`/api/1/webhooks/livekit`. Update the four config sources (`application.yaml`,
`services/docker/.env.example`, `services/docker/compose.yaml`,
`services/docker/caddy/Caddyfile`) to match.

- **Alternative — exclude the webhook controller from the shared prefix**:
  cross-cutting change to shared infra affecting all services. Rejected.

### D5 — Security posture of the endpoint

`SecurityConfig` already permits all requests (gateway-fronted). The webhook
must be reachable without the tenant header, so it must not depend on
`TenantFilter` binding. Authentication is the LiveKit signed-JWT payload check
via `WebhookReceiver`; an invalid/missing signature yields `401`. The raw body
must be read exactly as received (no pre-parsing) for the hash to validate — the
handler consumes the request body as a raw string with content type
`application/webhook+json`.

### D6 — `CloseReason.LEFT` for room_finished bulk close

Active sessions still open when the room finishes are closed with `LEFT` and a
`leftAt` of the event time, reusing `ParticipationLog.leave`. This avoids a DB
migration to the `ck_participation_close_reason` CHECK constraint and matches
the existing enum (`LEFT`, `SUPERSEDED`).

## Webhook processing sequence

```mermaid
sequenceDiagram
    participant LK as LiveKit Server
    participant CT as LiveKitWebhookController
    participant WR as WebhookReceiver (SDK)
    participant K as Kafka (internal topic, key=room)
    participant CS as Webhook Consumer
    participant DB as Postgres (meet)
    participant OB as Outbox → Kafka

    LK->>CT: POST /api/1/webhooks/livekit (raw body + Authorization JWT)
    CT->>WR: receive(rawBody, authHeader)
    alt invalid signature
        WR-->>CT: throws
        CT-->>LK: 401
    else valid
        WR-->>CT: WebhookEvent
        CT->>K: publish(event, key=room.name)
        CT-->>LK: 200 OK
        K->>CS: deliver event (per-room ordered)
        CS->>CS: bind TenantContext from room.metadata
        alt room_started
            CS->>DB: Meeting.start() if SCHEDULED (else no-op)
        else participant_joined
            CS->>DB: supersede orphan; create ParticipationLog + assignSid (skip if SID exists)
        else participant_left
            CS->>DB: close active log by SID = LEFT (skip if closed/unknown)
        else room_finished
            CS->>DB: Meeting.complete() + close all active logs = LEFT
        else other
            CS->>CS: no-op ack
        end
        CS->>OB: enqueue domain events (only on real change)
        CS->>CS: clear TenantContext
    end
```

## Risks / Trade-offs

- **[Lost `participant_left` leaves orphaned active sessions]** →
  `participant_joined` supersedes an existing active session for the same
  identity; `room_finished` bulk-closes any remainder with `LEFT`.
- **[Out-of-order delivery: `participant_left` before its `joined`]** → the
  leave handler finds no active session and is a safe no-op; the later join
  creates a session that a subsequent `room_finished` will close.
- **[Duplicate delivery / multi-instance double processing]** → all handlers are
  idempotent against DB state and enqueue outbox events only on real change;
  unique active-identity constraint plus retry-on-conflict guards concurrent
  inserts per the existing repository contract.
- **[Missing/foreign tenant metadata]** → consumer performs no tenant-scoped
  write and records the anomaly rather than writing to the `system` tenant.
- **[Raw-body parsing breaks signature check]** → controller consumes the body
  as a raw string; no `@RequestBody` JSON binding before verification.
- **[Internal topic backlog under spikes]** → consumer group scales with meet
  instances; per-room keying bounds head-of-line blocking to a single room.

## Migration Plan

1. Additive code + config only; no DB migration (reuses existing schema, enum,
   and events).
2. Deploy meet with the new endpoint and consumer.
3. Update LiveKit `webhook.urls` / gateway route to `/api/1/webhooks/livekit`.
4. New tokens carry room-metadata tenant automatically; rooms created before
   deploy simply won't have metadata until their next room creation — acceptable
   because processing is additive and missing metadata is handled safely.
5. **Rollback**: revert the config URL so LiveKit stops delivering, and disable
   the consumer; no schema to unwind.

## Open Questions

- None blocking. Internal topic name and partition count are implementation
  details to be fixed in tasks (default: single logical topic, partitioned,
  keyed by room name).
