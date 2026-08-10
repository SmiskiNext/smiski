# Smiski

An online meeting module embedded in Jira through an Atlassian Forge app.
Meetings are created and managed directly from a Jira issue: start or schedule a
room bound to an issue, join over WebRTC, see participants and history, and
trace every meeting back to the issue it belongs to.

The project grew out of the Zero Meeting System (ZMS) — a standalone meeting
platform — and was refocused into a Jira integration. See
[requirements/ROADMAP.md](requirements/ROADMAP.md) for the migration plan and
[requirements/BA.md](requirements/BA.md) for the business analysis.

**License:** AGPL-3.0

---

## What this module does

- **Meetings from a Jira issue** — start instant rooms or schedule them, bound
  to an issue (`issueKey` / `issueId`).
- **Issue traceability** — list and trace meetings by issue.
- **Video meetings** — audio, video, and screen share powered by LiveKit
  (WebRTC).
- **Participants and history** — participant list and lifecycle history shown
  inside the Jira issue panel and project page.
- **Identity bridge** — reuse the logged-in Jira user; no separate login.

**Out of scope** (per BA): AI summary, chat sync, mobile app, marketplace
listing, full OAuth migration, calendar sync, multi-tenant clustering, and
LiveKit clustering. Recording exists only as an unwired `record` service and is
not part of the shipped build.

---

## Architecture

The Forge app is the only client. Every backend call enters through the API
gateway, which authenticates the Forge Installation Token (FIT) via a Go
authorization service before routing to a Spring service. Locally the gateway is
Envoy with an `ext_authz` sidecar; on Kubernetes it is Kong.

```text
 Jira issue panel / project page (Forge Custom UI)
          │  Forge Remote (FIT + app system token)
          ▼
   API gateway  ── ext_authz ──►  gateway (Go)  ──►  Jira permission check
   (Envoy local / Kong k8s)                          (View / Edit Meeting)
          │
          ├──►  tenant        (Spring · Postgres)      identity & tenancy
          ├──►  meet          (Spring · Postgres)      meeting lifecycle, LiveKit
          └──►  notification  (Spring · Postgres)      email + SSE event streams
                    ▲                    │
                    │  Kafka (CloudEvents 1.0)
                    └────────────────────┘
          │
     LiveKit (WebRTC)  ◄── room token from meet, media direct to client
```

The SSE event-stream and LiveKit webhook routes bypass authorization by design —
see [services/docker/AGENTS.md](services/docker/AGENTS.md) for the full gateway
route table and the resulting security limitations. The local stack is therefore
restricted to local development only.

### Tech stack

| Layer                 | Technology                                            |
| --------------------- | ----------------------------------------------------- |
| Backend services      | Spring Boot 4 / Java 25, hexagonal architecture + DDD |
| Authorization gateway | Go `ext_authz` service (checks FIT against Jira)      |
| API gateway           | Envoy (local) · Kong (Kubernetes)                     |
| Service communication | Kafka (CloudEvents 1.0), gRPC contracts, SSE + Valkey |
| Persistence           | Postgres (Flyway), Valkey                             |
| Real-time media       | LiveKit (WebRTC)                                      |
| Jira integration      | Atlassian Forge app (Custom UI, issue panel + page)   |
| Infrastructure        | Docker Compose (local), Kubernetes / k3s (prod)       |
| API-first             | OpenAPI 3.1 generated from tests, per service         |

---

## Quick start

This gets the backend stack running locally in a few minutes. For the Forge app,
see [app/README.md](app/README.md).

### Prerequisites

- Java 25
- Node.js 22+ and pnpm 10+
- Docker with Compose v2 (Postgres, Kafka, Valkey, LiveKit, Envoy all run in
  containers)
- A host LAN IP reachable from your browser — LiveKit advertises it in ICE
  candidates, and loopback does not work under rootless Docker
- An Atlassian developer site + Forge CLI (only for the Jira integration)

The toolchain is pinned by `.mise.toml` (Java 25, Node, pnpm, gitleaks,
lefthook, buf). Run `mise install` to match it.

### 1. Configure the environment

```bash
cp services/docker/.env.example services/docker/.env
```

Set `SMISKI_HOST_IP` to your host LAN IP (`ip route get 1` or
`ipconfig getifaddr en0`). The other required entries ship with working
placeholders, so the stack boots as-is; swap in real Resend credentials only
when exercising email.

### 2. Build the service images

Compose does **not** build the Java images — it pulls the same artifacts the
release pipeline publishes, so they must exist locally first.

```bash
./services/gradlew -p services/tenant bootBuildImage
./services/gradlew -p services/meet bootBuildImage
./services/gradlew -p services/notification bootBuildImage
```

### 3. Start the stack

```bash
docker compose -f services/docker/compose.yaml up -d
docker compose -f services/docker/compose.yaml ps
```

Everything is reached through the Envoy gateway at `http://localhost:30000`. Add
`--profile observability` to also start Loki, Alloy, Prometheus, and Grafana.

Stop it:

```bash
docker compose -f services/docker/compose.yaml down    # keep data
docker compose -f services/docker/compose.yaml down -v # wipe data
```

See [services/docker/AGENTS.md](services/docker/AGENTS.md) for the host port
map, gateway routes, observability profile, and troubleshooting.

### Running a service natively

For day-to-day coding, run one service with the `dev` profile against the
stack's datastores instead of rebuilding its image:

```bash
docker compose -f services/docker/compose.yaml up -d # datastores + gateway
./services/gradlew -p services/meet bootRun --args='--spring.profiles.active=dev'
```

---

## Project structure

```text
smiski/
├── services/                  # Spring Boot backend + gateway + infra
│   ├── tenant/                # Identity and tenancy
│   ├── meet/                  # Meeting lifecycle, issue link, LiveKit
│   ├── notification/          # Email + SSE event streams
│   ├── record/                # Recording service (unwired, out of scope)
│   ├── gateway/               # Go authorization service (Envoy ext_authz)
│   ├── shared/                # Shared domain types, Result type, test fixtures
│   ├── proto/                 # gRPC + Kafka CloudEvent contracts (buf)
│   ├── docker/                # Local Compose stack + Envoy + observability
│   └── k8s/                   # Kubernetes manifests (Kustomize + Helm)
├── app/                       # Atlassian Forge app (issue panel + project page)
├── sdks/                      # Generated TypeScript SDK (@smiskinext/smiski-ts)
├── requirements/              # BA and roadmap documents
├── openspec/                  # Product specifications and change artifacts
└── build-logic/               # Shared Gradle convention plugins
```

Detailed rules live next to the code: [services/AGENTS.md](services/AGENTS.md)
for the backend and [app/AGENTS.md](app/AGENTS.md) for the Forge app.

---

## Common commands

### Backend services

```bash
./services/gradlew build                            # build + test all services
./services/gradlew -p services/meet build           # build one service
./services/gradlew -p services/meet test            # fast tests (unit + ArchUnit)
./services/gradlew -p services/meet integrationTest # Testcontainers + @SpringBootTest
./services/gradlew spotlessApply                    # format Java / KTS / XML
./services/gradlew bufFormatApply                   # format proto files
```

### OpenAPI generation

Each service emits its own spec to `services/<service>/openapi.yaml` from a
`@SpringBootTest`. The full pipeline then joins them into a single merged
`services/openapi.yaml` and lints every spec with Redocly.

```bash
pnpm run openapi:generate # emit each service's openapi.yaml from tests
pnpm run openapi:join     # merge into services/openapi.yaml
pnpm run openapi:lint     # lint each spec + the merged document
pnpm run openapi          # full pipeline (generate + join + lint)
```

### Root tooling

```bash
pnpm lint   # markdownlint (--fix)
pnpm format # prettier for md/json/yaml/toml/sh/sql
```

---

## Pre-commit and CI

Git hooks run automatically via lefthook (parallel, on staged files):

- `gitleaks protect --staged` — secret scanning
- Spotless — format Java / Kotlin / XML under `services/**`
- Buf — lint and format proto under `services/proto`
- Biome `check --fix` — `scripts/` and `app/`
- Prettier + markdownlint — md / json / yaml / toml / sh elsewhere
- commitlint (`commit-msg`) — conventional commits
- `pre-push` — blocks direct pushes to `main`

---

## Kubernetes deployment

See [services/k8s/deploy.en.md](services/k8s/deploy.en.md) for the full guide:
k3s setup, Helm charts (Kong, LiveKit, Strimzi/Kafka, PLG stack), secret
management, and the dev/prod overlay differences.

---

## API reference

Each service generates its own OpenAPI 3.1 spec at
`services/<service>/openapi.yaml` after `pnpm run openapi`; the merged view
lives at `services/openapi.yaml`.

- `tenant` — identity and tenancy
- `meet` — meetings, invitations, participants, join requests, LiveKit tokens
- `notification` — meeting event streams and inbound email

---

## Contributing

1. Fork the repository and create a branch from `main`.
2. Run `mise install`, `pnpm install`, and `./services/gradlew build` to verify
   your setup.
3. Make your changes following the hexagonal + DDD conventions in
   [services/AGENTS.md](services/AGENTS.md).
4. Ensure hooks pass (`pnpm lint`, `./services/gradlew test`, and the pre-commit
   hooks above).
5. Open a PR targeting `main` with a conventional commit message.

---

## License

GNU Affero General Public License v3.0 (AGPL-3.0) — see [LICENSE](LICENSE) for
details. Copyright 2025 SmiskiNext.
