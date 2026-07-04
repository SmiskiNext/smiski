# Context

The Android video-call flow already uses an activity-scoped `CallViewModel`, a
`PreJoinFragment`, repository abstractions, and generated OpenAPI DTOs for
meeting lookup and join requests. The backend already enforces password
requirements in the join use case and exposes both the password-aware join
request payload and a short-code lookup endpoint that returns
`settings.requirePassword`.

What is missing is a client-side orchestration layer that resolves meeting info
before the join request, remembers whether the current short code requires a
password, and updates the pre-join UI without breaking the existing approval and
LiveKit connection flow. The change spans domain models, repository contracts,
mapping, ViewModel state, XML layout, fragment behavior, localized resources,
and Android tests, but stays within the existing Java/XML/Hilt/MVVM patterns
described in `frontends/android-app/app/codemap.md`.

## Goals / Non-Goals

**Goals:**

- Add Android support for resolving a meeting by short code before submitting a
  join request
- Surface `requirePassword` in Android domain state so the pre-join flow can
  decide whether password entry is required
- Extend the join request contract to include an optional password without
  changing approved, pending, denied, and expired backend handling
- Update `PreJoinFragment` to reveal password entry progressively, preserve
  inline validation, and route network vs. field-level errors to the correct UI
- Add resource and test coverage for the protected join flow in English and
  Vietnamese

**Non-Goals:**

- Change backend password rules, host bypass logic, or request-join API
  semantics
- Persist meeting passwords locally or auto-fill them across sessions
- Redesign the broader pre-join screen beyond the password section, button
  loading state, and related feedback
- Replace the existing Java + `LiveData` + `CompletableFuture` implementation
  style with coroutines, Compose, or new state-management patterns

## Decisions

### D1: Add a preflight meeting lookup step before requesting room join

**Decision:** `CallViewModel` will introduce
`fetchMeetingInfoAndJoin(shortCode)` to call
`MeetingRepository.getMeetingByShortCode(...)`, cache the resolved meeting UUID,
and publish whether the meeting requires a password before the fragment attempts
`requestJoinRoom()`.

**Rationale:** Password gating depends on meeting metadata that is only
available after lookup. Doing the lookup in the ViewModel preserves the
thin-fragment pattern, keeps business decisions out of the UI layer, and allows
the existing join repository to continue focusing on join submission rather than
branching UI logic.

**Alternatives considered:**

- Let `PreJoinFragment` call `MeetingRepository` directly → simpler initially,
  but breaks MVVM boundaries and scatters error handling
- Submit join requests blindly and rely on backend denial for protected meetings
  → technically functional, but fails the requested UX and prevents progressive
  password disclosure

### D2: Model password gating as explicit ViewModel UI state rather than deriving it solely from `MeetingSettings`

**Decision:** `CallViewModel` will expose dedicated state for
`requiresPassword`, `password`, `isFetchingMeetingInfo`, and `fetchError`, while
still mapping `requirePassword` into `MeetingSettings` for domain consistency.

**Rationale:** The password requirement belongs to the meeting domain, but the
pre-join screen also needs transient UI state for whether a lookup is in
progress, whether the password should currently be shown, and whether the user
changed the meeting code after the last lookup. Separate state keeps the domain
model clean and makes fragment logic deterministic.

**Alternatives considered:**

- Derive all behavior from `MeetingSettings` alone → insufficient for loading,
  stale-code detection, and field-reset behavior
- Store these flags only in the fragment → vulnerable to configuration changes
  and inconsistent with the activity-scoped call flow

### D3: Keep join submission as a single repository call with nullable password

**Decision:** `JoinRoomRepository.requestJoin(...)` will add an
`@Nullable String password` parameter, and `JoinRoomRepositoryImpl` will always
set `.password(password)` on `MeetingManagementJoinRequestRequest`.

**Rationale:** The backend contract already supports nullable passwords, so the
smallest and clearest extension is to thread that nullable field through the
existing repository abstraction. This avoids creating a second join method or a
parallel request type while preserving current pending-approval and token
handling.

**Alternatives considered:**

- Create separate protected and unprotected join methods → duplicates flow and
  increases testing surface
- Put password into ViewModel-only branching and omit it from repository API →
  hides a real backend contract from the domain layer

### D4: Distinguish lookup errors from join errors in the presentation contract

**Decision:** Meeting-code lookup failures will be surfaced through dedicated
`fetchError` state, while existing backend join failures continue to use
`joinError` and `JoinState`.

**Rationale:** The requested UX treats lookup failures differently: not found is
an inline meeting-code error, network fetch failures show a retry snackbar, and
invalid password should stay attached to the password field after join
submission. Separate channels let the fragment render the correct feedback
without parsing ambiguous error strings from one generic error source.

**Alternatives considered:**

- Reuse `joinError` for both lookup and join submission → conflates two phases
  with different UI requirements
- Model a larger combined state machine enum → possible, but heavier than needed
  for the incremental change

### D5: Reset password-specific state whenever the effective short code changes

**Decision:** Changing the meeting code after a password prompt was shown will
clear the cached meeting UUID, reset the password text/state, hide password
requirements until a fresh lookup succeeds, and force the next tap on Join to
run `fetchMeetingInfoAndJoin(...)` again.

**Rationale:** The password requirement is tied to a specific meeting lookup.
Keeping old password UI visible after the code changes risks submitting a stale
password against the wrong meeting and creates confusing validation behavior.

**Alternatives considered:**

- Keep the password field visible until the next lookup completes → exposes
  stale state and violates the requested edge-case handling
- Re-fetch on every keystroke → too chatty for the network layer and unnecessary
  for this workflow

### D6: Handle the password reveal in the fragment, but drive it from observable state

**Decision:** `PreJoinFragment` will observe `requiresPassword` and trigger a
one-time reveal animation using the existing view system, with delayed loading
spinner behavior and password auto-focus handled in the fragment.

**Rationale:** Animation timing, focus control, snackbar presentation, and
inline field errors are view responsibilities. Keeping them in the fragment
respects Android UI boundaries while still making the ViewModel the source of
truth for when the password section should appear.

**Alternatives considered:**

- Imperatively show the field directly after the repository callback without
  observable state → harder to recover after recreation and couples async logic
  to the fragment lifecycle
- Move animation timing logic into the ViewModel → mixes view concerns into
  presentation state management

## Risks / Trade-offs

- **Lookup and join now happen in two phases, increasing UI state complexity** →
  Mitigation: separate `fetchError`, `joinError`, and `requiresPassword` state
  and reset them in one place when the code changes or join state resets
- **Backend error messages for invalid password may not always be structured for
  field-level rendering** → Mitigation: define expected invalid-password
  handling in spec/tasks and keep a fallback path that still surfaces a
  user-visible error
- **Spinner delay and password animation can race with fast responses or
  fragment recreation** → Mitigation: keep loading state observable, make the
  fragment render idempotently, and only show delayed loading UI while fetch is
  still in progress
- **Short-code lookup adds an extra network dependency before join** →
  Mitigation: reuse the lookup response to cache `meetingUuid`, reducing later
  resolution work and allowing retry without recomputing view state contracts

## Migration Plan

1. Extend Android domain/data models and repository contracts to support
   `requirePassword`, short-code lookup, and nullable join passwords
2. Implement `MeetingRepositoryImpl.getMeetingByShortCode(...)` and thread the
   password field through `JoinRoomRepositoryImpl`
3. Update `CallViewModel` with protected-join lookup state, password state, and
   reset logic
4. Update `fragment_prejoin.xml`, `PreJoinFragment`, and string resources for
   the password section, delayed loading state, and localized errors
5. Add unit tests for mapping and ViewModel state transitions, then validate the
   protected join path manually or through available integration coverage

Rollback is limited to the Android client: remove the lookup/password UI changes
and restore the previous direct join flow. Backend APIs remain backward
compatible because the password field is nullable.

## Open Questions

- Which exact backend error code or message the Android client receives for an
  invalid password, and whether it is stable enough to map directly to the
  password field without additional translator work
- Whether the app's existing integration-test setup can cover the protected join
  path end to end, or whether this iteration should stop at ViewModel + mapper
  unit coverage plus manual verification
