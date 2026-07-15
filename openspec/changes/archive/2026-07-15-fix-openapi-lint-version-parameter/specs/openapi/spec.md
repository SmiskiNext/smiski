## ADDED Requirements

### Requirement: Version path parameter defined for versioned operations

The emitted OpenAPI document SHALL declare a `version` path parameter for every
operation whose emitted path contains the `{version}` template variable. The
parameter SHALL be `in: path`, `name: version`, `required: true`, and typed as
an integer, so the emitted document is self-consistent and passes
`path-parameters-defined` validation. This parameter SHALL be attached uniformly
across all services without per-controller declaration, matching the shared
runtime path prefix `/api/{version}`.

#### Scenario: Version parameter present on a versioned operation

- **WHEN** a service OpenAPI document is generated for a path containing
  `{version}` (e.g. `/api/{version}/tenants`)
- **THEN** the operation declares a `version` path parameter with `in: path`,
  `required: true`, and an integer schema

#### Scenario: No per-controller duplication of the version parameter

- **WHEN** a new controller operation under the versioned prefix is added to a
  service
- **THEN** the emitted `version` path parameter appears without the controller
  declaring a `@PathVariable` or repeating the `{version}` template

#### Scenario: Non-versioned paths are not given a version parameter

- **WHEN** the document contains a path without the `{version}` template
  variable (e.g. framework or actuator paths)
- **THEN** no `version` path parameter is added to that path's operations

### Requirement: Nullable response fields marked nullable in the emitted schema

Response fields that may be absent SHALL be marked nullable in the emitted
schema so that documented `null` example values conform to the schema and pass
`no-invalid-media-type-examples` validation. A documented example that supplies
`null` for such a field SHALL NOT produce a schema-conformance violation.

#### Scenario: Nullable tenant field validates its null example

- **WHEN** the tenant registration or uninstall response schema is generated for
  an optional field (such as `environmentId`, `siteUrl`, `installerAccountId`,
  `uninstalledAt`, or `purgeAfter`)
- **THEN** the field's schema is marked nullable and a documented example that
  sets the field to `null` conforms to the schema

#### Scenario: Non-nullable field still rejects a null example

- **WHEN** a response field that is always present (such as `installationId` or
  `status`) is generated
- **THEN** the field's schema is not marked nullable, so a `null` example for it
  would be reported as a schema-conformance violation
