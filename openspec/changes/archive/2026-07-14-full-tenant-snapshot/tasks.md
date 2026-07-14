## 1. Proto contract (shared)

- [x] 1.1 Extend `tenant_installed.proto`: add `status` (10), `updated_at` (11),
      `uninstalled_at` (12), `purge_after` (13) as strings, keeping `reserved 6`
- [x] 1.2 Extend `tenant_uninstalled.proto`: add `app_version` (6),
      `environment_id` (7), `site_url` (8), `installer_account_id` (9), `status`
      (10), `installed_at` (11), `updated_at` (12) as strings
- [x] 1.3 Regenerate proto sources and confirm Buf `STANDARD` lint passes ←
      (verify: both protos compile and pass `bufFormatApply`/lint; new field
      numbers do not collide with reserved)

## 2. Domain events

- [x] 2.1 Add `status`, `updatedAt`, `@Nullable uninstalledAt`,
      `@Nullable purgeAfter` to `TenantInstalledEvent` record
- [x] 2.2 Add `appVersion`, `environmentId`, `siteUrl`, `installerAccountId`,
      `status`, `installedAt`, `updatedAt` to `TenantUninstalledEvent` record
- [x] 2.3 Update `Tenant.registerInstalledEvent()` to pass all twelve values
      (uninstalled/purge null at install)
- [x] 2.4 Update `Tenant.uninstall()` to pass all twelve values ← (verify:
      events constructed from aggregate carry every field; domain imports no
      proto/Kafka types)

## 3. Infrastructure proto mappers

- [x] 3.1 Update `TenantEventProtoMapper.toProto` to set the four new fields
      (status as enum name; null → proto3 default)
- [x] 3.2 Update `TenantUninstalledEventProtoMapper.toProto` to set the seven
      new fields ← (verify: proto message carries all snapshot fields; null
      values serialize to empty string)

## 4. Application results and mapping

- [x] 4.1 Expand `RegisterTenantResult` to the full 12-field snapshot
- [x] 4.2 Expand `UninstallTenantResult` to the full 12-field snapshot
- [x] 4.3 Update `TenantResultMapper.toResult` and `toUninstallResult` to
      project all fields from `Tenant` ← (verify: mappers read every aggregate
      getter; nullable fields preserved)

## 5. Presentation responses

- [x] 5.1 Remove `tenantId` from `TenantResponse`; add remaining domain fields
      with `@Schema`; update `from(result)`
- [x] 5.2 Remove `tenantId` from `UninstallTenantResponse`; add remaining domain
      fields with `@Schema`; update `from(result)`
- [x] 5.3 Update `TenantController` `@ExampleObject` blocks (201 created, 200
      reinstall, 200 uninstalled) to drop `tenantId` and show full snapshot;
      keep `resultValue.tenantId()` for the Location header ← (verify: register
      201 still emits Location; response bodies contain no `tenantId`)

## 6. Spec artifact

- [x] 6.1 Regenerate `services/tenant/openapi.yaml` via
      `generateOpenApiDocsFromTests` ← (verify: spec reflects new response
      fields and absence of `tenantId`; `TenantResponse` schema name unchanged)

## 7. Tests (per spec scenario)

- [x] 7.1 event-driven "Installation event carries the full snapshot": update
      `TenantEventProtoMapperTest` to assert `status`, `updatedAt` present and
      `uninstalledAt`/`purgeAfter` default
- [x] 7.2 event-driven "Uninstallation event carries the full snapshot": update
      `TenantUninstalledEventProtoMapperTest` to assert install metadata +
      `installedAt` present
- [x] 7.3 event-driven "Absent optional value uses proto3 default": assert null
      domain field → empty proto string in both mapper tests
- [x] 7.4 api-convention "Mutation response returns the full snapshot without
      the tenant id": update `TenantControllerIntegrationTest` to assert no
      `$.tenantId` and presence of new fields for both POST and DELETE responses
- [x] 7.5 Update `TenantResultMapperTest`,
      `RegisterTenantApplicationServiceTest`,
      `UninstallTenantApplicationServiceTest`, and domain `TenantTest` to match
      expanded result/event shapes ← (verify: full fast suite green)

## 8. Verification

- [x] 8.1 Run `./services/gradlew -p services/ tenant test`
- [x] 8.2 Run `./services/gradlew -p services/ tenant integrationTest`
- [x] 8.3 Run `./services/gradlew spotlessApply` and
      `./services/gradlew bufFormatApply` ← (verify: build green, formatting
      clean, openapi.yaml committed)
