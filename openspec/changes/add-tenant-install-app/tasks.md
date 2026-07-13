## 1. Proto contract

- [x] 1.1 Create
      `services/proto/src/main/proto/io/github/smiskinext/event/tenant/v1/tenant_installed.proto`
      with `package io.github.smiskinext.event.tenant.v1`,
      `java_multiple_files = true`, and a `TenantInstalled` message carrying
      cloudId, installation id, app id, app version, environment id, environment
      type, site url, installer account id, and installed_at
- [x] 1.2 Run `pnpm run openapi`-adjacent proto tooling /
      `./services/gradlew bufFormatApply` and confirm Buf STANDARD lint passes ←
      (verify: proto compiles, generated `TenantInstalled` class available on
      the tenant classpath, Buf STANDARD lint clean)

## 2. Shared application contracts

- [x] 2.1 Add `UseCase<I, O, E>` interface in
      `io.github.smiskinext.shared.application` with a single method returning
      `Result<O, E>`
- [x] 2.2 Add `Command` and `Query` marker interfaces in
      `io.github.smiskinext.shared.application`
- [x] 2.3 Confirm shared module still compiles and existing services are
      unaffected ← (verify: `./services/gradlew -p services/shared build`
      passes)

## 3. Tenant domain

- [x] 3.1 Add enums `TenantStatus` (ACTIVE, SUSPENDED, UNINSTALLED) and
      `EnvironmentType` (DEVELOPMENT, STAGING, PRODUCTION) in `domain/model`
- [x] 3.2 Add `Tenant` aggregate extending `AggregateRoot<String>` (identity =
      cloudId) with `install(...)` factory (new), `reinstall(...)` (replace
      installation id, reactivate, refresh updated_at, preserve installed_at),
      and `reconstitute(...)`; both install and reinstall register a
      `TenantInstalledEvent`
- [x] 3.3 Add value objects required by the aggregate (e.g. `InstallationId`,
      `AppId`) with validation in compact constructors
- [x] 3.4 Add tenant-local `PublishableEvent` interface with `aggregateId()`
      typed as `String`, plus `eventId`, `aggregateType`, `eventType`, `topic`,
      `occurredAt`
- [x] 3.5 Add `event/TenantInstalledEvent` record implementing
      `PublishableEvent` — framework-free, no proto imports; carries appVersion,
      environmentId, environmentType, siteUrl, installerAccountId; `eventType` =
      `io.github.smiskinext.tenant.v1.installed`, `topic` =
      `tenant.tenant.installed`, `aggregateType` = `tenant`
- [x] 3.6 Add ports `TenantRepository` and `EventPublisher` in `domain/port`
- [x] 3.7 Add `TenantError` (sealed interface extending shared `DomainError`)
      and `TenantErrorCode` (enum implementing `ErrorCode`) including a
      missing-tenant-context error mapped to an appropriate `ErrorCategory`

## 4. Tenant application

- [x] 4.1 Add `command/RegisterTenantCommand` (record implementing `Command`;
      carries the install payload fields, no validation)
- [x] 4.2 Add `usecase/RegisterTenantUseCase` interface extending shared
      `UseCase`, input command + cloudId, output response, error `TenantError`
- [x] 4.3 Add `response/TenantResponse` record (tenantId, installationId, appId,
      status, environmentType, installedAt) and `mapper/TenantResponseMapper`
      (domain → response)
- [x] 4.4 Add `service/RegisterTenantApplicationService`
      (`@Service @Transactional`) implementing the use case: resolve cloudId,
      reject default/missing tenant, upsert via `TenantRepository`, drain domain
      events, filter `PublishableEvent`, publish via `EventPublisher`, return
      `Result<TenantResponse, TenantError>` ← (verify: create returns new-tenant
      result, update path returns existing-tenant result, both publish exactly
      one event, missing context returns failure)

## 5. Tenant infrastructure — persistence

- [x] 5.1 Add `persistence/TenantJpaEntity` mapped to `tenants` with `@TenantId`
      on `tenant_id`, matching `B1.0.0__baseline.sql` exactly
- [x] 5.2 Add `persistence/TenantJpaRepository` (Spring Data) and
      `persistence/TenantRepositoryAdapter` (`@Repository`, implements
      `TenantRepository`) with `TenantPersistenceMapper` (entity ⇄ domain)
- [x] 5.3 Add `persistence/OutboxEventJpaEntity` mapped to `outbox_event` with
      `@TenantId`, `aggregate_id` as `String`, matching the baseline, plus
      `persistence/OutboxEventJpaRepository` exposing an unpublished-rows finder
      ordered by `created_at`

## 6. Tenant infrastructure — messaging

- [x] 6.1 Add `messaging/TenantEventProtoMapper` converting
      `TenantInstalledEvent` → `TenantInstalled` proto message
- [x] 6.2 Add `messaging/OutboxEventPublisher` (implements `EventPublisher`):
      build CloudEvent (`data` = proto rendered by `JsonFormat`,
      `datacontenttype` `application/json`, `type` = versioned installed id,
      `dataschema` = proto FQN, `id` unique), serialize the CloudEvent to a JSON
      string, insert an `outbox_event` row in the current transaction
- [x] 6.3 Add `config/KafkaProducerConfig` exposing
      `KafkaTemplate<String, CloudEvent>` and `messaging/OutboxRelayScheduler`
      (`@Scheduled`): select unpublished rows, parse payload to `CloudEvent`,
      send keyed by aggregate id, set `published_at` on success, increment
      `retry_count` + record `last_error` on failure ← (verify: relay publishes
      unpublished rows and marks them, published rows not resent, failures
      increment retry_count with published_at still null, message key = cloudId)
- [x] 6.4 Add `config/TenantMessageConfiguration` registering `messages/tenant`
      bundle; enable `@EnableScheduling` (on config or application)

## 7. Tenant presentation

- [x] 7.1 Add `presentation/request/RegisterTenantRequest` — nested Forge shape
      (`id`, `installerAccountId?`, `app{id,version}`, `environment{id}?`,
      `environmentType?`, `siteUrl?`) with Jakarta validation (`id`, `app.id`
      non-blank; `app.version` required) and a `toCommand()` method; default
      `environmentType` to `PRODUCTION`. Unknown Forge fields (e.g. `app.name`,
      `app.ownerAccountId`) are silently ignored by Jackson.
- [x] 7.2 Add `presentation/TenantController` (`@RestController`,
      `@PostMapping("/tenants")`): read cloudId from `TenantContext`, invoke use
      case, map via `ResultResponder` (`created(...)` with `Location` on new,
      `ok(...)` on update) ← (verify: route resolves to
      `/api/{version}/tenants`, create → 201 + Location, update → 200, no
      envelope, tenant identity comes from context not body)

## 8. Resources

- [x] 8.1 Add `messages/tenant.properties` and `messages/tenant_vi.properties`
      with title/detail text for every `TenantErrorCode`
- [x] 8.2 Add `implementation(libs.protobuf.java.util)` to
      `services/tenant/build.gradle.kts` (proto already available via
      `service.base`)

## 9. Unit tests (fast suite)

- [x] 9.1 `Tenant` aggregate: first install creates ACTIVE tenant and registers
      `TenantInstalledEvent` (install-app: First installation creates the
      tenant)
- [x] 9.2 `Tenant` aggregate: reinstall replaces installation id, reactivates to
      ACTIVE, refreshes updated_at, preserves installed_at, registers event
      (install-app: Reinstall updates installation id and reactivates; Update
      outcome still emits the event)
- [x] 9.3 `RegisterTenantApplicationService`: cloudId taken from context and
      rejects default/missing tenant with the missing-context error
      (install-app: cloudId is taken from context, not the body; Missing tenant
      context is rejected)
- [x] 9.4 `RegisterTenantApplicationService`: redelivery updates in place
      without a duplicate and still publishes (install-app: Redelivery of the
      same installation is safe; event-driven: Event enqueued atomically with
      the upsert — service-level publish call)
- [x] 9.5 `RegisterTenantRequest`/mapper: absent `environmentType` defaults to
      PRODUCTION and optional members may be omitted (install-app: Absent
      environment type defaults to PRODUCTION; Optional members may be omitted)
- [x] 9.6 `TenantResponseMapper`: representation reflects persisted state and
      carries no envelope fields (install-app: Representation reflects persisted
      state; Representation carries no envelope)
- [x] 9.7 `TenantEventProtoMapper`: domain event maps to `TenantInstalled` proto
      with matching fields (event-driven: Domain event mapped to proto at the
      boundary)

## 10. Integration tests (Testcontainers)

- [x] 10.1 Add `KafkaContainer` to `integrationTest`
      `TestcontainersConfiguration` alongside the existing Postgres container
- [x] 10.2 `TenantRepositoryAdapter` + `OutboxEventPublisher` (Postgres):
      upsert + outbox row written atomically; rolled-back transaction leaves no
      outbox row (event-driven: Event enqueued atomically with the upsert;
      Rolled-back upsert leaves no event)
- [x] 10.3 `OutboxEventPublisher` payload: stored `payload` is a proto-JSON
      CloudEvent with `datacontenttype` `application/json`, versioned stable
      `type`, unique `id` (event-driven: Payload is a proto-JSON CloudEvent in
      TEXT; Event type is versioned and stable)
- [x] 10.4 `OutboxRelayScheduler` (Postgres + Kafka): unpublished rows relayed
      and marked, published rows not resent, failure increments retry_count with
      published_at null, message key = cloudId (event-driven: Unpublished rows
      are relayed and marked; Already-published rows are not resent; Publish
      failure is retryable; Aggregate id is the Kafka message key)
- [x] 10.5 `TenantController` (@SpringBootTest): first install → 201 +
      Location + representation; redelivery → 200; missing `X-Tenant-ID` →
      Problem Details missing-context; invalid body (missing `id`, missing
      `app.id`) → 400 `VALIDATION_ERROR` with per-field `errors` (install-app:
      First installation creates the tenant; Redelivery of the same installation
      is safe; Missing tenant context is rejected; Missing required installation
      id is rejected; Missing required app id is rejected)
- [x] 10.6 Update `TenantApplicationTests`/`TenantOpenApiGenerationTest` as
      needed so context loads with the Kafka producer bean, and regenerate
      `services/tenant/openapi.yaml` ← (verify: `POST /tenants` present in
      openapi.yaml, context loads with Kafka + scheduler beans)

## 11. Verification

- [x] 11.1 `./services/gradlew spotlessApply` then
      `./services/gradlew -p services/tenant test` (unit + ArchUnit) passes
- [x] 11.2 `./services/gradlew -p services/tenant integrationTest` passes with
      Postgres + Kafka containers
- [x] 11.3 `./services/gradlew -p services/tenant generateOpenApiDocsFromTests`
      regenerates the spec and `./services/gradlew -p services/proto build`
      compiles the proto ← (verify: full tenant build green, ArchUnit layering +
      naming rules satisfied, openapi.yaml committed)
