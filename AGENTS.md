# AGENTS.md

Guidance for OpenCode sessions in this repo. Keep changes verifiable against
config and scripts, not prose.

## Architecture

Monorepo: Spring Boot 4 / Java 25 microservices (hexagonal:
`domain → application → infrastructure → presentation`) + an Atlassian Forge
app. API-first: services emit OpenAPI via tests, merged into
`openapi/unified-openapi.yaml`.

The stack has migrated from the legacy Zero Meeting System into a focused Jira
integration. The **legacy codebase is archived at `zms/`** (gitignored, full
snapshot) — treat it as **read-only reference** and consult it only when asked
or when porting behavior. Never extend or wire into it.

**Current services** (`services/`, packages `io.github.smiskinext.<name>`):

- `tenant` — Postgres `tenants` (identity/tenancy; supersedes `user-management`)
- `meet` — Postgres `meetings`, Kafka, LiveKit
- `record` — Postgres `recordings`, LiveKit egress → RustFS (S3-compatible)
- `notification` — Kafka consumer, Resend email (no DB)
- `proto`, `shared` — shared proto + libs

The legacy `user-management`, `meeting-management`, `chat-management` dirs no
longer live under `services/` — they exist only in `zms/services/`.

**Frontend:** `app/` is an Atlassian **Forge** app (Jira issue panel, UI Kit).
It has its own `app/AGENTS.md` with strict Forge rules — read it before editing.
The old `frontends/web` Next.js client is **removed** (present only in `zms/`).

**Wiring:** Kong gateway (external) · gRPC (`notification` → user identity
service) · Kafka + CloudEvents (async, consumed by `notification`) · SSE +
Valkey (real-time). Integrations: LiveKit (WebRTC + egress), Firebase, Resend.

Builds are registered in `services/settings.gradle.kts` via `includeBuild`.

## Commands

Prefer the `pnpm smiski` CLI (`scripts/`, citty + tsx + zx) — it loads
allowlisted secrets from `services/docker/.env` and forwards signals to parallel
child processes.

```sh
pnpm smiski --help                 # list groups
pnpm smiski setup                  # bootstrap: mise tools + pnpm + hooks + .env
pnpm smiski setup --env-only       # copy services/docker/.env from .env.example
pnpm smiski doctor                 # tool status
pnpm smiski dev                    # infra up + backend services in parallel
pnpm smiski infra <up|down|reset|logs|ps>
```

Gradle:

```sh
./services/gradlew build                       # all services
./services/gradlew -p services/ < name > build # build one
./services/gradlew -p services/ < name > test  # test one
./services/gradlew -p services/ < name > generateOpenApiDocsFromTests
./services/gradlew spotlessApply  # format Java/KTS/XML
./services/gradlew bufFormatApply # format proto
```

Root formatting: `pnpm lint` (markdownlint), `pnpm format` (prettier).

## Pre-commit (lefthook, parallel on staged files)

`gitleaks protect --staged` · Spotless (`services/**`) · Buf (`services/proto`)
· Biome `check --fix` (frontend) · Prettier for md/json/yaml/toml/sh elsewhere.
`commit-msg` runs commitlint (`commitlint.config.js`).

## Database Migrations (Flyway)

Postgres services (`tenant`, `meet`, `record`) manage schema with Flyway SQL
under `src/main/resources/db/migration/`.

- Baseline: each service starts from a single `B1.0.0__baseline.sql` (Flyway `B`
  prefix — applied only on a clean DB). Incremental changes use
  `V<n>__<desc>.sql` (e.g. `V2__...`).
- `ddl-auto: validate` (main) / `none` (test): JPA entities must match schema
  exactly — keep migrations and `*JpaEntity.java` in sync.
- Never edit an applied migration; always add a new `V` migration.

## Code Conventions

- Backend: JSend envelope for HTTP responses; client interceptors unwrap it.
- Biome is the formatter/linter for the app; Spotless for Java.
- Self-documenting code; no inline comments. Standard doc comments where useful.

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
