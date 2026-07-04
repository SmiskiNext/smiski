# Context

The current backend update surface mixes two API styles:

- `PUT /api/v1/me` already exists for full user profile replacement
- `PATCH /api/v1/me`, `PATCH /api/v1/me/preferences`, and
  `PATCH /api/v1/meetings/{id}/settings` still depend on Jackson `JsonNullable`
  wrappers to represent omitted versus explicit-null fields

This creates cross-cutting coupling across Spring request DTOs, shared Jackson
configuration, Redocly-generated OpenAPI documents, Android SDK generation, and
Android Retrofit object mapping. It also keeps multiple update contracts for the
same resource family alive at once.

The change spans multiple modules:

- `services/user-management`
- `services/meeting-management`
- `services/shared`
- `openapi/unified-openapi.yaml`
- `frontends/android-app`

The design must preserve behavioral integrity while removing PATCH-only
serialization semantics. Two areas are especially sensitive:

- User profile updates publish `UserUpdatedEvent`, consumed by
  `meeting-management` to update active participant profiles
- Meeting settings updates publish `MeetingSettingsUpdatedEvent` and may trigger
  `PendingJoinRequestApprover.approveAll(...)` when a LIVE meeting becomes more
  permissive

## Goals / Non-Goals

**Goals:**

- Replace PATCH update contracts with PUT-only contracts for current-user update
  endpoints and meeting settings updates
- Remove `JsonNullable` request models, commands, serializers, and generated DTO
  wrappers from the affected backend services and Android app
- Preserve existing domain event publication and downstream behavior when the
  underlying update semantics move from patch-merge to full replacement
- Regenerate service OpenAPI documents, rebuild the Redocly-joined unified spec,
  and regenerate Android SDKs from the new contracts
- Keep validation explicit at the presentation boundary and business rules in
  the use-case layer

**Non-Goals:**

- Introducing versioned replacement endpoints (the paths remain the same where a
  PUT endpoint already exists, or switch from PATCH to PUT on the same resource)
- Preserving backward compatibility for existing PATCH clients in this change
- Changing JSend response envelopes or API version routing
- Redesigning unrelated Android screens or web client behavior
- Changing the contents of published domain events beyond what already exists

## Decisions

### 1. Remove PATCH endpoints instead of deprecating them

**Decision**: Remove PATCH endpoints in the same change that introduces or keeps
their PUT replacements.

**Rationale**:

- The user explicitly chose a breaking change rather than a deprecation period
- Keeping PATCH endpoints alive would force backend and Android to continue
  carrying `JsonNullable` support
- Removing PATCH immediately makes cleanup objective and testable: no PATCH
  paths, no `JsonNullable` runtime registration, no generated wrappers

**Alternatives considered**:

- Deprecate PATCH first, remove later: rejected because `JsonNullable` support
  would still be required in shared Jackson config, Android `ObjectMapper`, and
  generated SDKs

### 2. Current-user profile and preferences use full replacement semantics

**Decision**:

- `PUT /api/v1/me` remains the only profile update endpoint
- `PUT /api/v1/me/preferences` replaces the whole preferences object using a raw
  `Map<String, Object>` body shape

**Rationale**:

- `PUT /api/v1/me` already implements the desired semantics and event flow
- Preferences are stored as generic JSON and currently accept arbitrary keys, so
  a raw map keeps the contract flexible without wrapper-specific semantics
- Replacing the entire preferences document is easier to reason about than merge
  behavior once PATCH is removed

**Behavioral rules**:

- `{}` means clear all stored preferences
- request body `null` is rejected at the presentation boundary as invalid input
- profile updates continue to publish `UserUpdatedEvent` through the existing
  `UpdateUserUseCase`
- preferences updates remain persistence-only and do not introduce a new domain
  event

**Alternatives considered**:

- Wrapper body `{ "settings": { ... } }`: rejected because the existing PATCH
  endpoint already accepts a raw JSON object and the user chose raw-map PUT
- Keeping merge semantics on PUT: rejected because it would preserve patch-like
  behavior under a different verb

### 3. Meeting settings PUT uses a full contract with explicit password behavior

**Decision**: Replace patch-style meeting settings updates with a PUT contract
that requires the full settings payload and includes nullable password input.

**Contract**:

- All meeting settings fields are supplied on every PUT request
- `joinRequestTimeoutSeconds = null` clears the timeout
- `password = null` clears the existing meeting password
- non-null `password` updates the password and is hashed in the use case before
  persisting

**Rationale**:

- The existing `MeetingSettingsRequest` already models full settings for create
  and schedule flows, including optional raw password input
- Password handling cannot be left implicit because the current PATCH endpoint
  intentionally preserves `passwordHash`
- An explicit nullable password field gives a complete and testable replacement
  contract: null clears, non-null updates

**Use-case implications**:

- The current `UpdateMeetingSettingsUseCase` is PATCH-specific and should not be
  reused as-is because it depends on `JsonNullable` and merge logic
- The replacement use case must still:
    - enforce host-only authorization
    - enforce SCHEDULED/LIVE-only status updates
    - enforce `maxParticipants` ceiling rules
    - block `maxParticipants` changes when the effective admission policy is
      `ALLOW_ALL`
    - publish `MeetingSettingsUpdatedEvent` via `meeting.updateSettings(...)`
    - auto-approve pending join requests for LIVE meetings when access becomes
      more permissive

**Alternatives considered**:

- Reuse `UpdateMeetingSettingsUseCase` with optional defaults: rejected because
  it preserves PATCH semantics and keeps `JsonNullable` in the application layer
- Preserve password when field is omitted: rejected because PUT requires a full
  payload and the user explicitly defined null as clear

### 4. Prefer reusing existing full request patterns where possible

**Decision**: Reuse existing full-replacement patterns instead of introducing a
second abstraction style.

**Rationale**:

- `PutUserRequest` + `UpdateUserUseCase` already establish the target style in
  `user-management`
- `MeetingSettingsRequest` already captures full settings plus raw password for
  creation flows, so the update path should align closely with that model rather
  than inventing another representation unless controller mapping forces a
  dedicated request type
- The repo follows request → command → use case layering; the replacement
  endpoints should continue that separation instead of passing DTOs directly

### 5. Remove JsonNullable support across backend, Redocly output, and Android

**Decision**: Remove `JsonNullable` support from runtime configuration and SDK
generation once PATCH endpoints are gone.

**Required cleanup**:

- Remove `Patch*` request/command/use case classes in `user-management`
- Remove patch-specific meeting settings request/command/use case classes in
  `meeting-management`
- Remove `JsonNullableJackson3Module` bean from
  `services/shared/.../JacksonConfig.java`
- Remove `jackson-databind-nullable` dependencies from affected service modules
- Regenerate OpenAPI service specs and Redocly-joined unified spec so no PATCH
  operations or `JsonNullable` schemas remain
- Update Android `openApiGenerate` config to disable nullable wrappers and
  remove `JsonNullableModule` registration from `NetworkModule`
- Remove Android ProGuard rules that preserve
  `org.openapitools.jackson.nullable`

**Rationale**:

- Runtime cleanup is only safe once API contracts and generated DTOs stop
  referencing `JsonNullable`
- The OpenAPI and Android layers are part of the same migration, not follow-up
  cleanup

### 6. Migrate tests to PUT-first behavior and preserve integrity checks

**Decision**: Convert existing PATCH-oriented tests to PUT-oriented tests and
add coverage where patch-specific behavior disappears.

**Required coverage changes**:

- Migrate integration tests hitting `/me`, `/me/preferences`, and
  `/meetings/{id}/settings` from PATCH to PUT
- Replace patch-specific unit tests with PUT-oriented use-case tests
- Add or update tests that verify:
    - full preferences replacement using a raw JSON object
    - `password = null` clears meeting password
    - non-null password is hashed before persistence
    - `MeetingSettingsUpdatedEvent` still originates from the meeting aggregate
    - `PendingJoinRequestApprover` still runs on the same LIVE transition rules
    - OpenAPI generation no longer emits PATCH operations or `JsonNullable`
      component schemas

## Risks / Trade-offs

- **Breaking existing PATCH clients** → Mitigate by making the change explicit
  in proposal/specs, regenerating SDKs immediately, and validating Android
  compile output after regeneration
- **Behavior drift when replacing patch-merge logic with full replacement** →
  Mitigate by encoding exact PUT semantics in specs and replacing patch-oriented
  tests with full replacement tests
- **Meeting password regressions** → Mitigate by reusing existing password
  hashing patterns from create/schedule flows and adding explicit tests for
  clear/update scenarios
- **Loss of domain-side side effects during refactor** → Mitigate by requiring
  the replacement meeting settings use case to call
  `meeting.updateSettings(...)` and preserve LIVE access-opening approval logic
- **OpenAPI/SDK cleanup misses stale nullable schemas** → Mitigate by verifying
  generated service specs, Redocly join output, and Android generated sources
  for absence of PATCH methods and `JsonNullable`
- **Shared Jackson bean removal affects unrelated endpoints** → Mitigate by
  searching for all remaining `JsonNullable` usage before deleting the shared
  bean and dependencies

## Migration Plan

1. Replace PATCH controller methods and patch-specific request/command/use case
   classes in `user-management`
2. Replace PATCH controller method and patch-specific request/command/use case
   classes in `meeting-management`
3. Port tests from PATCH behavior to PUT behavior and add missing integrity
   coverage around password handling and auto-approval logic
4. Remove shared/backend `JsonNullable` dependencies and runtime registration
5. Run service OpenAPI generation tasks and `pnpm run openapi:unified`
6. Regenerate Android SDK, remove Android `JsonNullable` runtime/config support,
   and verify builds/tests

**Rollback strategy**:

- Revert the change as a single unit if downstream SDKs or clients cannot move
  to PUT immediately
- Because the change removes endpoints and DTO types, partial rollout is not
  advised; backend and generated client artifacts must move together

## Open Questions

- None. The remaining semantic decisions were resolved during exploration:
  preferences use raw-map full replacement, meeting password uses null-to-clear,
  and PATCH endpoints are removed in this change.
