## Why

The tenant service currently emits partial views of its aggregate: lifecycle
events and HTTP responses each carry an ad-hoc subset of `Tenant` fields.
`TenantInstalledEvent` omits `status`/`updatedAt`/`uninstalledAt`/`purgeAfter`;
`TenantUninstalledEvent` omits all installation metadata and `installedAt`;
responses echo `tenantId` (already supplied by the client via `X-Tenant-ID`) yet
drop most descriptive fields. Consumers and clients therefore cannot reconstruct
the full tenant state from a single event or response. Standardizing on a full
snapshot removes guesswork and repeated lookups.

## What Changes

- Every tenant lifecycle event (`TenantInstalled`, `TenantUninstalled`) carries
  a **full snapshot** of the 12 `Tenant` aggregate fields, including the tenant
  id (as `cloudId`/`aggregateId`). Fields with no value at event time are empty.
  **BREAKING** to the proto contract (new fields added; backward-compatible for
  existing consumers since proto3 fields are additive).
- Every successful mutation response (POST/PUT) returns the **full domain
  snapshot** of the tenant, **excluding** `tenant_id` (the client already knows
  it) and any field marked sensitive. No field is classified sensitive today, so
  responses carry the remaining 11 fields.
- Application results (`RegisterTenantResult`, `UninstallTenantResult`) expand
  to carry the full snapshot so presentation can project it.
- OpenAPI spec (`services/tenant/openapi.yaml`) is regenerated to match.

## Capabilities

### New Capabilities

<!-- none -->

### Modified Capabilities

- `event-driven`: The shared Protocol Buffers event contract requirement is
  extended so that tenant lifecycle events carry the **complete** tenant
  aggregate snapshot rather than an event-specific subset. Applies to both
  `TenantInstalled` and `TenantUninstalled`.
- `api-convention`: The successful-response-body requirement gains a rule that a
  mutation (POST/PUT) response returns the full domain snapshot of the affected
  resource, excluding the tenant identifier (supplied via `X-Tenant-ID`) and any
  field marked sensitive.

## Impact

- **Proto (shared contract)**: `services/proto/.../tenant_installed.proto`,
  `tenant_uninstalled.proto` — new fields; Buf regen.
- **Tenant service**: domain events (`TenantInstalledEvent`,
  `TenantUninstalledEvent`), `Tenant` aggregate event registration, proto
  mappers (`TenantEventProtoMapper`, `TenantUninstalledEventProtoMapper`),
  results (`RegisterTenantResult`, `UninstallTenantResult`),
  `TenantResultMapper`, responses (`TenantResponse`, `UninstallTenantResponse`),
  `TenantController` OpenAPI examples.
- **Spec artifact**: `services/tenant/openapi.yaml` regenerated.
- **Tests**: proto mapper tests, result mapper test, application service tests,
  domain `TenantTest`, controller integration test.
- **Out of scope**: JPA entity and `outbox_event` schema (retain `tenant_id`
  column), `PublishableEvent` interface, transport message key.
