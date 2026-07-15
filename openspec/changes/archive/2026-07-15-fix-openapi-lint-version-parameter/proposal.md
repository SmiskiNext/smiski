## Why

`pnpm openapi:lint` fails with three blocking `path-parameters-defined` errors:
every versioned path emitted by springdoc (`/api/{version}/tenants`,
`/api/{version}/meetings:instant`) contains the `{version}` template variable
but no matching parameter definition, because the prefix is injected at runtime
by `ApiPathPrefixAutoConfiguration` rather than declared as a `@PathVariable`.
Tenant response examples also emit `null` for fields the schema declares as
non-nullable `string`, producing `no-invalid-media-type-examples` warnings. The
lint gate must pass so `pnpm run openapi` stays green in CI and pre-commit.

## What Changes

- Add a shared `GlobalOpenApiCustomizer` in the `shared` module that injects an
  integer `version` path parameter into every emitted path containing the
  `{version}` template variable, applied uniformly across all services.
- Register the customizer via a new shared auto-configuration entry (mirroring
  the existing `OpenApiProblemDetailAutoConfiguration` pattern).
- Mark the nullable fields of `TenantResponse` and `UninstallTenantResponse`
  with `@Schema(nullable = true)` so their `null` examples conform to the
  emitted schema.
- Regenerate the tenant/meet/record OpenAPI documents and confirm the lint gate
  passes with zero errors.

No API contract, URL shape, or versioning strategy changes. The record service's
`ProblemDetail` unused-component warning is explicitly out of scope.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `openapi`: The emitted OpenAPI document SHALL define the `{version}` path
  parameter for every versioned operation, and nullable response fields SHALL be
  marked nullable in the emitted schema so documented `null` examples validate.

## Impact

- **New files** (`services/shared`): a `GlobalOpenApiCustomizer` implementation
  and its auto-configuration, plus one line in
  `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
- **Modified files** (`services/tenant`): `TenantResponse`,
  `UninstallTenantResponse` response DTOs.
- **Regenerated artifacts**: `services/tenant/openapi.yaml`,
  `services/meet/openapi.yaml`, `services/record/openapi.yaml`.
- **Tooling**: `pnpm run openapi:lint` (redocly), `generateOpenApiDocsFromTests`
  Gradle task. No runtime behavior, no database, no external dependency changes.
