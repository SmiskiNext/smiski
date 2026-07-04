## Why

The current development environment requires a full Kubernetes cluster
(k3s/minikube) to run all services locally. This adds unnecessary complexity for
day-to-day development — slow startup, resource-heavy, and requires k8s
knowledge to debug. Docker Compose provides a simpler, faster alternative for
local development while k8s remains appropriate for staging and production.

## What Changes

- Rename the existing k8s `overlays/dev` to `overlays/staging` (the k8s dev
  overlay becomes the staging environment)
- Create a new Docker Compose-based development environment under
  `services/docker/`
- Replace Kong API Gateway with Caddy (custom build with caddy-jwt module) for
  the dev environment
- Caddy handles: JWT validation (HS256), X-User-ID/X-User-Email header
  injection, CORS, and LiveKit WebSocket proxying at `/livekit` path
- All infrastructure services run in Docker Compose: Kafka (KRaft mode), Valkey,
  MongoDB (replica set), 2x PostgreSQL, RustFS, LiveKit Server + Redis + Egress
- Application services built via Spring Boot Buildpacks (`bootBuildImage`) — no
  Dockerfiles needed
- Host port range 30000-31000 for exposed services (Caddy gateway on 30000)

## Capabilities

### New Capabilities

- `docker-compose-dev-env`: Docker Compose orchestration for the full
  development stack including Caddy gateway, all application services,
  databases, message broker, cache, object storage, and LiveKit infrastructure

### Modified Capabilities

- `frontend-env-safety`: The `.env.local.example` needs to document the Docker
  Compose dev URLs (port 30000 for API, `ws://localhost:30000/livekit` for
  LiveKit)

## Impact

- `services/k8s/overlays/dev/` — renamed to `services/k8s/overlays/staging/`
- `services/docker/` — new directory with all Docker Compose configuration
- `frontends/web/.env.local.example` — updated with Docker dev URLs
- No application code changes (Java/Kotlin/TypeScript)
- No CI/CD workflow changes
- No k8s prod overlay changes
