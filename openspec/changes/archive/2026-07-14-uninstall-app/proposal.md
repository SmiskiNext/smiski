## Why

When a Forge app is uninstalled, Atlassian fires a `preUninstall` life-cycle
trigger, but the tenant service has no endpoint to record it: the `Tenant`
aggregate already carries `uninstalledAt`, `purgeAfter`, and an `UNINSTALLED`
status that nothing ever sets, and no downstream service is told the tenant is
gone. This change closes the install/uninstall loop so tenant data can be marked
for scheduled purge and other services can clean up their own projections.

## What Changes

- Add `DELETE /tenants` that records a Forge app uninstall. The cloudId is
  resolved from the tenant context (`X-Tenant-ID` header); the request body is
  the Forge `preUninstall` payload.
- Mark the tenant `UNINSTALLED`, stamp `uninstalledAt = now`, and compute
  `purgeAfter = now + retention` from a configurable retention window.
- Publish a new `TenantUninstalled` domain event through the transactional
  outbox (new proto message + topic `tenant.tenant.uninstalled`) so downstream
  services can react.
- Return `200 OK` with the tenant representation (including `status`,
  `uninstalledAt`, `purgeAfter`) on success; `404` when the cloudId has no
  tenant; idempotent `200` no-op (no re-publish) when the tenant is already
  `UNINSTALLED`.
- Introduce a configurable purge retention window
  (`smiski.tenant.retention.purge-after`, ISO-8601 `Duration`, default `P30D`)
  wrapped in a properties record exposed to the domain via a port.

## Capabilities

### New Capabilities

- `uninstall-app`: Records Forge app uninstalls for a tenant — the
  `DELETE /tenants` endpoint, status transition to `UNINSTALLED`, purge-window
  scheduling, idempotency/not-found semantics, and the publishable
  `TenantUninstalled` event.

### Modified Capabilities

<!-- No spec-level requirement changes to existing capabilities. -->

## Impact

- **tenant service — domain**: `Tenant.uninstall(...)`, `isUninstalled()`; new
  `TenantUninstalledEvent`; new `PurgePolicy` port; `TenantError.TenantNotFound`
    - `TenantErrorCode.TENANT_NOT_FOUND`.
- **tenant service — application**: `UninstallTenantCommand`,
  `UninstallTenantResult`, `UninstallTenantUseCase`,
  `UninstallTenantApplicationService`.
- **tenant service — infrastructure**: `TenantRetentionProperties`
  (`@ConfigurationProperties`) implementing `PurgePolicy`;
  `TenantUninstalledEventProtoMapper`.
- **tenant service — presentation**: `UninstallTenantRequest`,
  `UninstallTenantResponse`, `TenantController.uninstall()`.
- **proto**: new `tenant_uninstalled.proto` (`TenantUninstalled` message).
- **resources**: `application.yaml` retention key; `tenant.properties` /
  `tenant_vi.properties` not-found messages; regenerated `openapi.yaml`.
- **No DB migration**: `uninstalled_at`, `purge_after`, and the `UNINSTALLED`
  status check already exist in `B1.0.0__baseline.sql`.
