## ADDED Requirements

### Requirement: Record app uninstall endpoint

The tenant service SHALL expose `DELETE /tenants` that records a Forge app
uninstall. The request body SHALL be the Forge `preUninstall` life-cycle event
payload. The tenant identifier (Jira cloudId) SHALL be resolved from the tenant
context bound from the `X-Tenant-ID` request header and SHALL NOT be read from
the request body. On success the response body SHALL be the tenant
representation, following the api-convention successful-response and versioned
URL requirements (effective route `/api/{version}/tenants`).

#### Scenario: Active tenant is marked uninstalled

- **WHEN** a `DELETE /tenants` request arrives with an `X-Tenant-ID` header
  whose cloudId maps to a tenant whose `status` is `ACTIVE`
- **THEN** the tenant's `status` becomes `UNINSTALLED`, `uninstalled_at` is set
  to the current time, `updated_at` is refreshed, the response status is
  `200 OK`, and the body is the tenant representation

#### Scenario: cloudId is taken from context, not the body

- **WHEN** a `DELETE /tenants` request carries a `preUninstall` payload and the
  `X-Tenant-ID` header holds the cloudId
- **THEN** the tenant identified by the header cloudId is the one marked
  uninstalled, regardless of any identifier present in the body

#### Scenario: Missing tenant context is rejected

- **WHEN** a `DELETE /tenants` request arrives without a resolvable tenant (the
  context holds the default `system` tenant because no `X-Tenant-ID` header was
  provided)
- **THEN** the response is a Problem Details body with a machine-readable `code`
  indicating the tenant context is missing and no tenant row is modified

### Requirement: Unknown tenant returns not found

Recording an uninstall for a cloudId that has no persisted tenant SHALL fail
with a not-found domain error. The response SHALL follow the api-convention
Problem Details semantics with HTTP status `404` and a stable machine-readable
`code`. No tenant row SHALL be created and no event SHALL be published.

#### Scenario: Uninstall for a nonexistent tenant is 404

- **WHEN** a `DELETE /tenants` request arrives with an `X-Tenant-ID` header
  whose cloudId has no existing tenant row
- **THEN** the response is `404` Problem Details with a machine-readable `code`
  identifying the tenant as not found, and no `outbox_event` row is written

### Requirement: Idempotent uninstall on already-uninstalled tenant

Recording an uninstall SHALL be idempotent for a tenant that is already
`UNINSTALLED`. When the tenant's `status` is already `UNINSTALLED`, the endpoint
SHALL return `200 OK` with the current tenant representation without modifying
`uninstalled_at`, `purge_after`, or `updated_at`, and SHALL NOT publish another
uninstall event.

#### Scenario: Redelivered preUninstall is a safe no-op

- **WHEN** a `DELETE /tenants` request arrives for a cloudId whose tenant is
  already `UNINSTALLED`
- **THEN** the response is `200 OK` with the tenant representation, the stored
  `uninstalled_at` and `purge_after` are unchanged, and no new `outbox_event`
  row is written

### Requirement: Purge window scheduling

When an active tenant is marked uninstalled, the service SHALL compute and store
`purge_after` as the current time plus a configurable retention window. The
retention window SHALL be supplied through an externalized, validated
configuration value expressed as an ISO-8601 duration, wrapped in a dedicated
properties type and exposed to the application through a domain port so the
domain remains free of framework configuration types. When the configuration is
absent the retention window SHALL default to 30 days (`P30D`).

#### Scenario: Purge deadline computed from retention window

- **WHEN** an `ACTIVE` tenant is marked uninstalled at time `T` and the
  configured retention window is duration `D`
- **THEN** the stored `purge_after` equals `T + D`

#### Scenario: Default retention applies when unconfigured

- **WHEN** no retention window is configured and a tenant is uninstalled at time
  `T`
- **THEN** the stored `purge_after` equals `T + P30D`

### Requirement: TenantUninstalled event contract

Uninstalling a tenant SHALL register a publishable `TenantUninstalled` domain
event enqueued to the transactional outbox in the same transaction as the state
change, satisfying the event-driven transactional-outbox and CloudEvent
requirements. The event SHALL be defined by a shared Protocol Buffers message
`TenantUninstalled` in package `io.github.smiskinext.event.tenant.v1` (file
`io/github/smiskinext/event/tenant/v1/tenant_uninstalled.proto`) that passes the
Buf `STANDARD` ruleset. The event SHALL carry the cloudId, installation id, app
id, `uninstalled_at`, and `purge_after`. It SHALL use a stable versioned event
type `io.github.smiskinext.tenant.v1.uninstalled` and topic
`tenant.tenant.uninstalled`, and SHALL be keyed by the tenant cloudId. The
domain event type SHALL import no Protocol Buffers or messaging types; mapping
to the proto contract SHALL happen at the infrastructure boundary through a
registered per-event-type mapper.

#### Scenario: Uninstall enqueues the event atomically

- **WHEN** an active tenant is marked uninstalled and its transaction commits
- **THEN** exactly one `outbox_event` row exists with `published_at` null,
  `event_type` the versioned uninstalled identifier, `topic`
  `tenant.tenant.uninstalled`, and `aggregate_id` equal to the cloudId

#### Scenario: No event on rolled-back uninstall

- **WHEN** the uninstall transaction is rolled back before commit
- **THEN** no `outbox_event` row for that uninstall exists

#### Scenario: Proto passes standard lint

- **WHEN** the proto module is linted
- **THEN** `tenant_uninstalled.proto` passes the Buf `STANDARD` ruleset

### Requirement: Uninstalled tenant representation

The tenant representation returned by the uninstall endpoint SHALL expose the
tenant's identity and uninstall lifecycle without a wrapping envelope. It SHALL
include the `tenantId` (cloudId), `status`, `uninstalledAt`, and `purgeAfter`,
and MAY include the remaining stored tenant metadata. It SHALL NOT expose fields
that are not persisted.

#### Scenario: Representation reflects uninstalled state

- **WHEN** an active tenant is successfully marked uninstalled
- **THEN** the returned representation's `tenantId` equals the context cloudId,
  `status` is `UNINSTALLED`, and `uninstalledAt` and `purgeAfter` match the
  persisted row

#### Scenario: Representation carries no envelope

- **WHEN** the endpoint returns the tenant representation
- **THEN** the body is the representation object itself, not wrapped in a
  data/result envelope
