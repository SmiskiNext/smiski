# Why

The backend currently exposes PATCH update endpoints that depend on Jackson
`JsonNullable` semantics to distinguish omitted fields from explicit nulls. This
adds serializer-specific coupling across backend, generated OpenAPI clients, and
the Android app, and makes endpoint behavior harder to reason about than a
full-replacement contract.

We need to replace these PATCH contracts with PUT endpoints that use explicit,
fully-defined request bodies, remove `JsonNullable` from the affected services
and SDK generation pipeline, and preserve all existing domain events and update
logic so downstream systems remain consistent.

## What Changes

- **BREAKING** Remove `PATCH /api/v1/me` and keep `PUT /api/v1/me` as the only
  profile replacement endpoint
- **BREAKING** Remove `PATCH /api/v1/me/preferences` and add
  `PUT /api/v1/me/preferences` with raw-map full replacement semantics
- **BREAKING** Remove `PATCH /api/v1/meetings/{id}/settings` and add
  `PUT /api/v1/meetings/{id}/settings` with a fully-defined request contract
- Preserve existing domain behavior during migration, including user profile
  update events, meeting settings update events, and automatic approval of
  pending join requests when LIVE meeting access becomes more permissive
- Remove `jackson-databind-nullable` and `JsonNullable` usage from affected
  backend services, shared Jackson configuration, Android networking, ProGuard,
  and generated OpenAPI SDKs
- Regenerate service OpenAPI documents, rebuild the Redocly-joined unified spec,
  and regenerate Android API clients against the new PUT-only contracts

## Capabilities

### New Capabilities

- `current-user-replacement-api`: Replace current-user profile and preferences
  updates with PUT-only contracts that do not rely on `JsonNullable`
- `meeting-settings-replacement-api`: Replace meeting settings updates with a
  PUT-only contract that preserves event publication and meeting access logic
- `nullable-free-openapi-sdk`: Regenerate backend OpenAPI artifacts and Android
  SDKs without PATCH-only update contracts or `JsonNullable` wrappers

### Modified Capabilities

_(none - these API requirements are not yet defined in existing specs)_

## Impact

**Affected backend code:**

- `services/user-management/`: controllers, request/command/use case classes,
  tests, OpenAPI generation, and Jackson nullable dependency usage
- `services/meeting-management/`: controller, request/command/use case classes,
  tests, and Jackson nullable dependency usage
- `services/shared/`: shared Jackson configuration if it still registers
  `JsonNullable` support

**Affected clients and API artifacts:**

- `openapi/unified-openapi.yaml`: regenerated via Redocly join with PATCH
  contracts removed and PUT contracts added/updated
- `frontends/android-app/`: regenerated OpenAPI SDK, Retrofit/Jackson setup,
  ProGuard rules, and any compile-time references to removed PATCH models

**Dependencies and tooling:**

- Remove `jackson-databind-nullable` from affected modules once PATCH contracts
  are removed
- Update Android OpenAPI generator settings so generated DTOs no longer emit
  `JsonNullable` wrappers
- Verification must include backend tests, Android tests/builds, Spotless, and
  `pnpm run openapi:unified`
