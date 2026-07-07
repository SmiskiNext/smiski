# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with
code in this repository.

## Architecture Overview

Monorepo: Spring Boot 4 / Java 25 backend microservices (hexagonal:
`domain → application → infrastructure → presentation`) + an Atlassian Forge
app. API-first: services emit OpenAPI via tests, merged into
`openapi/unified-openapi.yaml`.

The stack has migrated from the legacy Zero Meeting System into a focused Jira
integration. The **legacy codebase is archived at `zms/`** (gitignored, full
snapshot) — treat it as **read-only reference** and consult it only when asked
or when porting behavior. Never extend or wire into it.

**Components:**

- `services/` — Spring Boot 4 / Java 25 microservices
- `app/` — Atlassian **Forge** app (Jira issue panel, UI Kit). Has its own
  `app/AGENTS.md` with strict Forge rules — read it before editing.
- `services/k8s/` — Kubernetes manifests (Kong gateway, Kafka, DBs, LiveKit,
  Valkey)
- `openspec/` — Product specs and change artifacts
- `build-logic/` — Shared Gradle convention plugins

**Current services** (`services/`, packages `io.github.smiskinext.<name>`):

- `tenant` — Postgres `tenants` (identity/tenancy; supersedes `user-management`)
- `meet` — Postgres `meetings`, Kafka, LiveKit
- `record` — Postgres `recordings`, LiveKit egress → RustFS (S3-compatible)
- `notification` — Kafka consumer, Resend email (no DB)
- `proto`, `shared` — shared proto + libs

The legacy `user-management`, `meeting-management`, `chat-management` (MongoDB)
and the Next.js `frontends/web` client no longer live in the tree — they exist
only in `zms/`.

**Wiring:** Kong gateway (external) · gRPC (`notification` → user identity
service) · Kafka + CloudEvents (async, consumed by `notification`) · SSE +
Valkey (real-time). Integrations: LiveKit (video), Firebase (auth/storage),
Resend (email).

## Build Commands

### Convenience CLI (`pnpm smiski`)

A TypeScript CLI under `scripts/` (citty + tsx + zx) wraps common dev workflows.
It loads only allowlisted secrets from `services/docker/.env` before running
Spring services and forwards SIGINT/SIGTERM to child processes in parallel runs.

```sh
pnpm smiski --help                       # list all groups
pnpm smiski setup                        # bootstrap (mise tools + pnpm + hooks + .env)
pnpm smiski setup --env-only             # only copy services/docker/.env from .env.example
pnpm smiski doctor                       # report tool status
pnpm smiski dev                          # infra up + backend services in parallel
pnpm smiski infra <up|down|reset|logs|ps>
```

### Backend services (Gradle)

```sh
./services/gradlew build                       # all services
./services/gradlew -p services/ < name > build # build one
./services/gradlew -p services/ < name > test  # test one
./services/gradlew -p services/ < name > generateOpenApiDocsFromTests
./services/gradlew spotlessApply  # format Java/KTS/XML
./services/gradlew bufFormatApply # format proto
```

### Root-level formatting

```sh
pnpm lint   # markdownlint
pnpm format # prettier for md/json/toml/yaml/sh
```

## Pre-commit Hooks (lefthook)

Configured in `lefthook.yml`. Runs on staged files:

- `gitleaks protect --staged` — secret scan
- Spotless — Java/KTS/XML for `services/**`
- Buf — proto formatting (`services/proto`)
- Biome `check --fix` — `scripts/` and `app/`
- Prettier + markdownlint — md/json/yaml/toml/sh elsewhere

`commit-msg` runs commitlint against `commitlint.config.js`. `pre-push` blocks
direct pushes to `main` and runs full-scan verification.

## Database Migrations (Flyway)

Postgres services (`meet`, `record`) manage schema with Flyway SQL under
`src/main/resources/db/migration/`.

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
- Biome is the formatter/linter for app.

## REST API Design

Postgres/servlet services (`tenant`, `meet`, `record`, `notification`) share a
versioned path scheme configured in `spring.mvc.apiversion` +
`ApiPathPrefixAutoConfiguration` (`services/shared`). The URL shape is
`/api/{version}/path/to/resource` where `{version}` is an integer (`1`, `2`,
`3`, …) at path-segment index 1 (segment 0 is the literal `api`).

- **The `/api/{version}` prefix is applied globally** to every `@RestController`
  via `PathMatchConfigurer#addPathPrefix`. Never repeat `api` or the version in
  controller mappings.
- **Always declare the full resource path at method level** — do not rely on a
  class-level `@RequestMapping` base path. Example:

    ```java
    @RestController
    class MeetingController {
        @GetMapping("/meetings/{id}")           // → /api/1/meetings/{id}
        @PostMapping("/meetings")               // → /api/1/meetings
    }
    ```

- **Standard RESTful methods** map to collection/resource paths
  (`GET/POST /meetings`, `GET/PUT/DELETE /meetings/{id}`,
  `GET/POST /meetings/{id}/participants`).
- **Action endpoints** (operations outside the standard RESTful methods) use the
  `:action` suffix on the target resource:
  `/meetings/{id}/participants/{id}:mute`, `/meetings/{id}:end`. Keep actions as
  `POST`.
- Actuator and other non-`@RestController` endpoints stay unprefixed.
