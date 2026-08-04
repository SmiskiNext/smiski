# Local development stack

Docker Compose topology for the Smiski backend: the three Spring services, the
Go authorization service, the Envoy gateway, and every datastore they need.

> [!WARNING]
>
> **Local development only. Do not expose this stack to a network.**
>
> The meeting event-stream routes are served **without authentication**. Forge
> cannot authenticate them today: `requestRemote` does not support response body
> streaming, and `EventSource` cannot set an `Authorization` header, so the UI
> opens streams with a plain browser `fetch` carrying no token.

## Prerequisites

- Docker with Compose v2
- A host LAN IP reachable from your browser — LiveKit advertises it in ICE
  candidates, and loopback does not work under rootless Docker
- The three Java service images, built before the stack starts (compose does not
  build them)

## Startup

```sh
# 1. Environment: set SMISKI_HOST_IP to your host LAN IP. The other REQUIRED
#    entries ship with working placeholders, so the stack boots as-is; swap in
#    real Resend credentials only when exercising email or inbound webhooks.
cp services/docker/.env.example services/docker/.env

# 2. Build the Java images (required — compose only pulls them)
./services/gradlew -p services/tenant bootBuildImage
./services/gradlew -p services/meet bootBuildImage
./services/gradlew -p services/notification bootBuildImage

# 3. Start the stack (builds the Go gateway from source)
docker compose -f services/docker/compose.yaml up -d
```

Teardown:

```sh
docker compose -f services/docker/compose.yaml down    # stop, keep volumes
docker compose -f services/docker/compose.yaml down -v # stop and wipe data
```

Inspect:

```sh
docker compose -f services/docker/compose.yaml ps
docker compose -f services/docker/compose.yaml logs -f envoy
docker compose -f services/docker/compose.yaml config # validate definition
```

A missing Java image fails immediately with a pull error naming the image,
rather than starting partially and failing at request time.

## Observability (opt-in)

Logs, metrics and dashboards are off by default. `docker compose up -d` starts
the same containers it always has; enabling the profile adds eight more and
roughly 800 MB of RAM.

```sh
# Loki, Alloy, Prometheus, Grafana + four infrastructure exporters
docker compose -f services/docker/compose.yaml --profile observability up -d
```

> [!IMPORTANT]
>
> **Rebuild the three Java images first, or metrics and JSON logs will not
> appear.** Compose runs them with `-Dspring.aot.enabled=true`, and AOT
> evaluates `@ConditionalOnAvailableEndpoint` at build time, so
> `/actuator/prometheus` is compiled into the image — no environment variable
> can add it to an image built before this change:
>
> ```sh
> ./services/gradlew -p services/tenant bootBuildImage
> ./services/gradlew -p services/meet bootBuildImage
> ./services/gradlew -p services/notification bootBuildImage
> ```
>
> An operator running stale images gets today's behaviour, not an error: the
> `spring-services` targets simply report down on Prometheus's `/targets` page.

Grafana is on <http://localhost:3000> with both datasources already provisioned;
sign in with `GRAFANA_ADMIN_USER` / `GRAFANA_ADMIN_PASSWORD` from `.env`
(`admin` / `admin` by default). Turn it all off again by omitting the flag — no
file edit is needed.

Nothing in the profile is a dependency of any application container, so a failed
or stopped observability component never stops the stack serving requests.

### Per-container CPU and memory: two host paths

Alloy runs cAdvisor in-process rather than as a separate container. To attribute
usage to a container it joins two host sources, both mounted read-only:

| Path              | Supplies                        | Override               |
| ----------------- | ------------------------------- | ---------------------- |
| `/sys/fs/cgroup`  | kernel accounting — the numbers | `CADVISOR_CGROUP_ROOT` |
| `/var/lib/docker` | Docker state — the names        | `CADVISOR_DOCKER_ROOT` |

Measured on Docker Engine: the socket alone yields one series, for Alloy's own
cgroup. Adding cgroups yields every container but with no `name` label, so
nothing can be attributed. Both together is what produces named metrics. Neither
needs write access and no `privileged` flag is involved.

The container-side path of the Docker root is fixed at `/var/lib/docker` because
cAdvisor's root is not configurable through this component; only the host side
is overridable.

> [!NOTE]
>
> **Docker Desktop (Windows, macOS).** The daemon runs inside a VM, so neither
> path exists on the host and there is nothing to point the overrides at. The
> rest of the profile is unaffected — logs, service metrics, Envoy, Postgres and
> Valkey all work — but the per-container CPU and memory panels stay empty.
> Comment the two mounts out of the `alloy` service to avoid a bind-mount error
> on startup.

`loki` runs a distroless image whose only executable is `/usr/bin/loki` — no
shell, no `wget`, no `curl` — so it declares no container healthcheck. Readiness
is observed from the host instead. Expect this to fail for the first half minute
or so — the ingester reports `Ingester not ready` until its ring stabilises:

```sh
curl -fsS http://localhost:3100/ready
```

Kafka and the Go `gateway` are deliberately absent from the metrics targets:
`apache/kafka:4.1.0` bundles no JMX exporter, and `gateway` carries no
instrumentation yet.

## Host port map

| Port     | Component               | Purpose                                |
| -------- | ----------------------- | -------------------------------------- |
| 30000    | `envoy`                 | API gateway — the only app entry point |
| 9901     | `envoy`                 | Admin: `/ready`, `/stats`, config dump |
| 8281     | `tenant-postgres`       | Postgres, database `tenant`            |
| 8282     | `meet-postgres`         | Postgres, database `meet`              |
| 8283     | `notification-postgres` | Postgres, database `notification`      |
| 8284     | `valkey`                | Application cache and gateway cache    |
| 9094     | `kafka`                 | Host listener (`PLAINTEXT_HOST`)       |
| 7880     | `livekit-server`        | Signalling (HTTP / WebSocket)          |
| 7881     | `livekit-server`        | ICE / TCP                              |
| 7882/udp | `livekit-server`        | ICE / UDP mux                          |
| 3000     | `grafana`               | Dashboards and Explore (observability) |
| 9090     | `prometheus`            | Target health at `/targets`            |
| 3100     | `loki`                  | Log push and query API                 |
| 12345    | `alloy`                 | Log collector component health UI      |

The last four start only under the `observability` profile. Their four exporters
— `tenant-postgres-exporter`, `meet-postgres-exporter`,
`notification-postgres-exporter` and `valkey-exporter` — publish nothing; only
Prometheus reads them, over the stack network.

Not published: `gateway` (gRPC 9001) and `livekit-redis` are internal only. The
three Spring services are reachable only through Envoy on 30000; inside the
network they listen on 8080, which is what `envoy/envoy.yaml` resolves.

LiveKit's media ports are published directly rather than proxied: WebRTC media
is not HTTP and cannot traverse an HTTP gateway.

Kafka exposes two listeners so both run modes work — containers use `kafka:9092`
(the default profile), natively run services use `localhost:9094` (the `dev`
profile, whose Postgres ports match the table above).

## Gateway route table

Envoy evaluates routes in order and the **first match wins**. Ordering is
load-bearing: `notification` owns two paths inside `meet`'s `/meetings` path
space, so the more specific routes must come first.

| #   | Match                                                         | Service        | Auth                |
| --- | ------------------------------------------------------------- | -------------- | ------------------- |
| 1   | exact `/api/1/webhooks/livekit`                               | `meet`         | **none** (bypassed) |
| 2   | exact `/api/1/webhooks/resend/inbound`                        | `notification` | **none** (bypassed) |
| 3   | regex `^/api/1/meetings/[^/]+(/join-requests/[^/]+)?/events$` | `notification` | **none** (bypassed) |
| 4   | prefix `/api/1/issues`                                        | `meet`         | FIT + authorization |
| 5   | prefix `/api/1/tenants`                                       | `tenant`       | FIT + authorization |
| 6   | prefix `/api/1/meetings`                                      | `meet`         | FIT + authorization |

Any path matching no route is rejected without reaching a backend. .

## Startup ordering and readiness

Verify Envoy itself is up:

```sh
curl -fsS http://localhost:9901/ready
```

## Validating configuration changes

```sh
# Compose definition
docker compose -f services/docker/compose.yaml config

# Compose definition with the observability profile requested
COMPOSE_PROFILES=observability \
    docker compose -f services/docker/compose.yaml config

# Envoy configuration, without starting the stack
docker run --rm -v "$(pwd)/services/docker/envoy:/etc/envoy:ro" \
    envoyproxy/envoy:v1.36-latest --mode validate -c /etc/envoy/envoy.yaml

# Prometheus scrape configuration
docker run --rm -v "$(pwd)/services/docker/observability:/w:ro" \
    --entrypoint promtool prom/prometheus:v3.13.2 check config /w/prometheus.yml

# Alloy configuration
docker run --rm -v "$(pwd)/services/docker/observability:/w:ro" \
    grafana/alloy:v1.18.0 validate /w/config.alloy

# Loki configuration
docker run --rm -v "$(pwd)/services/docker/observability:/w:ro" \
    grafana/loki:3.7.4 -config.file=/w/loki.yaml -verify-config
```

Each command exits non-zero and names the offending field on a syntax or schema
error, so a bad edit is caught before the stack is started.
