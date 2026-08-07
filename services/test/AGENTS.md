# Load-test harness stack

Measurement harness for the WebRTC meeting stack: TC-01 through TC-05, with the
acceptance thresholds the assignment states. It is a thin **overlay** on
`services/docker/compose.yaml` plus a TURN server, a mock Jira, a browser QoS
page and the `smiski test` CLI.

> [!WARNING]
>
> **Local development only. Do not expose this stack to a network.**
>
> It inherits and then widens the development stack's warning. Two additions are
> deliberately insecure: Envoy accepts **Forge Invocation Tokens signed by a key
> on this machine**, so anyone who can reach port 30000 can mint any identity;
> and `mock-jira` **grants every permission it is asked for**. The generated
> private key under `keys/` must never be committed.

## What differs from the development stack

`compose.yaml` here contains no copied service definitions. It declares
`name: smiski-test` and `include: ../docker/compose.yaml`, so the test stack
tracks the development stack automatically instead of drifting from a duplicate.
`services/docker/` is read-only for this work.

| Addition                   | Why it exists                                               |
| -------------------------- | ----------------------------------------------------------- |
| `coturn`                   | TURN relay for TC-01, independently measurable              |
| `mock-jira`                | Removes the gateway's 2 s Jira timeout from TC-04           |
| `harness`                  | Browser QoS page for TC-01/TC-02, outside the Forge app     |
| `livekit-redis-exporter`   | Makes the **second** cache instance a separate target       |
| `envoy/envoy.yaml`         | `local_jwks` instead of Atlassian's `remote_jwks`           |
| `observability/*`          | Scrape jobs for LiveKit, Coturn, `livekit-redis`            |
| pinned network + `node_ip` | Fixes relay traversal — see [The relay fix](#the-relay-fix) |

Volumes and networks are prefixed separately (`smiski-test_valkey-data` vs
`docker_valkey-data`), so the two stacks never share data.

> [!IMPORTANT]
>
> **Host ports are inherited unchanged**, and they are hardcoded in the included
> file (30000, 9901, 8281-8284, 9094, 7880-7882, 3000, 9090, 3100, 12345). The
> two stacks cannot run simultaneously as written even though their data is
> fully separate. Stop one before starting the other, or add `ports: !override`
> entries in a local file that is not committed.

## Startup

```sh
# 1. Environment. BOTH .env files load; services/test/.env wins on a shared key,
#    so this file only carries deltas. SMISKI_HOST_IP and CURSOR_SECRET are
#    declared with `:?` upstream and must exist in one of the two.
cp services/test/.env.example services/test/.env

# 2. Key material. Envoy reads the JWKS at startup AND at --mode validate time,
#    so this must run before either. A missing file is a hard failure.
pnpm --dir scripts smiski test keygen

# 3. Java images. Compose only pulls them — see the rebuild warning below.
./services/gradlew -p services/tenant bootBuildImage
./services/gradlew -p services/meet bootBuildImage
./services/gradlew -p services/notification bootBuildImage

# 4. Start. The observability profile is REQUIRED for TC-05 and for the
#    server-side half of TC-02 and TC-03.
COMPOSE_PROFILES=observability docker compose -f services/test/compose.yaml up -d

# 5. Fixtures. Prints the meeting identifiers every later command consumes.
pnpm --dir scripts smiski test seed
```

Teardown:

```sh
docker compose -f services/test/compose.yaml down    # stop, keep volumes
docker compose -f services/test/compose.yaml down -v # stop and wipe data
```

Results land in `services/test/results/`, which is **gitignored** — every file
there is an artifact reproduced by re-running the harness, not source.

## Preconditions that yield missing data rather than an error

These are the traps. Each one leaves the stack looking healthy while the numbers
you came for are absent or wrong. All were observed on this host, not inferred.

### Stale Java images

> [!IMPORTANT]
>
> **Rebuild the three Java images, or `spring-services` reports down.** Compose
> runs them with `-Dspring.aot.enabled=true`, and AOT evaluates
> `@ConditionalOnAvailableEndpoint` at build time, so `/actuator/prometheus` is
> compiled into the image. No environment variable can add it afterwards.
> **Symptom:** the stack serves requests normally and the `spring-services`
> targets simply read `down` on `/targets`. TC-04's service-side series are
> empty; nothing errors.

### The harness page needs a secure context

> [!IMPORTANT]
>
> **Open the harness page at `http://localhost:8090`, never at a LAN address.**
> Browsers expose `navigator.mediaDevices` only in a secure context.
> `http://localhost` counts as secure; `http://192.168.x.x:8090` does not.
> **Symptom:** the page loads, connects, and samples happily — but cannot
> capture, so `uplink_kbps` is 0 for the whole session and screen share fails
> with `Cannot read properties of undefined (reading 'getDisplayMedia')`. The
> page detects this case and says so in its status line, which is the only
> warning you get.

### Adaptive streaming suppresses inbound statistics

> [!IMPORTANT]
>
> **Do not enable `adaptiveStream`.** With it on, the client subscribes only to
> tracks whose video element it judges visible, so a background tab or an
> unsized element leaves `isSubscribed` false. **Symptom:** no `inbound-rtp` is
> produced at all, so `jitter_ms`, `packet_loss_percent` and `downlink_kbps`
> export **blank** on a connection that looks perfectly healthy — TC-02 loses
> its entire downlink half. `harness.js` sets `adaptiveStream: false` and
> `dynacast: false` for exactly this reason; leave them off.

### And three more, each silent in its own way

- **Something must be publishing before downlink statistics exist.** A lone
  harness client in an empty room exports blank jitter and loss even when
  everything is configured correctly — there is simply no inbound media.
  Measured: the same page read `jitter —, packetLoss —` alone and
  `jitter 0.00 ms, packetLoss 0.91 %, downlink 13679 kbps` with a populated
  room. Start `loadtest room`, or a second client, first.
- **`${VAR:-default}` is inert for a key already in the development `.env`.**
  `JIRA_API_BASE` is set there, so a `:-` default never applied and the gateway
  called the real Atlassian API while the mock sat idle. **Symptom:** pure
  latency in the exact figure TC-04 measures — a 2 s timeout, then an empty
  permission set, no error. The value is written literally in `compose.yaml`;
  redirect the mock by editing that line, not the `.env`.
- **Prometheus does not expand environment variables, yet
  `promtool check config` passes anyway.** A templated port yields
  `too many colons in address` only at scrape time. Ports in
  `observability/prometheus.yml` are literal, so changing
  `LIVEKIT_PROMETHEUS_PORT` means editing **both** files.

> [!NOTE]
>
> The common thread: **file validation cannot detect any of these.** After any
> observability change, read `/targets` on <http://localhost:9090> and confirm
> every job reports `up`. A valid configuration file does not imply a reachable
> target — that mistake has already been made once here.

## The relay fix

TC-01 fails without this, and the failure is silent on both sides.

`services/docker/compose.yaml` starts LiveKit with
`--node-ip ${SMISKI_HOST_IP}`, so it advertises the **host LAN address** in its
ICE candidates while its real egress toward Coturn is its **container address**.
Coturn installs the peer permission for the advertised address, so the
connectivity check arrives from an unpermitted source and is discarded. Measured
before the fix: `requestsSent: 8, responsesReceived: 0, state: failed`, with no
error logged anywhere. The only symptom is a candidate pair that never
completes.

The overlay fixes it in three coupled parts, all in `compose.yaml`:

| Part                                           | Purpose                                       |
| ---------------------------------------------- | --------------------------------------------- |
| `networks.default.ipam` `10.77.0.0/24`         | Makes a static address assignable             |
| `livekit-server.networks.default.ipv4_address` | Pins it to `10.77.0.10`                       |
| `command: !override` without `--node-ip`       | Lets configuration win                        |
| `rtc.node_ip: 10.77.0.10`                      | Advertises the address it actually sends from |

Dropping the flag is **required, not cosmetic**: measured on `livekit-server`
v1.9.12, the CLI flag beats the configuration file, so `--node-ip` wins over
`rtc.node_ip` whenever both are present.

`ip_range: 10.77.0.128/25` confines dynamic allocation to the upper half. Docker
assigns addresses upward from the start of a subnet, so without it the static
`.10` races the other ~20 containers. Measured: `notification-postgres` took
`10.77.0.10` first and LiveKit then failed with
`failed to set up container networking: Address already in use` — loud, but
dependent on startup order.

> [!NOTE]
>
> **Consequence.** The pinned address is reachable from inside the stack but not
> from the host, so a browser cannot form a direct pair with the media server.
> **Every** harness connection is relayed whether or not the relay-only toggle
> is set. That is what makes TC-01 pass, and it means a run with the toggle
> **off** is not evidence of direct connectivity. Read `local_candidate_type` in
> the export rather than assuming from the toggle.

## Running each case

`smiski` is `pnpm --dir scripts smiski`. Every command takes `--help`.

Two headers are required on any hand-rolled request, and both fail unhelpfully
when missing: `x-issue-id: 10001` and
`x-forge-oauth-system: loadtest-system-token` (omitting the latter yields
`500 {"error":"configuration_error","message":"Missing system token"}`). The
request body needs both `displayName` and `deviceId`; omitting `deviceId` is a
`400` naming the field.

### TC-01 — NAT traversal via TURN (< 3 s setup)

Preconditions: stack up, `keygen` and `seed` done, a meeting id to hand.

1. `smiski test impair blocked-udp --service livekit-server`
2. Join through the gateway to obtain a LiveKit token:

    ```sh
    curl -s -X POST "http://localhost:30000/api/1/meetings/<MEETING_ID>:join" \
        -H "Authorization: Bearer $(pnpm -s --dir scripts smiski test token)" \
        -H 'Content-Type: application/json' -H 'x-issue-id: 10001' \
        -H 'x-forge-oauth-system: loadtest-system-token' \
        -d '{"displayName":"Relay","deviceId":"d1"}'
    ```

3. **Manual:** open <http://localhost:8090>, paste the token, set the server URL
   to `ws://localhost:7880`, **tick relay-only**, connect.
4. Read the setup time from the status line; export the CSV.
5. `smiski test impair lan --service livekit-server` to restore baseline.

Evidence: `relay proven,true` and `call setup within 3000 ms budget,true` in the
export header, plus `turn_total_allocations` and `turn_total_traffic_sentb` from
Coturn. A successful connection alone is **not** relay evidence — the candidate
type is.

### TC-02 — Media QoS over 15 minutes

Preconditions: TC-01's steps, plus **a second publisher** and the `4g` profile.

1. `smiski test impair 4g --service livekit-server` — mandatory. On an
   unimpaired local stack latency is ~0.04 ms, so a "< 200 ms" result carries no
   information and must not be presented as evidence of behaviour under a
   degraded network.
2. Start publishers: `smiski test loadtest room --room "$ROOM" --duration 16m`
3. **Manual:** join from the harness page and leave it sampling for the full 15
   minutes. Do not reload — samples live in the page, and the beforeunload guard
   is the only protection.
4. Export, then `smiski test impair 4g --service livekit-server --remove`.
5. `smiski test collect --cases tc02 --network-profile 4g`

### TC-03 — Room capacity, 30 participants

```sh
smiski test loadtest room --room "$ROOM" --duration 3m \
    --video-publishers 10 --subscribers 19 --video-resolution high
```

**Manual leg:** `lk load-test` hardcodes `TrackSource_CAMERA` and _cannot_
publish a screen share. Join the same room from the harness page and use the
screen-share button; its start/stop timestamps go into the export header so the
interval can be aligned with `livekit_track_publish_counter`.

The `lk` summary table is recorded but **not** used for threshold assertions:
its Latency column is tester-side arrival timing and it reports no jitter. Take
jitter, loss and RTT from LiveKit's `/metrics` and the harness export.

### TC-04 — Token issuance, 500 requests in 1 s

```sh
# TC-04a: token throughput — all four passes
smiski test loadtest tokens --admission-policy ALLOW_ALL
# TC-04b: cached approval state
smiski test loadtest tokens --admission-policy MANUAL_APPROVAL \
    --variants single --cache-states warm
```

The 500 ms threshold is asserted **only** against the warm-cache sharded pass.
The single-room tail measures the pessimistic row lock, not throughput — see the
first code-derived divergence below.

TC-04b's own criterion is the cached approval state, which the response cannot
show: under `MANUAL_APPROVAL` every response carries `token: null`. Read it from
Redis **immediately after the run** — the keys carry a TTL, and any later
`FLUSHALL` (the isolation checks below use one) destroys the evidence:

```sh
docker exec smiski-test-valkey-1 valkey-cli --scan --count 500 | rg join_request
```

Measured on this stack: 500 requests produced 477 `PENDING` rows, 487
`join_request_meta` keys and 487 `join_request_device` keys, with 23 requests
counted as `http 5xx` failures rather than averaged into the timings.

### TC-05 — Container resources

Runs alongside TC-03 and TC-04; needs the `observability` profile.

```sh
smiski test collect --cases tc05 --since 30
```

The two cache instances are separate jobs (`valkey`, `livekit-redis`), so the
256 MB threshold can be applied to the intended one. The load generator has its
own cAdvisor series and is subtractable from host figures rather than silently
counted as system-under-test usage.

## Which steps are manual

| Step                                       | Scripted | Manual |
| ------------------------------------------ | :------: | :----: |
| Key generation, token signing, seeding     |    ✅    |        |
| Impairment apply / remove / inspect        |    ✅    |        |
| TC-03 room load, TC-04 token load          |    ✅    |        |
| Metric collection to CSV                   |    ✅    |        |
| Joining from a browser, reading setup time |          |   ✅   |
| Screen share start/stop                    |          |   ✅   |
| Holding TC-02 for its full 15 minutes      |          |   ✅   |
| Judging results against thresholds         |          |   ✅   |

Full automation was never a goal; combining scripted and operator steps is
explicitly acceptable.

## Divergences from the test plan, and their resolutions

The assignment describes a system this repository is not. Each mismatch is
resolved by a stated decision so no one silently reinterprets it.

| #   | Assignment states                     | Repository reality                                                                                        | Resolution                                                      |
| --- | ------------------------------------- | --------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------- |
| 1   | Backend is NestJS                     | Spring Boot 4 / Java 25, service `meet`                                                                   | TC-04 targets the Spring `meet` service                         |
| 2   | A Coturn container is monitored       | No Coturn anywhere; no `turn:` block, no `rtc.turn_servers`                                               | Added a standalone Coturn container to this stack               |
| 3   | "the Redis container"                 | **Two**: `valkey` (application) and `livekit-redis` (LiveKit-internal, uninstrumented)                    | TC-05 measures both, reported as separate jobs                  |
| 4   | Metrics come from a LiveKit dashboard | LiveKit exposes none: `prometheus_port` unset, no `livekit` scrape job                                    | `prometheus.port` enabled; scrape jobs added here               |
| 5   | Meeting sync rate to the PM system    | `meet` never writes to Jira; it stores `issue_id`/`issue_key` locally, and only the Go gateway reads Jira | Redefined as outbox drain latency + `participation_logs` writes |
| 6   | Kafka lag as an integration metric    | `apache/kafka:4.1.0` ships no JMX exporter, deliberately excluded from Prometheus                         | Drain measured via the `outbox_events` table instead            |

Four further divergences emerged from the code rather than from the brief:

1. **TC-04's two success criteria cannot both hold in one run.** Under
   `ALLOW_ALL` every request returns a token but touches no Redis; under
   `MANUAL_APPROVAL` Redis is written but the response carries `token: null`. It
   is therefore split into TC-04a and TC-04b. Separately,
   `RequestJoinApplicationService` holds a `PESSIMISTIC_WRITE` lock for the
   whole transaction, so 500 joins against one meeting measure lock queueing
   rather than throughput — hence the single/sharded variants. Measured here:
   median 253.5 ms single-room cold versus 3.9 ms sharded warm, same offered
   load.
2. **`lk load-test` cannot publish a screen share** — hardcoded
   `TrackSource_CAMERA`. That leg of TC-03 is manual, timestamped into the
   harness export.
3. **A real Forge Invocation Token cannot be minted locally** — Atlassian signs
   it. Envoy's `remote_jwks` is swapped for `local_jwks` reading a generated key
   file; everything else in the chain (RS256 verification, the Lua claim filter,
   the ext_authz gRPC hop, the gateway's Valkey cache) stays in the measured
   path. No mock JWKS service is needed.
4. **The Forge app cannot be used as the QoS client.** It builds its `Room` with
   no way to inject `rtcConfig`, and the relay-only toggle _is_ an `rtcConfig`
   override. A standalone page keeps the production application untouched.

## Token claim shape

`smiski test token` derives its claims from
`services/gateway/internal/fit/parser.go`. All of `iss`, `aud`, `principal`,
`context.cloudId`, `app.id`, `app.apiBaseUrl` and `app.environment.id` are
required; a missing `app.id` or `app.environment.id` fails in
`resolveARISegment` with a `403` naming the claim.

> [!WARNING]
>
> **`--cloud-id` must equal the seeded `meetings.tenant_id`.** A mismatch is not
> an authentication failure — it passes authentication, then Hibernate's
> `@TenantId` filter yields `404 MEETING_NOT_FOUND`, which reads like a missing
> meeting rather than a wrong token. `smiski test seed` prints the tenant it
> used for this reason.

## Validating configuration changes

```sh
# Compose definition, both ways — a profile-gated service is NOT validated
# while its profile is inactive, so the plain form alone leaves half unchecked.
docker compose -f services/test/compose.yaml config
COMPOSE_PROFILES=observability docker compose -f services/test/compose.yaml config

# Envoy. Requires keygen to have run: local_jwks is read at validate time, and
# the key set must be mounted where envoy.yaml names it, not merely exist.
# envoy.yaml is mounted as a FILE, not by mounting its directory: a read-only
# directory mount at /etc/envoy blocks the two nested mounts below it with
# `create mountpoint ... Read-only file system`.
docker run --rm \
    -v "$(pwd)/services/test/envoy/envoy.yaml:/etc/envoy/envoy.yaml:ro" \
    -v "$(pwd)/services/docker/envoy/lua:/etc/envoy/lua:ro" \
    -v "$(pwd)/services/test/keys:/etc/envoy/keys:ro" \
    envoyproxy/envoy:v1.36-latest --mode validate -c /etc/envoy/envoy.yaml

# Prometheus scrape configuration
docker run --rm -v "$(pwd)/services/test/observability:/w:ro" \
    --entrypoint promtool prom/prometheus:v3.13.2 check config /w/prometheus.yml

# CLI
pnpm --dir scripts lint && pnpm --dir scripts typecheck
```

Then, on a **running** stack, confirm every scrape target reports `up`:

```sh
curl -s 'http://localhost:9090/api/v1/targets?state=active' \
    | python3 -c 'import json,sys; [print(t["labels"]["job"], t["health"]) for t in json.load(sys.stdin)["data"]["activeTargets"]]'
```
