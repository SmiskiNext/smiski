## Why

The `meet` service issues LiveKit access tokens but never receives LiveKit's
server-side webhooks, so the room/participant lifecycle is invisible to the
backend: `ParticipationLog` rows are never created, meetings never transition
`SCHEDULED → RUNNING → COMPLETED` from real room activity, and the repository
methods that earlier slices deferred to "the webhook handler"
(`findActiveBySid`, `findActiveByMeetingIdAndIdentity`, `findActiveByMeetingId`)
still throw `UnsupportedOperationException`. LiveKit is the source of truth for
who is actually connected; without ingesting its webhooks the participant list,
meeting status, and downstream chat system events are all wrong.

## What Changes

- Add a LiveKit webhook receiver endpoint to `meet` that validates the signed
  JWT (`Authorization` header) against the raw `application/webhook+json` body
  using the LiveKit server SDK `WebhookReceiver`, then acknowledges fast.
- Process webhooks **asynchronously** for horizontal scalability: the endpoint
  verifies the signature and enqueues the decoded event to an internal Kafka
  topic keyed by room name (per-room ordering); a consumer performs the DB work
  under an explicitly bound tenant context.
- Handle four core events, idempotently against DB state:
    - `room_started` → transition the meeting `SCHEDULED → RUNNING` (no-op if
      already `RUNNING`/`COMPLETED`).
    - `participant_joined` → create a `ParticipationLog` and assign the LiveKit
      participant SID (no-op if the SID is already recorded; supersede an
      orphaned active session for the same identity per the repository
      contract).
    - `participant_left` → close the matching active `ParticipationLog` with
      `CloseReason.LEFT` (no-op if already closed).
    - `room_finished` → transition the meeting `RUNNING → COMPLETED` and close
      all remaining active participation logs with `CloseReason.LEFT`.
    - All other events (`track_*`, `egress_*`, `ingress_*`,
      `participant_connection_aborted`) are acknowledged as no-ops.
- Resolve tenant without a request header by **embedding `tenantId` in the
  LiveKit room metadata** at token-issue time (via
  `AccessToken.setRoomConfiguration`), so every webhook payload carries the
  owning tenant in `room.metadata`. The consumer binds `TenantContext` from that
  value for the duration of its transaction.
- Implement the three deferred `ParticipationLogRepository` lookup methods and
  their JPA queries.
- Publish the existing `ParticipantJoinedEvent`, `ParticipantLeftEvent`,
  `MeetingStartedEvent`, and `MeetingCompletedEvent` through the transactional
  outbox as part of webhook processing.

## Capabilities

### New Capabilities

- `livekit-webhook`: Receiving, authenticating, and asynchronously processing
  LiveKit server webhooks (`room_started`, `room_finished`,
  `participant_joined`, `participant_left`) to drive meeting status transitions
  and participation-log lifecycle, with tenant resolution via room metadata and
  idempotent, at-least-once-safe handling.

### Modified Capabilities

- `create-instant-meeting`: Host token issuance additionally embeds the owning
  `tenantId` into the LiveKit room configuration/metadata so the room-lifecycle
  webhooks can resolve the tenant.
- `join-meeting`: Participant token issuance (ALLOW_ALL admission) additionally
  embeds the owning `tenantId` into the LiveKit room configuration/metadata.

## Impact

- **New code** (`services/meet`): webhook controller (presentation), webhook
  handling use case + application service, internal Kafka publisher + consumer,
  `WebhookReceiver` bean and config, three `ParticipationLogRepositoryAdapter`
  method implementations + JPA queries.
- **Modified code** (`services/meet`): `LiveKitTokenRequest` gains a `tenantId`
  field; `LiveKitAdapter.generateToken` sets `RoomConfiguration` (name +
  metadata=tenantId) for both HOST and PARTICIPANT tokens;
  `CreateInstantMeetingApplicationService` and `RequestJoinApplicationService`
  pass the tenant into the token request.
- **Config**: `application.yaml` (webhook path, internal topic), and
  `services/docker/.env.example`, `services/docker/compose.yaml` (LiveKit
  webhook url), `services/docker/caddy/Caddyfile` aligned to the versioned
  integer path convention `/api/1/webhooks/livekit`.
- **Security**: the webhook endpoint is unauthenticated at the gateway but
  authenticated by LiveKit's signed-JWT payload verification; it must bypass the
  tenant header filter and never trust unsigned requests.
- **Dependencies**: uses the already-present `io.livekit:livekit-server` SDK
  (`WebhookReceiver`) and existing Kafka infrastructure; no new dependency.
- **Out of scope**: egress/recording (owned by `record`), track-level events,
  chat message creation (owned by the chat consumer), and any change to the
  shared `ApiPathPrefix` mechanism or the `CloseReason` enum.
