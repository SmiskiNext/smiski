# services/AGENTS.md

Backend guide: Spring Boot 4 / Java 25 microservices, hexagonal + DDD. Read the
root `AGENTS.md` for repo-wide commands and the legacy `zms/` warning.

## Services

Packages are `io.github.smiskinext.<name>`. Each service is its own Gradle build
registered in `settings.gradle.kts` via `includeBuild`.

- `tenant` — Postgres `tenants`
- `meet` — Postgres `meetings`, Kafka, LiveKit (most complete reference service)
- `record` — Postgres `recordings`, LiveKit egress → RustFS (S3-compatible)
- `notification` — Kafka consumer, Resend email (no DB, no Flyway)
- `shared` — shared libs + `testFixtures` (ArchUnit base + testcontainer
  support)
- `proto` — gRPC proto (`buf`); `notification` → identity service over gRPC

## Commands

Run from repo root. `<name>` is a service dir.

```sh
./services/gradlew build                                                # all services (test + integrationTest)
./services/gradlew -p services/ < name > build                          # build one
./services/gradlew -p services/ < name > test                           # fast tests only (unit + ArchUnit)
./services/gradlew -p services/ < name > integrationTest                # Testcontainers + @SpringBootTest
./services/gradlew -p services/ < name > jacocoTestReport               # coverage report (HTML + XML)
./services/gradlew -p services/ < name > jacocoTestCoverageVerification # coverage gate (opt-in)
./services/gradlew -p services/ < name > pitest                         # mutation testing (opt-in)
./services/gradlew -p services/ < name > generateOpenApiDocsFromTests   # emit openapi.yaml
./services/gradlew spotlessApply                                        # format Java/KTS/XML
./services/gradlew bufFormatApply                                       # format proto
```

API-first: each service emits its own spec to `services/<name>/openapi.yaml`
from a `@SpringBootTest`; specs are per-service, never merged.
`pnpm run openapi` (root) regenerates + lints tenant/meet/record.

## Hexagonal + DDD layering (ArchUnit-enforced)

`domain → application → infrastructure → presentation`. Outer layers depend
inward; domain is framework-agnostic. Enforced by each service's
`architecture/ArchitectureTest` extending the shared `CleanArchitectureTest`
(`services/shared/src/testFixtures`).

```text
services/<name>/src/main/java/io/github/smiskinext/<name>/
  domain/
    model/                 # Aggregates (extends AggregateRoot<UUID>), entities, enums
      valueobject/         # Value Objects: records implementing ValueObject; validate in compact ctor
    port/                  # Outbound ports (interfaces): {Entity}Repository, EventPublisher, external contracts
    event/                 # Domain events: PublishableEvent (Kafka) or DomainEvent (internal)
    projection/            # Read models for queries (CQRS-lite)
    {Feature}Error.java    # Sealed interface of error records → used in Result<T, {Feature}Error>
    {Feature}ErrorCode.java # Enum implementing ErrorCode (machine-readable API codes)
  application/
    usecase/               # Inbound ports: INTERFACES named *UseCase, extend shared UseCase<I,O,E>
    service/               # @Service @Transactional IMPLS named *ApplicationService (implement *UseCase)
    command/               # Write input records (implement shared Command); no validation
    query/                 # Read input records (implement shared Query)
    result/                # Framework-agnostic use-case output records (one per use case, named after the use case, e.g. RegisterTenantResult); no Jackson/Swagger
    mapper/                # domain → application result (static or MapStruct; interface+@Component only if collaborators)
    helper/                # Reusable orchestration helpers
    handler/               # Internal domain-event handlers
  infrastructure/
    persistence/           # {Entity}JpaEntity, {Entity}JpaRepository (Spring Data),
                           # {Entity}RepositoryAdapter (@Repository, implements domain port),
                           # {Entity}PersistenceMapper (entity ⇄ domain); OutboxEvent* for outbox
    messaging/             # KafkaEventPublisher (implements EventPublisher), consumers, outbox poller
    livekit/ storage/ email/ sse/  # External adapters (per-service)
    config/                # Framework config beans (Kafka, LiveKit, Redis...)
    web/                   # WebConfig, cursor codecs
    security/              # SecurityConfig
  presentation/
    request/               # Request DTO records (Jakarta @Valid); each has toCommand()/toQuery()
    response/              # Response DTO records (JSON/OpenAPI @Schema); static `from(...)` factory maps application result → response
    {Feature}Controller.java  # @RestController; injects *UseCase; maps Result<> via ResultResponder
  {Service}Application.java # @SpringBootApplication (scanBasePackages = service + shared)
  resources/application.yaml, db/migration/

src/test/java/.../<name>/                # FAST — no Spring context, no containers
  domain/                                # pure unit tests: aggregates, VOs, domain services
  application/                           # use-case tests: *ApplicationService with mocked ports
  architecture/ArchitectureTest.java     # extends CleanArchitectureTest + @AnalyzeClasses
src/integrationTest/java/.../<name>/     # SLOW — Testcontainers + @SpringBootTest
  config/TestcontainersConfiguration.java # per-service containers via shared factories
  infrastructure/                        # adapter tests: *RepositoryAdapter, KafkaEventPublisher
  presentation/                          # @WebMvcTest / @SpringBootTest controller tests
  {Service}ApplicationTests.java         # @SpringBootTest context-load
  {Service}OpenApiGenerationTest.java    # emits openapi.yaml
src/integrationTest/resources/application-test.yaml
```

## Testing strategy (hexagonal, layer-aligned)

Two source sets, wired by the `io.github.smiskinext.plugin.test.base` convention
plugin (`build-logic/`). `test` is fast and container-free; `integrationTest`
runs container-backed and full-context tests.

| Layer          | Source set        | Test type                                     |
| -------------- | ----------------- | --------------------------------------------- |
| domain         | `test`            | pure unit tests (no Spring, no containers)    |
| application    | `test`            | use-case tests, outbound ports Mockito-mocked |
| architecture   | `test`            | ArchUnit (`CleanArchitectureTest`)            |
| infrastructure | `integrationTest` | adapter tests via Testcontainers              |
| presentation   | `integrationTest` | controller / problem+json tests               |
| full context   | `integrationTest` | `@SpringBootTest`, OpenAPI generation         |

- `build`/`check` run `test` then `integrationTest`. Keep container-dependent
  tests out of `test` so the fast suite and mutation runs need no Docker.
- **Coverage (JaCoCo)**: `jacocoTestReport` / `jacocoTestCoverageVerification`
  measure only the fast `test` task (no Docker) for local use.
  `jacocoAggregatedReport` / `jacocoCoverageVerificationAll` combine `test` +
  `integrationTest` execution data for a full-picture gate (line ≥ 70% / branch
  ≥ 60%); CI runs the aggregated variant. Bootstrap, `*Config`, `*JpaEntity`,
  and generated classes are excluded.
- **Mutation (PIT)**: `pitest` targets `..domain..` + `..application..` against
  the fast `test` suite (JUnit 5 plugin), threshold 60%.
- Gates are **opt-in** (not wired into `check`) and ratchet upward as suites are
  backfilled. Thresholds and tool versions live in the convention plugin +
  `gradle/libs.versions.toml`, changed in one place for all services.

Shared library (`io.github.smiskinext.shared`):

- `domain/` — AggregateRoot, ValueObject, DomainEvent, DomainError, ErrorCode,
  Result<T,E>, paging
- `application/` — UseCase<I,O,E>, Command, Query (reusable inbound-port
  contracts)
- `infrastructure/` — ResultResponder, ProblemDetailMapper, Violation,
  GlobalExceptionHandler, ApiPathPrefix, tenancy, cache, logging, forge
- `testFixtures/` — architecture/CleanArchitectureTest,
  testcontainers/{Postgres,Kafka,Valkey,Minio}ContainerSupport

## Key patterns

- Use case = interface (inbound port) in `application.usecase`; impl =
  `*ApplicationService` in `application.service`. Controllers inject the
  interface, never the impl.
- Domain entities: static factories (`schedule()`/`create()` for new,
  `reconstitute()` from DB). VOs validate in the compact constructor.
- Error handling: `Result<T, {Feature}Error>` across boundaries — no exceptions
  for business rules. `{Feature}Error` is a sealed interface extending shared
  `DomainError`.
- Flow: Request → Command/Query → (usecase) → Result → Response. Request DTOs
  validate + `toCommand()`; command/query records carry no validation. Use-case
  results live in `application.result`; presentation maps them to
  `presentation.response` DTOs via static `from(...)` factories.
- Mapping split: `application.mapper` (domain → application result),
  `presentation.response.<Dto>#from` (result → response DTO),
  `infrastructure.persistence.{Entity}PersistenceMapper` (entity ⇄ domain).
- HTTP: RFC 9457 `application/problem+json` for errors; raw representation
  bodies (no envelope) for success. See `openspec/specs/api-convention/spec.md`.
- Primary keys: UUIDv7 (`UuidCreator.getTimeOrderedEpoch()`).
- Events: `registerEvent()` on AggregateRoot, cleared after publishing; filter
  by `PublishableEvent`; CloudEvents 1.0 over Kafka.

## ArchUnit naming (build fails if violated)

- `*UseCase` (usecase interfaces), `*ApplicationService` (service impls),
  `*RepositoryAdapter`, `*JpaEntity`, `*Controller`.
- `@Service` only in `application`; `@Repository`/`@Entity` only in
  `infrastructure.persistence`; `@RestController` only in `presentation`.
- Domain stays framework-agnostic (no Spring/JPA imports).

## Flyway migrations (Postgres: tenant, meet, record)

Schema lives in `src/main/resources/db/migration/`. `notification` has no DB.

- Baseline: each service starts from a single `B1.0.0__baseline.sql` (Flyway `B`
  prefix — applied only on a clean DB). Incremental changes use
  `V<n>__<desc>.sql` (e.g. `V2__...`).
- `ddl-auto: validate` (main) / `none` (test): `*JpaEntity.java` must match the
  schema exactly — keep migrations and entities in sync.
- Never edit an applied migration; always add a new `V` migration.

## REST API design

Shared versioned path scheme via `spring.mvc.apiversion` +
`ApiPathPrefixAutoConfiguration` (`services/shared`). URL shape:
`/api/{version}/path/to/resource`, `{version}` an integer at path-segment index
1 (segment 0 is literal `api`).

- The `/api/{version}` prefix is applied globally to every `@RestController` via
  `PathMatchConfigurer#addPathPrefix`. Never repeat `api` or the version in
  controller mappings.
- Declare the full resource path at method level; do not rely on a class-level
  `@RequestMapping` base path:

    ```java
    @RestController
    class MeetingController {
        @GetMapping("/meetings/{id}")   // → /api/1/meetings/{id}
        @PostMapping("/meetings")       // → /api/1/meetings
    }
    ```

- Standard RESTful methods map to collection/resource paths
  (`GET/POST /meetings`, `GET/PUT/DELETE /meetings/{id}`,
  `GET/POST /meetings/{id}/participants`).
- Action endpoints (outside standard REST) use the `:action` suffix on the
  target resource and stay `POST`: `/meetings/{id}/participants/{id}:mute`,
  `/meetings/{id}:end`.
- Actuator and other non-`@RestController` endpoints stay unprefixed.

## Conventions

- Self-documenting code; no inline comments. Standard doc comments where useful.
- Spotless formats Java/KTS/XML; runs on staged `services/**` at pre-commit.
