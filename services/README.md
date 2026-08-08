# Smiski Backend Services

The backend for the Smiski meeting module: Spring Boot 4 / Java 25 microservices
built with a hexagonal architecture and DDD, plus a Go authorization service and
the gateway/infra that front them. The Forge app
([../app/README.md](../app/README.md)) is the only client.

## Services

Java packages are `io.github.smiskinext.<name>`. Each Spring service is its own
Gradle build, registered in `settings.gradle.kts` via `includeBuild`.

| Service        | Language | Data                     | Responsibility                                             |
| -------------- | -------- | ------------------------ | ---------------------------------------------------------- |
| `tenant`       | Java     | Postgres `tenant`        | Identity and tenancy                                       |
| `meet`         | Java     | Postgres `meet`, LiveKit | Meeting lifecycle, issue link, join, tokens (reference)    |
| `notification` | Java     | Postgres `notification`  | Resend email, SSE event streams, Kafka consumer            |
| `gateway`      | Go       | Valkey                   | Envoy `ext_authz` — validates the FIT against Jira         |
| `shared`       | Java     | —                        | Shared domain types, `Result<T,E>`, test fixtures          |
| `proto`        | proto    | —                        | gRPC + Kafka CloudEvent contracts (`buf`)                  |
| `record`       | Java     | Postgres                 | Recording — present but **not wired** into the build/stack |

`meet` is the most complete service and the reference implementation for the
layering below. `record` is intentionally excluded from the aggregate build and
the Compose stack.

## How they communicate

- **North–south:** the Forge app calls the API gateway (Envoy locally, Kong on
  k8s). Envoy's `ext_authz` filter calls the Go `gateway` service, which
  validates the Forge Installation Token against Jira's permission API (caching
  results in Valkey) before Envoy routes to a Spring service.
- **East–west:** services publish domain events as CloudEvents 1.0 over Kafka
  (contracts in `proto/`); `notification` consumes them and maintains a `tenant`
  read-model projection. `proto/` also holds a gRPC user-service contract.
- **Real-time:** `notification` serves SSE event streams via Valkey pub/sub;
  `meet` mints LiveKit room tokens and LiveKit delivers WebRTC media directly to
  the browser.

## Prerequisites

- Java 25 (toolchain pinned by the repo's `.mise.toml`)
- Docker with Compose v2 (for the local stack and integration tests)
- Go 1.25+ (only to work on the `gateway` service directly)

## Build and test

Run from the repository root. `<name>` is a service directory.

```bash
./services/gradlew build                                         # build + test all services
./services/gradlew -p services/meet build                        # build one service
./services/gradlew -p services/meet test                         # fast tests (unit + ArchUnit, no Docker)
./services/gradlew -p services/meet integrationTest              # Testcontainers + @SpringBootTest
./services/gradlew -p services/meet jacocoTestReport             # coverage report
./services/gradlew -p services/meet generateOpenApiDocsFromTests # emit openapi.yaml
./services/gradlew spotlessApply                                 # format Java / KTS / XML
./services/gradlew bufFormatApply                                # format proto
```

Two source sets: `test` is fast and container-free (domain, application,
architecture); `integrationTest` is Testcontainers-backed (infrastructure,
presentation, full-context). `build` and `check` run both. Keep
container-dependent tests out of `test`. Coverage and mutation gates are opt-in
— see [AGENTS.md](AGENTS.md#testing-strategy-hexagonal-layer-aligned).

## Run the local stack

Java images are built by `bootBuildImage`, not by Compose. Build them first,
then start the stack:

```bash
cp docker/.env.example docker/.env # set SMISKI_HOST_IP
./services/gradlew -p services/tenant bootBuildImage
./services/gradlew -p services/meet bootBuildImage
./services/gradlew -p services/notification bootBuildImage
docker compose -f services/docker/compose.yaml up -d
```

The Go `gateway` is built from source by Compose. Everything is reached through
Envoy at `http://localhost:30000`. Full host port map, gateway route table,
observability profile, and the unauthenticated-stream security caveat are in
[docker/AGENTS.md](docker/AGENTS.md).

For iterative work, run one service natively with the `dev` profile against the
stack's datastores:

```bash
docker compose -f services/docker/compose.yaml up -d
./services/gradlew -p services/meet bootRun --args='--spring.profiles.active=dev'
```

## Architecture (hexagonal + DDD)

Dependencies point inward:
`domain → application → infrastructure → presentation`, with the domain
framework-agnostic. Each service enforces this with an ArchUnit test extending
the shared `CleanArchitectureTest`.

```text
domain/         Aggregates, value objects, ports (interfaces), events, errors
application/    Use-case interfaces (*UseCase) + @Service impls (*ApplicationService)
infrastructure/ JPA adapters, Kafka/outbox, LiveKit/email/SSE, config, security
presentation/   @RestController, request/response DTOs (RFC 9457 problem+json errors)
```

Key patterns: controllers inject use-case interfaces, never impls; business
rules return `Result<T, {Feature}Error>` rather than throwing; UUIDv7 primary
keys; events registered on the aggregate and published as CloudEvents. Full
detail — layer layout, ArchUnit naming rules, Flyway migration policy, and the
versioned `/api/{version}` REST scheme — is in [AGENTS.md](AGENTS.md).

## API specs

Each service emits its own OpenAPI 3.1 spec to `services/<name>/openapi.yaml`
from a `@SpringBootTest`. The root pipeline generates all specs, joins them into
`services/openapi.yaml`, and lints with Redocly:

```bash
pnpm run openapi # generate + join + lint (from repo root)
```

## The Go gateway

`gateway/` is a standalone Go module implementing Envoy's `ext_authz` gRPC
service. It reads the Forge Installation Token from incoming requests, checks
the caller's `View`/`Edit Meeting` permission against Jira (`JIRA_API_BASE`),
and caches results in Valkey. It fails closed: with `failure_mode_allow: false`,
an unreachable gateway rejects every authenticated request. It is configured
entirely from the environment (`REDIS_ADDR`, `JIRA_API_BASE`, `CACHE_TTL`,
`GRPC_PORT`, …) and serves a gRPC health check on `:9001`.

```bash
go -C services/gateway test ./...
go -C services/gateway build ./cmd/gateway
```

## Kubernetes

See [k8s/deploy.en.md](k8s/deploy.en.md) (English) or
[k8s/deploy.vi.md](k8s/deploy.vi.md) (Tiếng Việt) for the k3s deployment guide:
Helm charts (Kong, LiveKit, Strimzi/Kafka, PLG), Kustomize overlays, and secret
management.

## License

GNU Affero General Public License v3.0 (AGPL-3.0) — see [LICENSE](../LICENSE)
for details. Copyright 2025 SmiskiNext.
