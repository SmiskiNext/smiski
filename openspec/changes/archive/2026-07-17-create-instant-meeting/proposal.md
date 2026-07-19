## Why

A Jira user viewing an issue needs to start a meeting immediately ("meet now")
without scheduling. The `meet` service already has a complete domain layer
(`Meeting.instant()`, invitee/participation aggregates, LiveKit ports, events)
but no application, presentation, or infrastructure adapters — so no endpoint
can be called yet. This change delivers the first working vertical slice: create
an instant meeting, join it as host immediately, and notify invitees.

## What Changes

- Add `POST /api/1/meetings:instant` returning a meeting snapshot plus a LiveKit
  HOST access token so the caller can join the room right away. Request body
  requires `title`, `description`, `issueLink`, `settings` (maxParticipants
  ≤100), and `host`. Invitees are optional.
- Resolve the host `accountId` from a request header via a new **shared**
  servlet filter (`X-Account-Id` → `AccountContext`), mirroring the existing
  `TenantFilter`/`TenantContext` pattern. Tenant continues to come from
  `X-Tenant-ID`.
- On creation: generate a unique `shortCode`, create the `Meeting` as `INSTANT`,
  and **auto-start** it to `LIVE`.
- Accept a frontend-resolved invitee list (`email`, required `accountId`,
  required `displayName`). For each invitee: create a `MeetingInvitee`, generate
  an invite token (JWT; only the SHA-256 hash is stored), and emit
  `MeetingInvitationsCreatedEvent` carrying the raw tokens. Sending invite
  emails is **out of scope** — the `notification` service consumes the event
  later.
- Publish `MeetingCreatedEvent`, `MeetingStartedEvent`, and (when invitees are
  present) `MeetingInvitationsCreatedEvent` via the transactional outbox → Kafka
  (CloudEvents over protobuf), reusing the shared outbox relay. Both
  `MeetingCreatedEvent` and `MeetingStartedEvent` carry a full aggregate
  snapshot. `MeetingInvitationsCreatedEvent` embeds the invite token directly in
  each invitee entry (no separate token map).
- Add a new domain method `Meeting.recordInvitationsSent(...)` so the
  invitations event is registered on the aggregate (consistent with the existing
  `registerEvent()` + `PublishableEvent` pattern).
- Stand up the missing `meet` infrastructure required to run the slice:
  persistence adapters (meeting, participation log, invitee, outbox), LiveKit
  adapter + config, Kafka producer config, outbox event publisher + proto
  mappers, and an invite-token generator.
- Add protobuf event schemas for the three `meet` events under `services/proto`
  and regenerate.

## Capabilities

### New Capabilities

- `create-instant-meeting`: Creating an instant (start-now) meeting — request
  contract, host-identity resolution, meeting lifecycle (create → LIVE + host
  participation), invitee registration with invite tokens, LiveKit host-token
  issuance, domain-event publication via the outbox, and the response snapshot.

### Modified Capabilities

<!-- None. No existing capability's requirements change; this is a new slice. -->

## Impact

- **New endpoint**: `POST /api/1/meetings:instant` (meet service) — regenerates
  `services/meet/openapi.yaml`.
- **Shared library**: new `infrastructure/identity` package (`AccountContext`,
  `AccountFilter`, `AccountProperties`, auto-configuration) consumable by all
  services; no behavior change for existing services.
- **meet service**: new application, presentation, and infrastructure adapters
  (persistence, LiveKit, messaging); one new domain method; `application.yaml`
  gains `smiski.outbox` and account-header config. Uses the existing `meetings`,
  `participation_logs`, `meeting_invitees`, and `outbox_event` baseline tables —
  **no new migration**.
- **proto service**: shared `meeting_snapshot.proto` plus event messages under
  `event/meet/v1` (`meeting_created`, `meeting_started`,
  `meeting_invitations_sent`). Both created and started events embed a
  `MeetingSnapshot` message.
- **Kafka topics** (produced): `meet.meeting.created`, `meet.meeting.started`,
  `meet.meeting.invitations-sent`.
- **Out of scope**: schedule endpoint, join/`requestJoin` flow, LiveKit
  webhooks, invite accept/decline, email delivery, and the currently-unused
  query methods on the meet repositories (adapters throw
  `UnsupportedOperationException` for those, to be implemented in later slices).
