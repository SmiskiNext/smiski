## MODIFIED Requirements

### Requirement: Shared Protocol Buffers event contract

Tenant lifecycle events published to Kafka SHALL be defined by a shared Protocol
Buffers message under the proto module so that the publishing service and any
consuming service reference one authoritative schema. The installation event
SHALL be defined as a `TenantInstalled` message and the uninstallation event as
a `TenantUninstalled` message, both in package
`io.github.smiskinext.event.tenant.v1` (files
`io/github/smiskinext/event/tenant/v1/tenant_installed.proto` and
`tenant_uninstalled.proto`). Each message SHALL carry a **complete snapshot** of
the tenant aggregate at event time — cloudId, installation id, app id, app
version, environment id, site url, installer account id, status, installed
timestamp, updated timestamp, uninstalled timestamp, and purge-after timestamp —
so that a consumer can project the full tenant state from a single event without
a follow-up lookup. Fields that have no value at event time SHALL be encoded as
their proto3 default (empty string). New fields SHALL be added with new field
numbers so the change is backward-compatible for existing consumers. The protos
SHALL satisfy the module's Buf `STANDARD` lint rules.

#### Scenario: Publisher and consumer share one schema

- **WHEN** the tenant service publishes a lifecycle event and a consuming
  service reads it
- **THEN** both use the same generated message type from the shared proto
  module, with no divergent handwritten schema

#### Scenario: Installation event carries the full snapshot

- **WHEN** the tenant service publishes an installation event for an active
  tenant
- **THEN** the `TenantInstalled` message carries all twelve aggregate fields,
  with `status` set to the active status and the uninstalled/purge-after
  timestamps left at their proto3 default because they have no value yet

#### Scenario: Uninstallation event carries the full snapshot

- **WHEN** the tenant service publishes an uninstallation event
- **THEN** the `TenantUninstalled` message carries all twelve aggregate fields,
  including the installation metadata (app version, environment id, site url,
  installer account id) and the installed timestamp captured at install time

#### Scenario: Absent optional value uses proto3 default

- **WHEN** an aggregate field is null at event time (e.g. app version was never
  provided)
- **THEN** the corresponding proto field is the empty string rather than a
  distinct null representation

#### Scenario: Proto passes standard lint

- **WHEN** the proto module is linted
- **THEN** `tenant_installed.proto` and `tenant_uninstalled.proto` pass the Buf
  `STANDARD` ruleset

#### Scenario: Added fields keep existing consumers working

- **WHEN** a consumer built against the previous schema reads an event produced
  with the extended schema
- **THEN** the consumer deserializes successfully because the new fields use new
  field numbers and are ignored by the older generated type
