# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with
code in this repository.

## Architecture Overview

Monorepo with mixed stack: Java/Spring microservices + Next.js web app.
API-first: backend services generate OpenAPI specs, a root script merges them
into `openapi/unified-openapi.yaml`, and clients are generated for the web.

**Components:**

- `services/` — Spring Boot 4 / Java 25 backend microservices (hexagonal
  architecture)
- `frontends/web/` — Next.js 16 / React 19 web client
- `services/k8s/` — Kubernetes manifests (Kong gateway, Kafka, DBs, LiveKit,
  Valkey)
- `openspec/` — Product specs and change artifacts
- `build-logic/` — Shared Gradle convention plugins

**Backend services** (`user-management`, `meeting-management`,
`chat-management`, `notification`, `proto`, `shared`): each follows
`domain → application → infrastructure → presentation` layering.

**Service communication:**

- External: Kong API gateway routes to each service
- Sync: `meeting-management` → `user-management` via gRPC
- Async: Kafka + CloudEvents for domain events (publishers in user/meeting,
  consumers in chat/notification)
- Real-time: SSE in `meeting-management` (`MeetingSseManager`), backed by Kafka
  and Redis

**Persistence:**

- Postgres + Flyway: `user-management`, `meeting-management`
- MongoDB: `chat-management`
- Redis/Valkey: join-request state in `meeting-management`

**Key integrations:** LiveKit (video), Firebase (auth/storage), Resend (email
notifications)

## Build Commands

### Convenience CLI (`pnpm smiski`)

A TypeScript CLI under `scripts/` (citty + tsx + zx) wraps the common dev
workflows. Prefer it over invoking `gradlew` / `pnpm --dir` directly.

```sh
pnpm smiski --help                       # list all groups
pnpm smiski setup                        # all-in-one bootstrap (mise tools + pnpm deps + git hooks + .env)
pnpm smiski setup --env-only             # only copy services/docker/.env from .env.example
pnpm smiski doctor                       # report tool status (mise, java, node, docker, ...)
pnpm smiski dev                          # infra up + 4 backend services in parallel
pnpm smiski infra <up|down|reset|logs|ps>
pnpm smiski svc <all|user|meeting|chat|notification>
pnpm smiski web                          # Next.js dev server
pnpm smiski <build|test|format|lint|openapi|clean>
```

The CLI loads only allowlisted secrets from `services/docker/.env` before
running Spring services and forwards SIGINT/SIGTERM to all child processes when
running services in parallel.

### Backend services (Gradle)

```sh
./services/gradlew build                               # all services
./services/gradlew -p services/ < service-name > build # single service
./services/gradlew spotlessApply                       # format Java/KTS/XML
./services/gradlew bufFormatApply                      # format proto
```

### Web app

```sh
pnpm --dir frontends/web dev
pnpm --dir frontends/web build
pnpm --dir frontends/web lint
pnpm --dir frontends/web lint:fix
pnpm --dir frontends/web format
```

### OpenAPI / SDK generation

```sh
pnpm run openapi:services                 # generate per-service OpenAPI via tests
pnpm run openapi:join                     # merge specs with Redocly
pnpm run openapi:lint                     # lint unified spec
pnpm run openapi:unified                  # full pipeline (services + join + lint)
pnpm --dir frontends/web run sdk:generate # regenerate web SDK from unified spec
```

### Root-level formatting

```sh
pnpm lint   # markdownlint
pnpm format # prettier for md/json/toml/yaml/sh
```

## Test Commands

### Backend services

```sh
./services/gradlew test                               # all services
./services/gradlew -p services/ < service-name > test # single service
./services/gradlew -p services/ < service-name > generateOpenApiDocsFromTests
```

## Pre-commit Hooks (lefthook)

Configured in `lefthook.yml`. Runs in parallel on staged files:

- `gitleaks protect --staged` — secret scan
- Spotless — Java/KTS/XML for `services/**`
- Buf — proto formatting (`services/proto`)
- Biome `check --fix` — web (`frontends/web`)
- `bun run format` (Prettier) — md/json/yaml/toml/sh outside `frontends/web`

`commit-msg` runs commitlint against `commitlint.config.js`.

## Database Migrations (Flyway)

Postgres services (`user-management`, `meeting-management`) manage schema with
Flyway SQL under `src/main/resources/db/migration/`. `chat-management` uses a
Flyway-for-MongoDB JS migration (`V1__init_indexes.js`), referenced by name in
`k8s/base/kustomization.yaml` and `docker/compose.yaml` — do not rename it.

- Baseline: each Postgres service starts the current phase from a single
  `B1.0.0__baseline.sql` (Flyway `B` baseline prefix — applied only on a clean
  DB). Incremental changes after it use the standard `V<n>__<desc>.sql` prefix
  (e.g. `V2__...`).
- `ddl-auto: validate` (main) / `none` (test): JPA entities must match the
  schema exactly, so keep migrations and `*JpaEntity.java` in sync.
- Never edit an applied migration; always add a new `V` migration.

## Code Conventions

- Backend: JSend envelope pattern for HTTP responses; interceptors on the web
  client unwrap envelopes automatically.
- Web: generated SDK lives in `frontends/web/src/generated`; do not edit
  manually.
- Biome is the formatter/linter for web (not ESLint/Prettier per-file).
