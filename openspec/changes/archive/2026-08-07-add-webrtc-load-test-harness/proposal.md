## Why

An academic practical-work assignment requires empirical performance and
reliability evidence for the WebRTC meeting stack. The repository today has no
way to produce it: there is no load generator, no network-impairment tooling, no
TURN server, no LiveKit metrics endpoint, and no way to authenticate a scripted
caller through the Envoy gateway (the Forge Invocation Token is signed by
Atlassian and cannot be minted locally).

This change builds that measurement harness. The assignment is reproduced below
verbatim so implementers work from the original acceptance thresholds rather
than a paraphrase.

### Assignment: Practical Exercise 1 — NAT Traversal & QoS Testing

Simulate different real-world network environments (LAN, mobile 4G, corporate
network blocking UDP) to test ICE protocol and STUN/TURN server stability, and
measure media transport quality.

**TC-01: NAT traversal via TURN server**

A user joins a meeting from a strict LAN that blocks all direct UDP, forcing
relay through TURN.

- Meeting connects successfully over an encrypted protocol
- Call Setup Time < 3 seconds
- Audio and video streams render smoothly

**TC-02: Media stream Quality of Service measurement**

Hold a 2-party video call for 15 minutes with stream monitoring enabled (WebRTC
Internals).

- End-to-End Latency < 200 ms
- Packet Loss Rate < 2%
- Jitter < 30 ms

### Assignment: Practical Exercise 2 — SFU Scalability & Stress Testing

Use simulation tools (LiveKit benchmarking tool or headless browser scripts) to
drive concurrent users into the system, evaluating SFU architecture capacity and
the Redis storage tier.

**TC-03: Room capacity test**

Simulate one large room with 30 participants, of which 10 publish camera video
at 720p and 1 shares their screen.

- LiveKit SFU distributes streams correctly without the room crashing
- Server bandwidth distribution shows no bottleneck
- Connection error / room drop rate = 0%

**TC-04: Token generation and state synchronisation performance**

Simulate 500 concurrent connection and Access Token requests to the backend
within 1 second.

- Token distribution success rate reaches 100%
- Meeting state in the Redis cache updates correctly with no data conflict
- Average token generation API response time < 500 ms

**TC-05: Docker infrastructure resource monitoring under load**

Measure host hardware performance throughout TC-03 and TC-04.

- LiveKit Server container CPU usage < 80%
- Redis container RAM usage < 256 MB (no memory leak or cache overflow)

### Assignment: Data to collect

- **Real-time WebRTC metrics**: actual consumed bandwidth (bitrate in kbps, both
  up and down), frame drop rate, audio and video latency, collected via the
  LiveKit dashboard or WebRTC statistics.
- **Infrastructure performance logs**: CPU, RAM, network I/O and disk read/write
  charts for each independent container (LiveKit Server, Coturn, backend, Redis,
  PostgreSQL), collected with system monitoring tools such as Prometheus/Grafana
  or Docker stats.
- **Integration business metrics**: meeting data synchronisation success rate to
  the project management system, meeting history audit-trail timing, and
  security audit logs for the Access Token issuance flow.

### Assignment constraints given by the requester

- `scripts/` is the folder for setup/run test scripts.
- `services/test/` holds the Docker stack and related assets for this work.
  `services/docker/` MUST NOT be modified.
- Full automation of every measurement is not required; the harness is expected
  to combine scripted steps with manual operator steps.

### Codebase reality that contradicts the assignment

Investigation of the repository found six mismatches. Each is resolved by an
explicit decision so implementers do not silently reinterpret the assignment.

| #   | Assignment states                                  | Repository reality                                                                                                              | Resolution                                                               |
| --- | -------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------ |
| 1   | Backend is NestJS                                  | Spring Boot 4 / Java 25, service `meet`                                                                                         | TC-04 targets the Spring `meet` service                                  |
| 2   | Coturn container is monitored                      | No Coturn anywhere; no `turn:` block, no `rtc.turn_servers`                                                                     | Add a standalone Coturn container to the test stack                      |
| 3   | "the Redis container"                              | Two instances: `valkey` (application) and `livekit-redis` (LiveKit-internal, no exporter)                                       | TC-05 measures both, reported separately                                 |
| 4   | Metrics from a LiveKit dashboard                   | LiveKit exposes no metrics: `prometheus_port` unset, no `livekit` scrape job                                                    | Enable `prometheus_port` and add scrape jobs in the test stack           |
| 5   | Meeting sync rate to the project management system | `meet` never writes to Jira; it stores `issue_id`/`issue_key` locally, and only the Go gateway reads Jira for permission checks | Redefine as outbox drain latency plus `participation_logs` write success |
| 6   | Kafka lag as an integration metric                 | `apache/kafka:4.1.0` ships no JMX exporter and is deliberately excluded from Prometheus                                         | Measure drain via the `outbox_events` table instead                      |

### Verified technical constraints that shape the design

- **Token issuance never contacts LiveKit.** `LiveKitAdapter.generateToken`
  signs a JWT locally
  (`services/meet/.../infrastructure/livekit/LiveKitAdapter.java:93`). TC-04
  therefore stresses Spring, Postgres and Valkey — not the SFU.
- **A pessimistic row lock serialises concurrent joins.**
  `RequestJoinApplicationService` takes `findActiveByIdWithLock`
  (`MeetingJpaRepository.java:29-32`, `PESSIMISTIC_WRITE`) and holds it for the
  whole transaction. 500 concurrent joins against one meeting measure lock-queue
  latency, not throughput, so TC-04 must run both a single-room and a
  sharded-room variant.
- **TC-04's two success criteria are mutually exclusive in one run.** Under
  `ALLOW_ALL` every request returns a token but touches no Redis; under
  `MANUAL_APPROVAL` Redis is written but the response carries `token: null`.
  TC-04 splits into TC-04a (token throughput) and TC-04b (Redis state).
- **Envoy materially affects measured latency.** Bypassing it would skip RS256
  verification, the Lua claim filter, the ext_authz gRPC hop and the gateway's
  Valkey cache. Measurement therefore runs through `envoy:30000`.
- **A locally signed FIT is accepted by Envoy.** Verified experimentally against
  Envoy v1.36 using the repository's own `extract_claims.lua`: an unsigned
  request and a bad-signature request both returned 401, while a locally signed
  token returned 200 with `x-fit-cloud-id` and `x-fit-account-id` overwritten
  from verified claims even when the client attempted to spoof them. Swapping
  `remote_jwks` for `local_jwks` is sufficient; no mock JWKS service is needed.
- **The gateway calls Jira on cache miss with a 2 second timeout**
  (`services/gateway/internal/jira/client.go:20`), which would dominate the 500
  ms threshold. A mock Jira service removes that external dependency.
- **`lk load-test` cannot publish a screen-share track** — it hardcodes
  `TrackSource_CAMERA`. The screen-share leg of TC-03 is performed manually.
- **`services/test/` is currently a byte-identical copy** of `services/docker/`
  (510 identical lines, same volume and port names), which would drift on every
  dev-stack change.
- **`scripts/package.json` declares `bin.smiski` pointing at `./src/index.ts`,
  which does not exist.** The directory has no source at all.

## What Changes

### Test stack (`services/test/`)

- **BREAKING**: replace the duplicated `compose.yaml` with a thin overlay that
  uses `include: ../docker/compose.yaml` and a distinct project name, so the
  test stack tracks the dev stack automatically and runs alongside it with
  isolated volumes. Delete the duplicated `envoy/` and `observability/` assets
  that the overlay no longer needs.
- Override the `livekit_server_config` block to set `prometheus_port` and
  declare Coturn under `rtc.turn_servers`.
- Add a `coturn` container using TURN/TCP with a shared static auth secret, so
  relay works when all UDP is blocked and its resources can be measured
  independently.
- Add a `mock-jira` container returning fixed bulk-permission responses, wired
  through the existing `JIRA_API_BASE` variable.
- Override `envoy.yaml` to read `local_jwks` from a generated key file instead
  of fetching Atlassian's remote JWKS, and drop the now-unused JWKS cluster.
- Extend the Prometheus scrape configuration with jobs for LiveKit, Coturn and a
  `livekit-redis` exporter.
- Add a static harness page, served over HTTP, that joins a room with a pasted
  token, can force `iceTransportPolicy: 'relay'`, can publish a screen share,
  and samples `RTCStatsReport` once per second to a downloadable CSV.

### Developer CLI (`scripts/src/`)

- Create the missing CLI entrypoint that `scripts/package.json` already
  declares, built on the existing `citty` + `zx` + `tsx` toolchain.
- `smiski test keygen` — generate an RSA keypair and the Envoy JWKS file.
- `smiski test token` — sign a Forge Invocation Token carrying the exact claim
  shape the gateway parser requires.
- `smiski test seed` — insert one tenant and N meetings under both admission
  policies, printing the identifiers the other commands consume.
- `smiski test loadtest tokens` — drive TC-04a/TC-04b through k6 in Docker, in
  cold-cache and warm-cache passes and in single-room and sharded variants.
- `smiski test loadtest room` — drive TC-03 through `lk load-test`, pinned to a
  known CLI version.
- `smiski test impair` — apply and remove `tc netem` and `iptables` profiles for
  LAN, 4G and blocked-UDP conditions.
- `smiski test collect` — query the Prometheus range API and emit one CSV per
  test case.

### Documentation

- Add `services/test/AGENTS.md` covering startup, the operator steps each test
  case requires, and how the six assignment mismatches were resolved.

## Capabilities

### New Capabilities

- `load-test-harness`: The measurement harness as a whole — the test-stack
  overlay, the locally signed FIT authentication path, the mock Jira boundary,
  TURN relay provisioning, network-impairment profiles, the CLI command surface,
  the browser QoS harness, and the acceptance thresholds and evidence artifacts
  for TC-01 through TC-05.

### Modified Capabilities

None. `dev-infras` describes `services/docker/`, which this change does not
modify; the overlay only consumes it. `test-architecture` governs JVM unit and
integration test source sets, which are untouched.

## Impact

**Created**

- `services/test/compose.yaml` (rewritten as an overlay),
  `services/test/coturn/`, `services/test/mock-jira/`, `services/test/harness/`,
  `services/test/envoy/envoy.yaml` (overlay),
  `services/test/observability/prometheus.yml` (overlay),
  `services/test/.env.example`, `services/test/AGENTS.md`
- `scripts/src/**` — new CLI, fixing the broken `bin.smiski` declaration

**Deleted**

- The duplicated copies under `services/test/` that the overlay replaces:
  `envoy/lua/`, `observability/loki.yaml`, `observability/config.alloy`,
  `observability/grafana/`

**Untouched**

- `services/docker/**` — explicitly out of scope
- All Java sources under `services/meet/`, `services/tenant/`,
  `services/notification/`, `services/shared/`
- The Go `services/gateway/`
- The Forge app under `app/`

**New external dependencies**, all pulled as pinned Docker images so nothing is
installed on the host: `grafana/k6`, `coturn/coturn`, `livekit/livekit-cli`,
`oliver006/redis_exporter` (a second instance for `livekit-redis`).

**Security**: the test stack accepts locally signed Forge Invocation Tokens and
runs a permissive mock Jira. It is strictly local-only, inheriting and widening
the existing warning on the dev stack. Generated private keys must never be
committed.
