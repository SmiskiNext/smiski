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

# Envoy configuration, without starting the stack
docker run --rm -v "$(pwd)/services/docker/envoy:/etc/envoy:ro" \
    envoyproxy/envoy:v1.36-latest --mode validate -c /etc/envoy/envoy.yaml
```
