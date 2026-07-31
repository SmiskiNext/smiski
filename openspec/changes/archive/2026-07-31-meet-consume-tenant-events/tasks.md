## 1. Domain Layer

- [ ] 1.1 Create `TenantStatus` enum (`ACTIVE`, `SUSPENDED`, `UNINSTALLED`) in
      `domain/model/`
- [ ] 1.2 Create `TenantRecord` immutable record (`tenantId`, `cloudId`,
      `status`, `updatedAt`, `uninstalledAt`, `purgeAfter`) in `domain/model/`
- [ ] 1.3 Create `TenantRepository` port interface with
      `void upsert(TenantRecord record)` in `domain/port/`

## 2. Application Layer

- [ ] 2.1 Create `HandleTenantInstalledCommand` record in `application/command/`
- [ ] 2.2 Create `HandleTenantUninstalledCommand` record in
      `application/command/`
- [ ] 2.3 Create `HandleTenantInstalledUseCase` interface (extends `UseCase`) in
      `application/usecase/`
- [ ] 2.4 Create `HandleTenantUninstalledUseCase` interface (extends `UseCase`)
      in `application/usecase/`
- [ ] 2.5 Create `HandleTenantInstalledApplicationService` implementing
      `HandleTenantInstalledUseCase`, calls `TenantRepository.upsert()` with
      `status=ACTIVE` in `application/service/`
- [ ] 2.6 Create `HandleTenantUninstalledApplicationService` implementing
      `HandleTenantUninstalledUseCase`, calls `TenantRepository.upsert()` with
      `status=UNINSTALLED`, `uninstalledAt`, `purgeAfter` in
      `application/service/`

## 3. Infrastructure — Persistence

- [ ] 3.1 Create `TenantJpaEntity` (`@Entity @Table("tenants")`) with fields
      matching the baseline schema (`tenant_id`, `cloud_id`, `status`,
      `updated_at`, `uninstalled_at`, `purge_after`) in
      `infrastructure/persistence/`
- [ ] 3.2 Create `TenantJpaRepository` extending
      `JpaRepository<TenantJpaEntity, String>` in `infrastructure/persistence/`
- [ ] 3.3 Create `TenantRepositoryAdapter` (`@Repository`) implementing
      `TenantRepository`, upsert via `findById` + mutate or `save` in
      `infrastructure/persistence/` ← (verify: upsert is idempotent —
      redelivered events produce no duplicate row; ACTIVE re-upsert and
      UNINSTALLED upsert both save correctly)

## 4. Infrastructure — Kafka Config

- [ ] 4.1 Create `TenantKafkaProperties`
      (`@ConfigurationProperties(prefix="app.tenant.kafka")`) with
      `installedConsumerGroup`, `uninstalledConsumerGroup`, `retryAttempts`,
      `retryBackoffMs`, `deadLetterSuffix` in `infrastructure/messaging/`
- [ ] 4.2 Create `TenantKafkaConfig` (`@Configuration @EnableKafka`) wiring one
      `ConsumerFactory<String, CloudEvent>` (with `CloudEventDeserializer`,
      `earliest` offset reset), one `KafkaTemplate<String, CloudEvent>` for DLT,
      `DefaultErrorHandler` with `FixedBackOff` +
      `DeadLetterPublishingRecoverer`, and one
      `ConcurrentKafkaListenerContainerFactory` in `infrastructure/config/`

## 5. Infrastructure — Consumers

- [ ] 5.1 Create `TenantInstalledEventConsumer` (`@Component`, `@KafkaListener`
      on `tenant.tenant.installed`,
      `containerFactory="tenantKafkaListenerContainerFactory"`) that decodes
      `TenantInstalled` proto via `JsonFormat` and delegates to
      `HandleTenantInstalledUseCase` in `infrastructure/messaging/`
- [ ] 5.2 Create `TenantUninstalledEventConsumer` (`@Component`,
      `@KafkaListener` on `tenant.tenant.uninstalled`,
      `containerFactory="tenantKafkaListenerContainerFactory"`) that decodes
      `TenantUninstalled` proto via `JsonFormat` and delegates to
      `HandleTenantUninstalledUseCase` in `infrastructure/messaging/` ← (verify:
      both consumers decode correctly, missing/empty proto fields map to null in
      TenantRecord, decode errors throw and trigger DLT path)

## 6. Configuration

- [ ] 6.1 Add `app.tenant.kafka.*` properties to
      `src/main/resources/application.yaml` with defaults:
      `installed-consumer-group: meet-tenant-installed`,
      `uninstalled-consumer-group: meet-tenant-uninstalled`,
      `retry-attempts: 3`, `retry-backoff-ms: 1000`, `dead-letter-suffix: .dlt`

## 7. Unit Tests (fast, no containers)

- [ ] 7.1 `HandleTenantInstalledApplicationServiceTest`: verify `upsert` called
      with `status=ACTIVE` when command is handled (covers spec: "Installed
      event creates a new tenant row" + "upserts existing row")
- [ ] 7.2 `HandleTenantUninstalledApplicationServiceTest`: verify `upsert`
      called with `status=UNINSTALLED`, `uninstalledAt`, `purgeAfter` populated
      (covers spec: "Uninstalled event marks an existing tenant row" + "upserts
      even when row absent")
- [ ] 7.3 `TenantInstalledEventConsumerTest`: verify correct
      `HandleTenantInstalledCommand` built from a valid CloudEvent; verify
      `IllegalArgumentException` thrown on null data and on missing required
      `cloudId` field (covers spec: "Malformed CloudEvent data is retried then
      dead-lettered" trigger path)
- [ ] 7.4 `TenantUninstalledEventConsumerTest`: verify correct
      `HandleTenantUninstalledCommand` built from a valid CloudEvent; verify
      exception thrown on malformed proto-JSON (covers spec: "Malformed
      CloudEvent data" trigger path)
- [ ] 7.5 `ArchitectureTest`: existing ArchUnit test in `meet` picks up new
      classes — no additional test needed; verify no new ArchUnit violations
      with `./services/gradlew -p services/meet test`

## 8. Integration Tests (Testcontainers)

- [ ] 8.1 `TenantRepositoryAdapterIT`: upsert a new tenant (ACTIVE) → row
      exists; upsert again (UNINSTALLED) → same row updated; upsert ACTIVE after
      UNINSTALLED → row set back to ACTIVE (covers idempotency scenarios) ←
      (verify: all three scenarios pass, no duplicate rows, `fk_meetings_tenant`
      foreign key not violated)
- [ ] 8.2 `TenantInstalledEventConsumerIT`: publish a valid
      `tenant.tenant.installed` CloudEvent to an embedded Kafka topic →
      `tenants` row upserted with ACTIVE status (covers spec: end-to-end
      consumer flow)
- [ ] 8.3 `TenantUninstalledEventConsumerIT`: publish a valid
      `tenant.tenant.uninstalled` CloudEvent → `tenants` row upserted with
      UNINSTALLED status, `uninstalled_at` and `purge_after` populated (covers
      spec: end-to-end consumer flow)
- [ ] 8.4 `TenantConsumerDltIT`: publish a malformed (non-decodable) message to
      `tenant.tenant.installed` → after max retries, message lands on
      `tenant.tenant.installed.dlt` and partition is unblocked (covers spec:
      "Malformed CloudEvent data is retried then dead-lettered") ← (verify: DLT
      topic receives the message, main topic consumer continues processing
      subsequent messages)
