# Smiski

An online meeting module embedded in Jira via an Atlassian Forge app. Meetings
are created and tracked directly from a Jira Issue: create a room from an Issue,
join over WebRTC, view participants and history, and trace meetings back to the
Issue they belong to.

This project evolved from the Zero Meeting System (ZMS) — a standalone meeting
platform — into a focused Jira integration. The business brain
(`meeting-management`) is reused; chat, notifications, recording, and the mobile
client are out of scope. See [requirements/ROADMAP.md](requirements/ROADMAP.md)
for the migration plan.

**License:** Apache-2.0

---

## What This Module Does

- **Meetings from a Jira Issue** — create instant or scheduled rooms bound to an
  Issue (`issueKey` / `issueId`)
- **Issue traceability** — list and trace meetings by Issue (UC02, UC07)
- **Video meetings** — audio / video / screen share powered by LiveKit (WebRTC)
- **Participants & history** — participant list and meeting history shown inside
  the Issue panel
- **Identity bridge** — reuse the logged-in Jira user; no separate login

**Out of scope** (per BA): AI summary, recording, chat sync, mobile app, Jira
notifications, marketplace, full OAuth migration, calendar, email invite,
multi-tenant, LiveKit clustering.

---

## Architecture

```text
 Jira Issue Panel (Forge Custom UI)
          │  Forge fetch (+ Jira user token)
          ▼
    Kong Gateway (API Router)
          │
          ├── meeting-management (Spring Boot · Postgres)
          │         ├── gRPC client ──► user-management   (resolve name/avatar)
          │         ├── SSE ──► Valkey (pub/sub, join state)
          │         └── LiveKit (room token, WebRTC)
          └── user-management (Spring Boot · Postgres · gRPC server)
```

The auth bridge (how Forge identity reaches the backend) and the final role of
`user-management` are being decided via a spike — see Phase 2 in the roadmap.

**Tech stack summary:**

| Layer                 | Technology                                          |
| --------------------- | --------------------------------------------------- |
| Backend services      | Spring Boot 4 / Java 25, hexagonal architecture     |
| Service communication | Kong HTTP gateway, gRPC, SSE + Valkey               |
| Persistence           | Postgres (Flyway), Valkey                           |
| Real-time media       | LiveKit (WebRTC)                                    |
| Jira integration      | Atlassian Forge app (Custom UI, issue panel module) |
| Infrastructure        | Docker Compose (local), Kubernetes / k3s            |
| API-first             | OpenAPI 3.1 (generated from tests), per service     |

---

## Quick Start

### Prerequisites

- Java 25
- Node.js 22+
- pnpm 10+
- Docker with Compose v2 (Postgres, Kafka, Valkey, LiveKit, Envoy all run in
  containers)
- A host LAN IP reachable from your browser — LiveKit advertises it in ICE
  candidates, and loopback does not work under rootless Docker
- An Atlassian developer site + Forge CLI (for the Jira integration)

### 1. Configure the environment

```bash
cp services/docker/.env.example services/docker/.env
```

Fill in the two required values: `SMISKI_HOST_IP` (your host LAN IP) and
`CURSOR_SECRET`. Both have no default and block startup when unset.

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

Everything is reached through the Envoy gateway at `http://localhost:30000`. See
[services/docker/README.md](services/docker/README.md) for the host port map,
the gateway route table, and the stack's security limitations — the event-stream
routes are served **unauthenticated**, so the stack is restricted to local
development.

To stop it:

```bash
docker compose -f services/docker/compose.yaml down # add -v to wipe data
```

### Running services natively instead

For day-to-day coding, run a service with the `dev` profile against the stack's
datastores rather than rebuilding its image:

```bash
docker compose -f services/docker/compose.yaml up -d # datastores + gateway
./services/gradlew -p services/meet bootRun --args='--spring.profiles.active=dev'
```

---

## Project Structure

```text
smiski/
├── services/
│   ├── tenant/                # Identity and tenancy
│   ├── meet/                  # Meeting lifecycle, Issue link, LiveKit
│   ├── notification/          # Email + SSE event streams
│   ├── gateway/               # Go authorization service (Envoy ext_authz)
│   ├── shared/                # Shared domain types, Result type, test fixtures
│   ├── proto/                 # gRPC proto definitions
│   ├── docker/                # Local Compose stack + Envoy configuration
│   └── k8s/                   # Kubernetes manifests (Kustomize overlays)
├── app/                       # Atlassian Forge app (Jira issue panel)
├── requirements/              # BA and roadmap documents
├── openspec/                  # Product specifications and change artifacts
└── build-logic/               # Shared Gradle convention plugins
```

---

## Key Build Commands

### Backend services

```bash
./services/gradlew build                          # build all services
./services/gradlew -p services/ < service > build # build one service
./services/gradlew spotlessApply                  # format Java/KTS/XML
./services/gradlew bufFormatApply                 # format proto files
./services/gradlew test                           # run all tests
```

### OpenAPI generation

Each service emits its own spec to `services/<service>/openapi.yaml` via a
`@SpringBootTest`; specs are not merged.

```bash
pnpm run openapi:generate # generate every service's openapi.yaml from tests
pnpm run openapi:lint     # lint each service spec with redocly
pnpm run openapi          # full pipeline (generate + lint)
```

---

## CI / Pre-commit

Git hooks (via lefthook) run automatically on commit:

- `gitleaks` — secret scanning on staged files
- `spotlessApply` — format Java/Kotlin/XML
- `bufFormatApply` — format proto files
- `prettier` — format markdown, JSON, YAML, TOML, shell
- `commitlint` — validate commit messages against conventional commits

Root-level tooling:

```bash
pnpm lint   # markdownlint
pnpm format # prettier
```

---

## Kubernetes Deployment

See [services/k8s/deploy.en.md](services/k8s/deploy.en.md) for the full
deployment guide covering k3s setup, Helm charts (Kong, LiveKit, PLG stack),
secret management, and overlay differences between dev and prod.

---

## API Reference

Each service generates its own OpenAPI spec at `services/<service>/openapi.yaml`
after running `pnpm run openapi`. Specs are validated per service with redocly
and are not merged into a single document.

- `tenant` — identity and tenancy
- `meet` — meetings, invitations, participants, join requests
- `notification` — meeting event streams and inbound email

---

## Contributing

1. Fork the repository and create a branch from `main`
2. Run `pnpm install` and `./services/gradlew build` to verify your setup
3. Make your changes following the code conventions (hexagonal architecture for
   services)
4. Ensure all hooks pass (`pnpm lint`, `./services/gradlew test`, etc.)
5. Open a PR targeting `main` with a conventional commit message

---

## License

Apache-2.0 — see [LICENSE](LICENSE) for details.
