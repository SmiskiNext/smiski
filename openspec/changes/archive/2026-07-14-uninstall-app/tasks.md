## 1. Proto contract

- [x] 1.1 Add
      `services/proto/src/main/proto/io/github/smiskinext/event/tenant/v1/tenant_uninstalled.proto`
      defining message `TenantUninstalled` (cloud_id, installation_id, app_id,
      uninstalled_at, purge_after)
- [x] 1.2 Run `bufFormatApply` and regenerate proto sources so
      `TenantUninstalled` is available to the tenant service ← (verify:
      `tenant_uninstalled.proto` passes Buf STANDARD lint, generated type
      resolvable)

## 2. Domain

- [x] 2.1 Add `PurgePolicy` port in `tenant.domain.port` exposing the retention
      `Duration`
- [x] 2.2 Add `TenantUninstalledEvent` in `tenant.domain.event` implementing the
      tenant `PublishableEvent` marker (eventType
      `io.github.smiskinext.tenant.v1.uninstalled`, topic
      `tenant.tenant.uninstalled`, aggregateType `tenant`)
- [x] 2.3 Add `Tenant.uninstall(Instant purgeAfter)` — set status `UNINSTALLED`,
      `uninstalledAt = now`, `purgeAfter`, refresh `updatedAt`, register
      `TenantUninstalledEvent`; add `isUninstalled()`
- [x] 2.4 Add `TenantError.TenantNotFound` and
      `TenantErrorCode.TENANT_NOT_FOUND(NOT_FOUND)` ← (verify: error category
      maps to HTTP 404 via ResultResponder)

## 3. Application

- [x] 3.1 Add `UninstallTenantCommand` (cloudId) in `application.command`
- [x] 3.2 Add `UninstallTenantResult` in `application.result` (tenantId, status,
      uninstalledAt, purgeAfter)
- [x] 3.3 Add `UninstallTenantUseCase` interface in `application.usecase`
- [x] 3.4 Extend `TenantResultMapper` to map `Tenant` → `UninstallTenantResult`
- [x] 3.5 Add `UninstallTenantApplicationService` (@Service @Transactional):
      findById → 404 on absent, no-op success on already `UNINSTALLED`, else
      compute `purgeAfter` via `PurgePolicy`, `uninstall`, save, publish
      registered events ← (verify: not-found/no-op/uninstall branches match spec
      scenarios, event published only on real transition)

## 4. Infrastructure

- [x] 4.1 Add `TenantRetentionProperties` record
      (`@ConfigurationProperties("smiski.tenant.retention")`, `@Validated`)
      implementing `PurgePolicy`, default `P30D`
- [x] 4.2 Register the properties via `@EnableConfigurationProperties` (config
      class) so the `PurgePolicy` bean is available
- [x] 4.3 Add `TenantUninstalledEventProtoMapper` in `infrastructure.messaging`
      mapping `TenantUninstalledEvent` → `TenantUninstalled` proto ← (verify:
      mapper auto-registered in OutboxEventProtoMapperRegistry, payload fields
      complete)

## 5. Presentation

- [x] 5.1 Add `UninstallTenantRequest` in `presentation.request` modeling the
      Forge `preUninstall` payload with `toCommand(cloudId)`
- [x] 5.2 Add `UninstallTenantResponse` in `presentation.response` with
      `from(UninstallTenantResult)`
- [x] 5.3 Add `TenantController.uninstall()` — `@DeleteMapping("/tenants")`,
      cloudId from `TenantContext`, Swagger `@Operation`/`@ApiResponses` (200,
      400 missing-context, 404), maps `Result` via `ResultResponder` ← (verify:
      effective route `/api/{version}/tenants`, 200/404/missing-context
      responses match api-convention)

## 6. Resources & config

- [x] 6.1 Add `smiski.tenant.retention.purge-after: P30D` to `application.yaml`
- [x] 6.2 Add `error.tenant-not-found.title`/`detail` to `tenant.properties` and
      `tenant_vi.properties`

## 7. Tests

- [x] 7.1 Domain unit test: `uninstall(...)` sets status/timestamps/purgeAfter
      and registers `TenantUninstalledEvent`; `isUninstalled()` reflects state
- [x] 7.2 Application unit test — active tenant marked `UNINSTALLED`, event
      published, purgeAfter = now + retention (scenarios: "Active tenant is
      marked uninstalled", "Purge deadline computed from retention window")
- [x] 7.3 Application unit test — unknown cloudId returns `TenantNotFound`, no
      save, no publish (scenario: "Uninstall for a nonexistent tenant is 404")
- [x] 7.4 Application unit test — already `UNINSTALLED` returns current
      representation, no state change, no event (scenario: "Redelivered
      preUninstall is a safe no-op")
- [x] 7.5 Proto mapper unit test: `TenantUninstalledEvent` maps to
      `TenantUninstalled` proto with all fields
- [x] 7.6 Controller integration test — `DELETE /tenants` 200
      body/representation, 404 unknown tenant, missing-context Problem Details
      (scenarios: "cloudId is taken from context", "Missing tenant context is
      rejected", "Representation reflects uninstalled state")
- [x] 7.7 Outbox integration test — uninstall enqueues exactly one
      `outbox_event` row for the new topic; rollback leaves none (scenarios:
      "Uninstall enqueues the event atomically", "No event on rolled-back
      uninstall")

## 8. Spec artifacts & verification

- [x] 8.1 Regenerate `services/tenant/openapi.yaml` via
      `generateOpenApiDocsFromTests`
- [x] 8.2 Run `spotlessApply` + `bufFormatApply`, then
      `./services/gradlew -p services/tenant build` (test + integrationTest)
      green ← (verify: all layers compile, ArchUnit passes, OpenAPI reflects the
      new endpoint)
