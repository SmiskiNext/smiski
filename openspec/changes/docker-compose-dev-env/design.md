## Context

The system currently runs all services (application + infrastructure) on
Kubernetes for both development and production, using Kustomize overlays (`dev`
and `prod`). The dev overlay uses k3s/minikube locally, which requires:

- A running k8s cluster with Strimzi operator (Kafka), Kong Gateway operator,
  and LiveKit Helm charts
- Knowledge of kubectl, kustomize, and Helm for debugging
- Significant memory/CPU overhead from k8s control plane components

The application consists of 4 Spring Boot services (user-management,
meeting-management, chat-management, notification), backed by PostgreSQL (x2),
MongoDB, Kafka, Valkey (Redis-compatible), RustFS (S3-compatible), and LiveKit
(server + egress + dedicated Redis). Kong Gateway handles API routing, JWT
validation, header injection, rate limiting, and CORS.

## Goals / Non-Goals

**Goals:**

- Provide a single `docker compose up` command to start the entire dev stack
- Replace Kong with Caddy as the API gateway for dev (lighter, simpler config)
- Caddy must replicate Kong's JWT validation + X-User-ID/X-User-Email header
  injection behavior so downstream services work unchanged
- Proxy LiveKit WebSocket connections through Caddy at `/livekit` path
- Include LiveKit Egress for recording flow testing
- Rename k8s `overlays/dev` to `overlays/staging` to reflect its new role
- Use port range 30000-31000 for all host-exposed services

**Non-Goals:**

- Changing any application code (Java/Kotlin/TypeScript)
- Modifying k8s prod overlay
- Modifying CI/CD workflows
- Adding observability stack (PLG) to Docker dev env
- Supporting HTTPS in dev (HTTP on port 30000 is sufficient)
- Rate limiting in dev environment

## Decisions

### 1. Caddy with caddy-jwt module for JWT validation

**Choice**: Custom Caddy image built with `github.com/ggicci/caddy-jwt` module
via xcaddy.

**Alternatives considered**:

- (A) No JWT at gateway, let services handle it — would require changing
  meeting-management and chat-management which trust X-User-ID header from
  gateway
- (B) Use Kong in Docker — heavier, requires separate database, more complex
  config
- (C) Use Traefik with JWT middleware — similar complexity to Caddy but less
  elegant config

**Rationale**: Caddy + caddy-jwt is the lightest option that preserves the
existing security contract. meeting-management's `HeaderAuthFilter` reads
X-User-ID without re-validating JWT — this MUST continue working.

### 2. LiveKit WebSocket proxy at `/livekit` path

**Choice**: Caddy reverse-proxies `/livekit/*` to `livekit-server:7880` with
WebSocket upgrade support.

**Rationale**: Consolidates all traffic through a single gateway port (30000).
Frontend changes `NEXT_PUBLIC_LIVEKIT_URL` from `ws://localhost:7880` to
`ws://localhost:30000/livekit`. LiveKit SDK supports path-based URLs.

### 3. Apache Kafka in KRaft mode (single node)

**Choice**: `apache/kafka:latest` image with KRaft (no Zookeeper).

**Alternatives considered**:

- Confluent Platform — heavier, more images
- Redpanda — good but different wire protocol edge cases

**Rationale**: Official Apache image, minimal footprint, KRaft eliminates
Zookeeper dependency. Single node is sufficient for dev.

### 4. Spring Boot Buildpacks (bootBuildImage)

**Choice**: Use existing `bootBuildImage` Gradle task (already configured in
`build-logic/src/main/kotlin/io.github.phunguy65.zms.plugin.service.base.gradle.kts:109-116`).

**Rationale**: Already configured with image naming
`ghcr.io/phunguy65/zms/${project.name}:${project.version}`. No Dockerfiles
needed. Produces optimized layered images.

### 5. Port allocation scheme

| Port  | Service              |
| ----- | -------------------- |
| 30000 | Caddy (HTTP gateway) |
| 30001 | RustFS console       |
| 30002 | Reserved (future)    |

Internal services (databases, Kafka, Valkey, app services) are NOT exposed to
host — only reachable within Docker network.

### 6. MongoDB replica set initialization

**Choice**: Use the same `postStart` lifecycle approach from k8s — a shell
script that waits for mongod and initiates `rs0` replica set.

**Rationale**: chat-management requires MongoDB change streams which need a
replica set. Single-node RS is the standard dev approach.

### 7. Caddy public vs protected route split

Public routes (no JWT validation):

- `/api/v1/auth/register`
- `/api/v1/auth/login`
- `/api/v1/auth/google-login`
- `/api/v1/auth/refresh`
- `/api/v1/auth/logout`
- `/api/v1/auth/forgot-password`
- `/api/v1/auth/reset-password`
- `/api/v1/webhook/livekit`
- `/api/*/meetings/*:requestJoin`
- `/api/*/joinRequests/*/events`

Protected routes (JWT validated, X-User-ID injected):

- `/api/v1/users/*`
- `/api/v1/me/*`
- `/api/v1/meetings/*`
- `/api/v1/chat/*`

### 8. Docker Compose file location

**Choice**: `services/docker/compose.yaml` with supporting files in
`services/docker/caddy/` and `services/docker/livekit/`.

**Rationale**: Keeps Docker config alongside k8s config under `services/`.
Follows the existing pattern where infra config lives in `services/`.

## Risks / Trade-offs

- **[caddy-jwt is a community module]** → Mitigation: Pin to a specific version
  in the xcaddy build. The module is well-maintained and HS256 validation is
  straightforward.
- **[bootBuildImage requires Docker daemon]** → Mitigation: Docker is already
  required for the compose environment. Document the build step clearly.
- **[LiveKit path-based proxy may have edge cases with TURN/ICE]** → Mitigation:
  LiveKit SDK handles path-based URLs. For local dev (same network), TURN is not
  needed. ICE candidates use direct UDP which bypasses the HTTP proxy.
- **[MongoDB replica set init race condition]** → Mitigation: Use healthcheck +
  depends_on with condition to ensure mongo is ready before app services start.
- **[Large compose file with many services]** → Mitigation: Use Docker Compose
  profiles to allow starting subsets (e.g., only infra, or infra + specific
  service for active development).
