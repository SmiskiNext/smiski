# Load-test harness stack

Measurement harness for TC-01–TC-05 (thresholds: see `problem.md`). Thin
**overlay** on `services/docker/compose.yaml` (`include:`, `name: smiski-test`).
`services/docker/` is read-only for this work. No copied service definitions;
edit `services/test/compose.yaml` only.

> [!WARNING]
>
> Local dev only, never expose to a network. Envoy trusts a **locally signed**
> key (`keys/`, gitignored, never commit) — anyone reaching port 30000 can mint
> any identity. `mock-jira` grants every permission requested.

## File map

| Path                              | Role                                             |
| --------------------------------- | ------------------------------------------------ |
| `compose.yaml`                    | overlay services + `nat-gw`/`browser*`/networks  |
| `.env.example`                    | copy to `.env`; required keys marked             |
| `envoy/envoy.yaml`                | `local_jwks` (not Atlassian `remote_jwks`)       |
| `mock-jira/server.py`             | grants every permission asked, echoes ARI        |
| `harness/{harness.js,index.html}` | browser QoS/NAT page (TC-01/02)                  |
| `browser/custom-init.sh`          | routes `10.77.0.0/24` via `nat-gw` in `browser*` |
| `observability/prometheus.yml`    | scrape config (literal ports, no env expansion)  |
| `keys/`                           | gitignored; JWKS + private key from `keygen`     |
| `results/`                        | gitignored; CSV output, mounted into browsers    |

CLI: `scripts/src/commands/test/*` (`keygen`, `token`, `seed`, `impair`,
`loadtest/{room,tokens}`, `collect`). Invoke as
`pnpm --dir scripts smiski test <cmd> --help`.

## Network topology

Two pinned networks, `10.77.0.0/24` (media) and `10.88.0.0/24` (clients).
`ip_range: .128/25` on both — dynamic allocation stays out of the static range
below.

| Service          | Network         | IP                         |
| ---------------- | --------------- | -------------------------- |
| `livekit-server` | media           | `10.77.0.10`               |
| `harness`        | media           | `10.77.0.11`               |
| `coturn`         | media           | `10.77.0.12`               |
| `nat-gw`         | media + clients | `10.77.0.13` / `10.88.0.2` |
| `browser`        | clients         | `10.88.0.10`               |
| `browser-b`      | clients         | `10.88.0.11`               |

`nat-gw` is the only route from `clients` to `media` (MASQUERADE + FORWARD
ACCEPT). `browser`/`browser-b` get a route to `10.77.0.0/24` via `.2` from
`custom-init.sh` — default route untouched, so noVNC stays reachable. This is
what makes ICE form a real `srflx` candidate instead of a forced relay.

`livekit-server` command is `!override`'d (CLI `--node-ip` beats config
`rtc.node_ip`) and pinned to `10.77.0.10` so Coturn's peer permission matches
LiveKit's real egress address — without this, connectivity checks are silently
discarded (`requestsSent > 0, responsesReceived: 0`).

## Setup

```sh
cp services/test/.env.example services/test/.env # set SMISKI_HOST_IP
pnpm --dir scripts smiski test keygen            # before any start/validate
./services/gradlew -p services/tenant bootBuildImage
./services/gradlew -p services/meet bootBuildImage
./services/gradlew -p services/notification bootBuildImage
COMPOSE_PROFILES=observability docker compose -f services/test/compose.yaml up -d
pnpm --dir scripts smiski test seed
```

Teardown: `docker compose -f services/test/compose.yaml down [-v]`.

Validate config (profile-gated services need the profile set or they are
silently unchecked):

```sh
docker compose -f services/test/compose.yaml config
COMPOSE_PROFILES=observability docker compose -f services/test/compose.yaml config
pnpm --dir scripts lint && pnpm --dir scripts typecheck
```

## Hard constraints

- **Rebuild the 3 Java images after any backend change.** AOT bakes
  `/actuator/prometheus` in at build time; no env var adds it later. Symptom:
  `spring-services` reads `down` on `/targets`, nothing errors.
- **`harness` page needs a secure context.** Open at `http://localhost:8090`,
  never a LAN IP — `navigator.mediaDevices` is withheld outside secure context.
  Symptom: loads and samples fine, `uplink_kbps` stays 0, screen share throws.
- **Never enable `adaptiveStream`/`dynacast` in `harness.js`.** Suppresses
  `inbound-rtp` on an unsized/background video element. Symptom: `jitter_ms`,
  `packet_loss_percent`, `downlink_kbps` export blank on a healthy connection.
- **A room needs a publisher before downlink stats exist.** A lone client in an
  empty room exports blank jitter/loss — start `loadtest room` or a second
  client first.
- **`${VAR:-default}` is inert for `JIRA_API_BASE`** — already set in
  `services/docker/.env`, both `.env` files load, so redirect it by editing the
  literal in `compose.yaml`, not `.env`. Symptom: 2 s latency, empty permission
  set, no error.
- **Prometheus does not expand env vars; `promtool check config` passes
  anyway.** Ports in `observability/prometheus.yml` are literal — changing
  `LIVEKIT_PROMETHEUS_PORT` requires editing both files.
- **File validation cannot detect any of the above.** After an observability
  change, curl `/targets` on `:9090` and confirm every job is `up`.
- **NAT browsers cannot publish media unmodified.** `browser`/`browser-b` launch
  `CHROME_CLI` with `--unsafely-treat-insecure-origin-as-secure`,
  `--use-fake-device-for-media-stream`, `--use-fake-ui-for-media-stream`,
  `--user-data-dir` — without all four, uplink is 0 (insecure origin) and there
  is no capture hardware in the container anyway.
- **`impair 4g` shapes LiveKit's egress only** (downlink for every participant),
  not the client→server leg. TC-02 thresholds read from **inbound** stats, which
  is the shaped direction — do not report the uplink figure as a
  degraded-network measurement.
- **`--cloud-id` must equal the seeded `meetings.tenant_id`.** A mismatch passes
  auth, then Hibernate's `@TenantId` filter yields `404 MEETING_NOT_FOUND`.
  `smiski test seed` prints the tenant it used.
- **Two headers required on hand-rolled requests:** `x-issue-id: 10001`,
  `x-forge-oauth-system: loadtest-system-token` (missing →
  `500 configuration_error`). Body needs `displayName` + `deviceId`.

## Test cases

`local_candidate_type` in the harness export is the only thing that
distinguishes direct traversal (`srflx`/`prflx`) from relay (`relay`) — a
successful connection alone proves neither.

| Case        | Command                                                                                                                                                    | Threshold                                   | Evidence field                                                      |
| ----------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------- | ------------------------------------------------------------------- |
| TC-01 Leg 1 | connect via noVNC `:3010` → `http://10.77.0.11`, relay-only OFF                                                                                            | setup < 3000 ms                             | `local_candidate_type=srflx/prflx`, `nat traversal proven,true`     |
| TC-01 Leg 2 | `smiski test impair blocked-udp --service nat-gw`, reconnect                                                                                               | setup < 3000 ms                             | `local_candidate_type=relay`, `relay proven,true`                   |
| TC-02       | `impair 4g --service livekit-server`; both `browser`(`:3010`)/`browser-b`(`:3011`) join, publish ON, 15 min                                                | latency < 200 ms, loss < 2%, jitter < 30 ms | `round_trip_ms`, `packet_loss_percent`, `jitter_ms` per side        |
| TC-03       | `smiski test loadtest room --room "$ROOM" --duration 3m --video-publishers 10 --subscribers 19 --video-resolution high` + manual screen share from harness | 0% drop/error                               | `livekit_track_publish_counter`, harness export                     |
| TC-04a      | `smiski test loadtest tokens --admission-policy ALLOW_ALL`                                                                                                 | p50 < 500 ms (sharded warm only)            | CLI summary                                                         |
| TC-04b      | `smiski test loadtest tokens --admission-policy MANUAL_APPROVAL --variants single --cache-states warm`                                                     | Redis updated, no conflict                  | `valkey-cli --scan` for `join_request*` immediately after run (TTL) |
| TC-05       | `smiski test collect --cases tc05 --since 30` (needs `observability` profile)                                                                              | LiveKit CPU < 80%, Redis RAM < 256 MB       | CSV, separate `valkey`/`livekit-redis` jobs                         |

Reverse impairment after each case: `smiski test impair lan --service <name>` or
`--remove`.

TC-01 request:

```sh
curl -s -X POST "http://localhost:30000/api/1/meetings/<MEETING_ID>:join" \
    -H "Authorization: Bearer $(pnpm -s --dir scripts smiski test token)" \
    -H 'Content-Type: application/json' -H 'x-issue-id: 10001' \
    -H 'x-forge-oauth-system: loadtest-system-token' \
    -d '{"displayName":"NatClient","deviceId":"d1"}'
```

TC-01/02 connect fields inside the NAT browser: server `ws://10.77.0.10:7880`,
STUN `stun:10.77.0.12:3478`.

TC-04b Redis check (run before any `FLUSHALL`):

```sh
docker exec smiski-test-valkey-1 valkey-cli --scan --count 500 | rg join_request
```

## Manual vs scripted

Scripted: keygen, token, seed, impair, `loadtest/*`, `collect`. Manual:
everything through a browser (join, read setup time, screen share, holding TC-02
for 15 min, judging thresholds).

## Divergences from the assignment

| #   | Assignment                                 | Reality                                                                    | Resolution                                                                      |
| --- | ------------------------------------------ | -------------------------------------------------------------------------- | ------------------------------------------------------------------------------- |
| 1   | Backend is NestJS                          | Spring Boot 4 / Java 25, service `meet`                                    | TC-04 targets `meet`                                                            |
| 2   | Coturn container monitored                 | none existed                                                               | added standalone Coturn                                                         |
| 3   | "the Redis container"                      | two: `valkey`, `livekit-redis`                                             | TC-05 measures both, separate jobs                                              |
| 4   | Metrics from LiveKit dashboard             | `prometheus_port` unset                                                    | enabled `prometheus.port` + scrape job                                          |
| 5   | Meeting sync rate to PM system             | `meet` never writes to Jira                                                | outbox drain latency + `participation_logs`                                     |
| 6   | Kafka lag metric                           | no JMX exporter on `apache/kafka:4.1.0`                                    | drain measured via `outbox_events` table                                        |
| 7   | TC-04 100% success + Redis update, one run | `ALLOW_ALL` skips Redis; `MANUAL_APPROVAL` returns `token: null`           | split TC-04a/TC-04b                                                             |
| 8   | —                                          | `PESSIMISTIC_WRITE` lock serializes joins per meeting                      | single/sharded variants (253.5 ms vs 3.9 ms p50)                                |
| 9   | —                                          | `lk load-test` hardcodes `TrackSource_CAMERA`                              | screen share done manually from harness                                         |
| 10  | —                                          | Atlassian must sign a real FIT                                             | Envoy `local_jwks` reads a generated key                                        |
| 11  | —                                          | Forge app has no way to inject `rtcConfig`                                 | standalone harness page instead                                                 |
| 12  | —                                          | NAT type alone does not force relay (RFC 8445, reachable SFU)              | one NAT type (port-restricted cone), two legs = UDP open vs blocked             |
| 13  | —                                          | container behind NAT has no capture device, LAN origin is insecure context | `browser`/`browser-b` launch with fake-device + insecure-origin-as-secure flags |

## Token claims

`smiski test token` derives claims from
`services/gateway/internal/fit/parser.go`. Required: `iss`, `aud`, `principal`,
`context.cloudId`, `app.id`, `app.apiBaseUrl`, `app.environment.id` — missing
`app.id`/`app.environment.id` fails `403` in `resolveARISegment`.
