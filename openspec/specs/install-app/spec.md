# install-app Specification

## Purpose

TBD - created by archiving change add-tenant-install-app. Update Purpose after
archive.

## Requirements

### Requirement: Record app installation endpoint

The tenant service SHALL expose `POST /tenants` that records a Forge app
installation. The request body SHALL be the Forge `avi:forge:installed:app`
event payload. The tenant identifier (Jira cloudId) SHALL be resolved from the
tenant context bound from the `X-Tenant-ID` request header and SHALL NOT be read
from the request body. On success the response body SHALL be the tenant
representation, following the api-convention successful-response and versioned
URL requirements (effective route `/api/{version}/tenants`).

#### Scenario: First installation creates the tenant

- **WHEN** a `POST /tenants` request arrives with a valid install payload and an
  `X-Tenant-ID` header whose cloudId has no existing tenant row
- **THEN** a new tenant is persisted with `status` `ACTIVE`, the response status
  is `201 Created`, the `Location` header points to the created tenant, and the
  body is the tenant representation

#### Scenario: cloudId is taken from context, not the body

- **WHEN** a `POST /tenants` request carries an install payload whose body
  contains no tenant identifier, and the `X-Tenant-ID` header holds the cloudId
- **THEN** the persisted tenant's identifier equals the header cloudId and the
  installation is recorded against that tenant

#### Scenario: Missing tenant context is rejected

- **WHEN** a `POST /tenants` request arrives without a resolvable tenant (the
  context holds the default `system` tenant because no `X-Tenant-ID` header was
  provided)
- **THEN** the response is a Problem Details body with a machine-readable `code`
  indicating the tenant context is missing and no tenant row is written

### Requirement: Idempotent installation upsert

Recording an installation SHALL be idempotent on the cloudId. When a tenant
already exists for the cloudId, the endpoint SHALL update the existing row
rather than create a duplicate or fail: it SHALL store the new
`installation_id`, set `status` to `ACTIVE` (reactivating a previously
uninstalled tenant), and refresh the mutable installation metadata and
`updated_at`. The primary key (cloudId) and `installed_at` SHALL be preserved. A
publishable installation event SHALL be emitted for both create and update
outcomes.

#### Scenario: Redelivery of the same installation is safe

- **WHEN** the same install event is delivered twice for one cloudId (Forge
  retry)
- **THEN** the second request updates the existing tenant in place, returns
  `200 OK` with the tenant representation, and does not create a second row

#### Scenario: Reinstall updates installation id and reactivates

- **WHEN** an install event arrives for an existing cloudId whose `status` is
  `UNINSTALLED`, carrying a new `installation_id`
- **THEN** the tenant's `installation_id` is replaced, `status` becomes
  `ACTIVE`, `updated_at` is refreshed while `installed_at` is unchanged, and the
  response status is `200 OK`

#### Scenario: Update outcome still emits the event

- **WHEN** an install event updates an already-existing tenant
- **THEN** a `TenantInstalled` publishable event is registered and enqueued to
  the outbox in the same transaction, exactly as for a first install

### Requirement: Install payload validation

The request body SHALL be validated as the Forge install payload before
persistence. The installation `id` and the nested `app.id` SHALL be required and
non-blank; `app.version` SHALL be required. Optional members
(`installerAccountId`, `app.name`, `app.ownerAccountId`, `environment.id`,
`environmentType`, `siteUrl`) MAY be absent. When `environmentType` is absent it
SHALL default to `PRODUCTION`. Validation failures SHALL be reported per the
api-convention validation-failure semantics (`VALIDATION_ERROR`, HTTP `400`,
per-field `errors`).

#### Scenario: Missing required installation id is rejected

- **WHEN** a `POST /tenants` request body omits the installation `id`
- **THEN** the response is `400` Problem Details with `code` `VALIDATION_ERROR`
  and an `errors` entry for the `id` field with code `REQUIRED`

#### Scenario: Missing required app id is rejected

- **WHEN** the install payload omits `app.id`
- **THEN** the response is `400` Problem Details with `code` `VALIDATION_ERROR`
  and an `errors` entry identifying the app id field with code `REQUIRED`

#### Scenario: Absent environment type defaults to PRODUCTION

- **WHEN** a valid install payload omits `environmentType`
- **THEN** the tenant is persisted with `environment_type` `PRODUCTION`

#### Scenario: Optional members may be omitted

- **WHEN** a valid install payload omits all optional members and includes only
  the required `id`, `app.id`, and `app.version`
- **THEN** the installation is recorded successfully and the optional columns
  are stored as null (except defaulted `environment_type`)

### Requirement: Tenant representation

The tenant representation returned by the endpoint SHALL expose the tenant's
identity and lifecycle without wrapping envelope. It SHALL include the
`tenantId` (cloudId), `installationId`, `appId`, `status`, `environmentType`,
and `installedAt`, and MAY include the remaining stored installation metadata.
It SHALL NOT expose fields that are not persisted.

#### Scenario: Representation reflects persisted state

- **WHEN** an installation is recorded successfully
- **THEN** the returned representation's `tenantId` equals the context cloudId,
  `status` is `ACTIVE`, and `installationId`, `appId`, `environmentType`, and
  `installedAt` match the persisted row

#### Scenario: Representation carries no envelope

- **WHEN** the endpoint returns the tenant representation
- **THEN** the body is the representation object itself, not wrapped in a
  data/result envelope
