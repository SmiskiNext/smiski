# Purpose

TBD: Define the main specification for nullable-free OpenAPI and SDK generation.

# ADDED Requirements

## Requirement: Unified OpenAPI output removes PATCH update contracts

The system SHALL regenerate service OpenAPI documents and the Redocly-joined
`openapi/unified-openapi.yaml` so the unified contract reflects the PUT-only
update API surface.

### Scenario: Unified spec contains PUT-only current-user updates

- **WHEN** `pnpm run openapi:unified` completes after the change
- **THEN** `openapi/unified-openapi.yaml` SHALL include `PUT /api/v1/me` and
  `PUT /api/v1/me/preferences` and SHALL NOT include PATCH operations for those
  resources

### Scenario: Unified spec contains PUT-only meeting settings updates

- **WHEN** `pnpm run openapi:unified` completes after the change
- **THEN** `openapi/unified-openapi.yaml` SHALL include
  `PUT /api/v1/meetings/{id}/settings` and SHALL NOT include the PATCH operation
  for that resource

## Requirement: Unified OpenAPI schemas remove JsonNullable wrappers

The regenerated OpenAPI artifacts SHALL NOT emit component schemas or request
DTO shapes that depend on `JsonNullable`.

### Scenario: JsonNullable component schemas removed

- **WHEN** `openapi/unified-openapi.yaml` is regenerated after PATCH removal
- **THEN** the components section SHALL NOT contain `JsonNullable`-prefixed
  schemas for the migrated endpoints

### Scenario: Request bodies use explicit PUT schema fields

- **WHEN** the unified spec describes the migrated endpoints
- **THEN** the request body schemas SHALL use explicit fields and requiredness
  rules instead of patch-wrapper semantics

## Requirement: Android SDK generation removes JsonNullable support

The Android API client generation and runtime configuration SHALL align with the
new PUT-only OpenAPI contract and SHALL NOT require `JsonNullable` support.

### Scenario: OpenAPI generator disables nullable wrappers

- **WHEN** Android `openApiGenerate` runs after the unified spec is updated
- **THEN** the generator configuration SHALL no longer enable `openApiNullable`
  wrapper generation for the produced DTOs

### Scenario: Android runtime removes JsonNullable module registration

- **WHEN** the Android networking layer configures its `ObjectMapper`
- **THEN** it SHALL NOT register `JsonNullableModule`

### Scenario: Android generated sources contain no migrated PATCH APIs

- **WHEN** the Android SDK is regenerated from the unified spec
- **THEN** the generated API interfaces and DTOs SHALL NOT include the removed
  PATCH update methods or `JsonNullable` wrapper types for the migrated update
  contracts
