# Context

The current meeting-settings contract is spread across backend domain records,
REST DTOs, JSONB persistence DTOs, generated OpenAPI schemas, Android request
mapping, and Android schedule UI controls. That contract still reflects an older
model with timeout-based join approval, explicit recording flags, and a
three-value `screenShareMode`, which now adds unnecessary complexity for both
backend maintenance and Android form design.

This refactor is intentionally cross-cutting and breaking:

- backend `MeetingSettings` and all mappings must change together
- `openapi/unified-openapi.yaml` must be updated so generated Android DTOs match
- Android schedule creation code and layout must remove obsolete inputs and send
  the new booleans
- existing specs for meeting-settings replacement and Android meeting creation
  must be updated to describe the new contract

The system is not yet in production, so no backward-compatibility shim or
database migration is required. The main constraint is internal consistency:
every layer must converge on the same simplified shape and preserve current
authorization, validation, password hashing, and event behavior.

## Goals / Non-Goals

**Goals:**

- Simplify the canonical `MeetingSettings` model to the new field set:
  `admissionPolicy`, `allowGuest`, `maxParticipants`, `allowScreenShare`,
  `chatEnabled`, `allowMicrophone`, `allowVideo`, and nullable `password`
- Remove obsolete fields from backend code, OpenAPI schemas, Android mapping,
  and Android schedule UI in one coordinated change
- Preserve existing meeting-settings replacement semantics that still matter,
  including host-only authorization, status restrictions, participant ceiling
  checks, password hashing, and update event publication
- Keep password input named `password` at domain and API boundaries while still
  storing a hash internally in persistence
- Align Android defaults with the requested baseline: `allowScreenShare=true`,
  `allowMicrophone=true`, `allowVideo=true`, `chatEnabled=true`,
  `maxParticipants=100`, `allowGuest=true`
- Update automated tests and spec artifacts so the new contract is the only
  documented and verified shape

**Non-Goals:**

- Preserving compatibility for existing clients that still use removed fields
- Adding a database migration or data backfill for old persisted settings JSON
- Redesigning unrelated meeting-management APIs or Android screens beyond the
  schedule settings flow
- Changing host-video local behavior on Android, which remains separate from the
  backend meeting settings contract
- Introducing a richer permissions model for media/screen sharing beyond the new
  boolean toggles

## Decisions

### D1: Make the simplified record the single source of truth

**Decision:** Refactor the backend domain `MeetingSettings` record first, then
derive all DTO, persistence, and OpenAPI changes from that shape.

**Rationale:** The domain value object is the narrowest canonical definition of
meeting settings in the service. If request/response schemas or Android models
change without first anchoring the domain record, drift will reappear across
repository mapping and tests.

**Implications:**

- Remove `joinRequestTimeout`, `muteOnEntry`, and `recordingEnabled`
- Replace `screenShareMode` constants and validation with a single
  `allowScreenShare` boolean
- Rename domain field `passwordHash` to `password` so DTOs and the value object
  use the same term, even though persistence still stores a hash value
- Update `defaults()` to reflect the new requested defaults

**Alternatives considered:**

- Keep the old domain record and translate only at API boundaries → rejected
  because it would preserve unnecessary complexity internally and make mapping
  logic harder to reason about
- Introduce a second record just for API simplification → rejected because the
  service only needs one meeting-settings model

### D2: Treat `password` as an API/domain name, not a storage-format promise

**Decision:** Use `password` consistently in request, domain, response-adjacent,
and persistence-mapping code, while continuing to hash the raw request value
before persistence.

**Rationale:** The rename is intended to simplify the model presented to callers
and internal collaborators. Retaining `passwordHash` in the domain record would
continue leaking a storage concern into the business model. The use case already
owns hashing behavior, so internal storage safety can remain unchanged while the
record field name becomes easier to understand.

**Implications:**

- Request DTO still carries nullable raw `password`
- Use case continues hashing non-null input before saving the aggregate
- Response DTO should continue exposing only `requirePassword`, not the password
  value itself
- Persistence JSON may still physically contain a hashed string under the new
  `password` property because no migration/backward compatibility is required

**Alternatives considered:**

- Keep `passwordHash` in domain and only rename the API field → rejected because
  it preserves naming mismatch between layers
- Expose hashed password in responses for symmetry → rejected as a security and
  contract smell

### D3: Replace enum-like screen share modes with participant permission boolean

**Decision:** Replace `screenShareMode = ALL|HOST_ONLY|DISABLED` with
`allowScreenShare` where host sharing is always implicit and the boolean only
controls participant sharing.

**Rationale:** The requested product rule makes the old tri-state contract
unnecessary. A boolean more directly models the actual decision clients need to
make and removes fragile string validation from backend and Android.

**Implications:**

- Backend validation no longer needs screen-share constants/pattern matching
- OpenAPI request/response schemas become simpler
- Android schedule UI changes from a mode selector to a simple toggle
- Specs must state that host sharing remains available regardless of the boolean

**Alternatives considered:**

- Keep the enum and reinterpret values internally → rejected because it would
  keep obsolete API surface alive
- Add separate booleans for host and participant sharing → rejected because host
  sharing is now fixed implicit behavior

### D4: Use explicit participant media booleans instead of inferred behavior

**Decision:** Add `allowMicrophone` and `allowVideo` as first-class settings and
remove `muteOnEntry`.

**Rationale:** `muteOnEntry` is a join-time behavior flag, while the new product
direction focuses on whether participants are allowed to enable media at all.
Separate booleans produce a clearer long-lived policy for both API consumers and
Android UI.

**Implications:**

- Backend request validation and JSON persistence must include both new booleans
- Android schedule UI must replace the old mute-on-entry switch with microphone
  and video permission switches
- Tests should verify the new field mapping rather than join-time mute behavior

**Alternatives considered:**

- Keep `muteOnEntry` and derive microphone permissions elsewhere → rejected
  because it mixes different concepts and does not satisfy the requested model

### D5: Update both backend and Android in the same change

**Decision:** Treat OpenAPI regeneration and Android refactor as part of the
same delivery, not follow-up work.

**Rationale:** This is a breaking schema change. Leaving Android on the previous
generated DTOs would immediately break compile-time or runtime request mapping.
The cleanest path is to update `unified-openapi.yaml`, regenerate DTOs, and then
adjust repository/view-model/fragment code against the new generated contract.

**Implications:**

- The work order should be backend model/schema changes first, then Android DTO
  regeneration, then Android mapping/UI updates
- Build verification must include both backend tests and Android assembly

**Alternatives considered:**

- Land backend changes first and update Android later → rejected because the
  user explicitly scoped this as one coordinated change

## Risks / Trade-offs

- **Persisted JSON rows may still contain old properties in non-production
  data** → Mitigation: accept that no migration is needed, and rely on overwrite
  of settings documents when meetings are updated or recreated in this
  environment
- **Breaking schema changes can leave generated Android code temporarily out of
  sync** → Mitigation: update unified OpenAPI and regenerate Android DTOs before
  finishing repository/view-model work
- **Password rename could blur the difference between raw input and hashed
  storage** → Mitigation: keep hashing exclusively in the use-case layer and
  keep responses limited to `requirePassword`
- **Old tests may encode removed business concepts such as timeout, recording,
  or mute-on-entry** → Mitigation: rewrite tests to assert only the new field
  set and preserved business rules
- **Spec drift between backend contract and Android UX wording** → Mitigation:
  update both modified capability specs in the same change before implementation

## Migration Plan

1. Refactor backend `MeetingSettings` plus
   request/response/persistence/repository mappings to the new field shape and
   defaults
2. Update backend tests for PUT meeting settings and controller coverage using
   the new request/response signatures
3. Update `openapi/unified-openapi.yaml` schemas for meeting settings request
   and response
4. Regenerate Android OpenAPI DTOs from the updated unified spec
5. Refactor Android `MeetingSettingsInput`, repository mapping, view model,
   fragment logic, and layout controls to the new booleans and defaults
6. Update OpenSpec capability specs to document the new API and Android behavior
7. Verify with `./gradlew :services:meeting-management:test` and
   `./gradlew :android-app:app:assembleDebug`

Rollback is a code revert only. No database migration or data rollback is
required because the environment is not yet in production.

## Open Questions

- None for artifact creation. The requested target model, defaults, and affected
  files are specific enough to proceed directly into implementation.
