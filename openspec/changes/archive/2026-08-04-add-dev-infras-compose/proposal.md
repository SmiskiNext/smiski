## Why

`services/docker/` lost its `compose.yaml`, `.env.example`, and `AGENTS.md` when
Caddy was replaced by Envoy (commit `2f57ec6abb26`), leaving only
`services/docker/envoy/`. There is no way to start the system locally: the
`pnpm smiski infra up` commands documented in `AGENTS.md` and `AGENTS.md` cannot
run because `scripts/src/` was deleted in commit `63478870da25`.

Auditing every controller mapping against `services/docker/envoy/envoy.yaml`
revealed the gateway cannot serve the product's primary flows. Four routing
defects exist today, and one service cannot start at all — so restoring the
stack requires fixing the gateway and service configuration together, not just
re-adding a compose file.

## What Changes

### Local infrastructure stack

- Add `services/docker/compose.yaml` running the full stack in containers:
  `tenant`, `meet`, `notification`, `gateway`, `envoy`, three dedicated Postgres
  instances, Kafka (KRaft), Valkey, LiveKit server and its own Redis.
- Add `services/docker/.env.example` enumerating every environment variable,
  marking those with no default that block startup when unset.
- Add `../../../../services/docker/AGENTS.md` documenting the port map, the
  routing table, and the security caveat on unauthenticated SSE.
- Java service images come from `bootBuildImage`
  (`ghcr.io/smiskinext/<name>:<version>`), matching the CI publish path in
  `.github/workflows/release.yml`, so local and released images share one build.
- Exclude `record` — it is no longer registered in
  `services/settings.gradle.kts` and no service consumes its `record.*` topics.
- Exclude RustFS, `livekit-egress`, and bucket initialisation: recording is dead
  configuration (see below).
- LiveKit ports `7880`/`7881`/`7882` are published directly to the host. WebRTC
  media cannot traverse an HTTP gateway, so proxying only signalling would split
  the transport across two paths for no local benefit.

### Envoy routing audit

Every route is verified against actual controller mappings. Route order matters
because Envoy applies first-match-wins.

- Add `prefix: /api/1/issues` → `meet_cluster`.
  `POST /api/1/issues/{issueId}/meetings` (`MeetingController.java:2584`) had no
  route and returned 404, breaking the issue panel's meeting list — the app's
  main surface.
- Add a regex route for `^/api/1/meetings/[^/]+(/join-requests/[^/]+)?/events$`
  → `notification_cluster`. These SSE endpoints live in `notification`
  (`MeetingEventsController.java:65,114`) but the `prefix: /api/1/meetings`
  route sent them to `meet`, which has no such endpoint. The route also needs
  `timeout: 0s`; Envoy's 15-second route default would sever every stream.
- Add `path: /api/1/webhooks/livekit` → `meet_cluster` and
  `path: /api/1/webhooks/resend/inbound` → `notification_cluster`. Both had no
  route. Both bypass gateway authentication because external callers never carry
  a Forge Invocation Token, and both already verify their own signatures
  (LiveKit `WebhookReceiver`; Resend Svix inside
  `ProcessInboundEmailReplyUseCase`).
- Remove `prefix: /api/1/notifications`. No controller in any service declares
  that path.
- Per-route filter bypass must disable **both** `jwt_authn` and `ext_authz`.
  Disabling only `jwt_authn` still fails: `ext_authz` is configured at the
  `http_filters` level so it runs for every route, and the gateway rejects any
  request without an `Authorization` header (`server.go:48-50`).

### Service configuration corrections

- Add `app.cursor.secret` to
  `services/tenant/src/main/resources/application.yaml`. `TenantApplication`
  scans `io.github.smiskinext.shared` (`TenantApplication.java:10`), so the
  `CursorEncoder` bean is always created, and it reads
  `@Value("${app.cursor.secret}")` with no default (`CursorEncoder.java:45`).
  The key is absent, so **`tenant` fails to start today**.
- Normalise dev Postgres ports so native runs match the compose port map:
  `tenant` `8283` → `8281` (8283 was the old `chat-mongo` port), `notification`
  `5432` → `8283`.

### Dead configuration removal

- Remove the ten `app.livekit.recording.*` keys from `meet`'s
  `application.yaml`, `application-dev.yaml`, `application-staging.yaml`, and
  `application-test.yaml`. No `@ConfigurationProperties` or `@Value` binds them;
  `LiveKitProperties` exposes no `recording` component, and no
  `software.amazon`/`Egress` reference exists anywhere under
  `services/meet/src`.
- Remove `implementation(libs.aws.s3)` from `services/meet/build.gradle.kts:20`
  — an orphan left by the decommissioned `record` service.
- Remove `invite-invalidated-consumer-group` from `notification`. No consumer
  reads it.

### Documentation corrections

- `services/AGENTS.md` states `notification` has no DB and no Flyway. It has
  both: `db/migration/B1.0.0__baseline.sql` creates a `tenants` projection table
  and `build.gradle.kts` includes Flyway. The file also still lists `record`.
- `AGENTS.md`, `CLAUDE.md`, and `AGENTS.md` document `pnpm smiski` commands that
  cannot run.

### Accepted technical debt

The SSE routes are served **without authentication**. Forge cannot authenticate
them today: `requestRemote` documents "Response body streaming is not supported"
so streams cannot flow through it, and `EventSource` cannot set an
`Authorization` header. The frontend therefore calls the gateway with a plain
browser `fetch` (`sseClient.ts:57`) carrying no token.

This is a real access-control gap, not a cosmetic one:
`/meetings/{id}/join-requests/{requestId}/events` returns a LiveKit room token
in its payload, and identifiers are UUIDv7 — time-ordered, therefore partially
guessable. `MeetingEventsController.java:25-26` explicitly delegates host
identity verification to the gateway, so opening the route removes the only
check in the design.

**Scope: local development only.** The compose stack must not be used as a
staging or production baseline while this gap exists. Closing it requires either
short-lived stream tickets validated via Envoy `jwt_authn` `from_params`, or
migrating to Forge Realtime — tracked as follow-up work, out of scope here.

## Capabilities

### New Capabilities

- `dev-infras`: Local development infrastructure — the containerised topology,
  service and infrastructure dependencies, port allocation, image provenance,
  environment variable contract, startup ordering, and the gateway route table
  mapping every backend endpoint to its owning service with the correct
  authentication level.

### Modified Capabilities

- `envoy-gateway-routing`: The existing spec routes to `tenant:8081`,
  `meet:8082`, and `notification:8083`, but no service overrides `server.port` —
  all three listen on `8080`, as `envoy.yaml:161-201` already encodes. The spec
  also omits four routes that exist in code (`/api/1/issues/**`, the two SSE
  paths, and the two webhooks), declares a `/api/1/notifications` route with no
  backing endpoint, and states the gateway caches GET responses although no
  cache filter is configured. Requirements are corrected to match the audited
  route table, including which routes bypass FIT validation and external
  authorization.

## Impact

**New files**: `services/docker/compose.yaml`, `services/docker/.env.example`,
`../../../../services/docker/AGENTS.md`

**Modified**:

- `services/docker/envoy/envoy.yaml` — route table, per-route filter bypass,
  streaming timeouts
- `services/tenant/src/main/resources/application.yaml` — add
  `app.cursor.secret`
- `services/tenant/src/main/resources/application-dev.yaml`,
  `services/notification/src/main/resources/application-dev.yaml` — Postgres
  ports
- `services/meet/src/main/resources/application{,-dev,-staging}.yaml`,
  `services/meet/src/integrationTest/resources/application-test.yaml` — remove
  recording keys
- `services/meet/build.gradle.kts` — remove `libs.aws.s3`
- `services/notification/src/main/resources/application.yaml`,
  `services/notification/src/integrationTest/resources/application-test.yaml` —
  remove unused consumer group
- `AGENTS.md`, `CLAUDE.md`, `AGENTS.md`, `services/AGENTS.md` — correct stale
  commands and service facts

**Unchanged**: `services/docker/envoy/lua/extract_claims.lua`, all Java and Go
source, the Forge app under `app/`, `services/k8s/`, `services/record/`, and
`scripts/` (the CLI is not restored — only the docs that reference it).

**Runtime dependencies added for local development**: Docker with Compose v2,
plus a reachable host LAN IP for LiveKit to advertise in ICE candidates.

**Risks**

- Unauthenticated SSE routes, as described above — bounded to local development.
- LiveKit requires a real host IP; loopback does not work for ICE candidates
  under rootless Docker.
- Java images must be built with `bootBuildImage` before the stack starts;
  compose does not build them.
