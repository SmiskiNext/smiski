# Tasks

## 1. Rename k8s dev overlay to staging

- [x] 1.1 Rename `services/k8s/overlays/dev/` directory to
      `services/k8s/overlays/staging/`

## 2. Create Docker directory structure

- [x] 2.1 Create `services/docker/` directory with subdirectories: `caddy/`,
      `livekit/`
- [x] 2.2 Create `services/docker/.env.example` with all required environment
      variables and sensible dev defaults (JWT secret, DB credentials, Kafka,
      LiveKit keys, RustFS keys)

## 3. Custom Caddy image

- [x] 3.1 Create `services/docker/caddy/Dockerfile` that builds Caddy with
      `github.com/ggicci/caddy-jwt` module using xcaddy
- [x] 3.2 Create `services/docker/caddy/Caddyfile` with: public route
      pass-through (auth endpoints, webhook, requestJoin, joinRequests events),
      protected route JWT validation + X-User-ID/X-User-Email injection,
      `/livekit` WebSocket reverse proxy to livekit-server:7880, CORS
      configuration for localhost:3000 and localhost:5173 ← (verify: all
      public/protected routes match design.md section 7, JWT claims correctly
      mapped to headers, WebSocket upgrade works)

## 4. LiveKit configuration

- [x] 4.1 Create `services/docker/livekit/livekit.yaml` — LiveKit server config
      with API keys, Redis address, webhook URL pointing to
      caddy→meeting-management, RTC config for local Docker networking

## 5. Docker Compose file

- [x] 5.1 Create `services/docker/compose.yaml` — infrastructure services:
      user-postgres, meeting-postgres, chat-mongo (replica set), kafka (KRaft),
      valkey, rustfs, livekit-redis
- [x] 5.2 Add LiveKit services to compose: livekit-server (with livekit.yaml
      config mount), livekit-egress (with S3 output to rustfs)
- [x] 5.3 Add application services to compose: user-management,
      meeting-management, chat-management, notification — using bootBuildImage
      images, with environment variables from .env, depends_on with healthchecks
- [x] 5.4 Add Caddy gateway service to compose: custom build from
      `caddy/Dockerfile`, port 30000:80, depends_on application services
- [x] 5.5 Add RustFS init-buckets service (creates `recordings` bucket on
      startup) and expose console on port 30001 ← (verify: all containers start,
      dependency order correct, healthchecks defined for infra services, port
      mapping matches spec)

## 6. Update frontend environment example

- [x] 6.1 Update `frontends/web/.env.local.example` to document Docker Compose
      dev URLs: `http://localhost:30000` for API and
      `ws://localhost:30000/livekit` for LiveKit ← (verify: existing examples
      preserved, new Docker URLs added as documented alternatives)
