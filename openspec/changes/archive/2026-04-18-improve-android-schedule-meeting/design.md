# Context

The Android app already supports scheduled meeting submission through
`ScheduleFragment`, `ScheduleViewModel`, and `MeetingRepositoryImpl`, but the
current implementation only covers a minimal subset of the backend contract. The
form still treats `topic` as required, the repository maps most meeting settings
to hardcoded defaults, and the UI exposes only waiting-room and host-video
toggles even though the backend schedule endpoint accepts richer settings.

The backend contract is already defined in the meeting-management service and
generated OpenAPI clients. The server-side `ScheduleMeetingRequest` accepts an
optional `title` with `@Size(max = 255)` and requires a `settings` object
containing `admissionPolicy`, `muteOnEntry`, `maxParticipants`,
`recordingEnabled`, `screenShareMode`, and `chatEnabled`, with optional
`password`. On Android, `MeetingRepositoryImpl` currently always sends default
values for these fields, which creates drift between the user-facing form and
the actual request sent to the backend.

This change spans Android presentation, domain modeling, and repository request
mapping, so a design document is useful to lock in validation behavior, request
ownership, and form structure before implementation.

**Constraints:**

- Must preserve MVVM + Clean Architecture boundaries from
  `frontends/android-app/app/codemap.md`
- Must maintain the existing schedule submission flow and success/failure
  navigation behavior
- Must keep `hostVideo` as a local-only preference because the generated backend
  schema does not expose a host-video field for schedule settings
- Must use Material 3-compatible styling and existing spacing/theme tokens in
  Android resources
- Must follow existing Android accessibility patterns, especially for touch
  targets, content descriptions, and inline form feedback

## Goals / Non-Goals

**Goals:**

- Align Android validation with backend schedule constraints, especially
  optional title and 255-character limit
- Expand the schedule form to collect the backend-supported settings that matter
  for meeting creation
- Replace hardcoded repository defaults for scheduled meetings with actual
  user-selected values
- Keep host-video preference persisted locally while documenting that it is not
  sent to the backend
- Improve schedule-form clarity and accessibility with inline validation, helper
  text, better affordances, and visible loading state

**Non-Goals:**

- Adding invitee management to the schedule flow
- Adding meeting description, recurrence, or calendar integration
- Changing the backend API schema or service-side validation rules
- Changing instant-meeting creation behavior outside any shared request-model
  abstractions that benefit schedule flow

## Decisions

### D1: Represent schedule settings as a dedicated domain value object

**Decision:** Add a `MeetingSettingsInput` domain model and make
`ScheduleMeetingRequest` carry that object instead of flattening every setting
directly into repository method parameters.

**Rationale:** The schedule form now needs to carry a cohesive set of settings:
waiting room/admission policy, host video local preference, mute on entry,
password, max participants, screen share mode, chat enabled, and recording
enabled. Encapsulating them in one object keeps ViewModel-to-domain boundaries
readable, avoids constructor bloat on `ScheduleMeetingRequest`, and makes
repository mapping easier to reason about and test.

**Alternatives considered:**

- Put every new setting directly on `ScheduleMeetingRequest` as individual
  fields → workable, but creates a large, harder-to-maintain constructor and
  duplicates the concept of meeting settings
- Keep only primitive ViewModel arguments and build settings directly in
  `MeetingRepositoryImpl` → leaks form-shaping concerns across layers and makes
  validation ownership less clear

### D2: Keep validation split between immediate field feedback and submit-time safety checks

**Decision:** `ScheduleFragment` will own blur-based inline validation display
via `TextInputLayout.setError()`, while `ScheduleViewModel` remains the source
of truth for submit-time validation before request construction.

**Rationale:** The user asked for inline validation on blur using the existing
Android `TextInputLayout.setError()` pattern. That interaction is inherently
view-driven because blur/focus events belong to the fragment. However,
submit-time validation must still live in the ViewModel so invalid requests
cannot bypass UI feedback or get submitted from stale state.

**Alternatives considered:**

- Move all validation into the fragment → improves immediacy but weakens
  business-rule enforcement and testability
- Move all validation into the ViewModel and emit field states for every blur
  event → possible, but overcomplicates a small Java/XML form and diverges from
  existing fragment-driven error wiring patterns in the app

### D3: Map waiting room to admission policy and keep host video local-only

**Decision:** Preserve the current meaning of the waiting-room toggle by mapping
it to `admissionPolicy`, and continue saving host video into `SessionRepository`
without serializing it in the schedule API request.

**Rationale:** This follows an established behavior already documented in
`android-meeting-creation` and matches the backend schema reality. The
waiting-room control is user-visible and meaningful to the backend, while host
video remains a local pre-join preference because no supported request field
exists.

**Alternatives considered:**

- Rename the waiting-room control to expose raw `admissionPolicy` values → more
  accurate technically, but worse for UX and unnecessary because the toggle can
  still map deterministically
- Attempt to inject host-video into the generated request model indirectly →
  brittle and outside the supported OpenAPI contract

### D4: Use an always-visible primary settings block plus a collapsible advanced section

**Decision:** Keep high-frequency settings visible by default (waiting room,
host video, mute on entry, password), and place lower-frequency controls (max
participants, screen share, chat, recording) inside a collapsible advanced
section.

**Rationale:** The schedule screen already has several required fields at the
top. Adding all new settings inline would produce a long, noisy form and bury
the most important scheduling controls. A progressive-disclosure layout
preserves backward familiarity while still exposing the full backend-supported
surface.

**Alternatives considered:**

- Show all settings in one flat list → simplest to implement but visually heavy
  and harder to scan
- Hide all settings under a separate dialog/screen → cleaner main form but adds
  navigation friction and makes request completeness less obvious

### D5: Compute helper text for end time in the presentation layer

**Decision:** `ScheduleFragment`/`ScheduleViewModel` will derive and show a
helper label under duration that reflects the calculated end time whenever date,
time, and duration are all valid.

**Rationale:** End time is not a separate editable field, but it is central to
helping users confirm their selection. Exposing it as helper text reduces
scheduling mistakes without changing the backend contract, which still requires
only `startTime` and `endTime` in the final request.

**Alternatives considered:**

- Do not show end time until after submit → keeps UI simpler but misses a
  valuable confirmation cue
- Add a dedicated read-only end-time field → clearer, but heavier than needed
  for derived information

### D6: Migrate schedule form inputs to Material 3-compatible styling while preserving layout conventions

**Decision:** Update the schedule form’s `TextInputLayout` usage away from
Material Components 2 widget styles and align the form with the app’s Material 3
theming, spacing, and icon-row patterns.

**Rationale:** The requested UX cleanup is not just cosmetic; it reduces style
inconsistency in one of the app’s primary creation flows. Keeping the same
fragment and XML structure while modernizing control styles limits risk and
avoids a full screen rewrite.

**Alternatives considered:**

- Leave old input styles in place and only add new fields → faster, but
  preserves the very inconsistency the change is meant to address
- Rewrite the screen in Compose → far out of scope for this Java/XML feature
  update

## Risks / Trade-offs

- **Form complexity increases as more settings are exposed** → Mitigation: keep
  a short primary section and collapse lower-priority controls behind an
  advanced section
- **Validation may diverge again if UI and ViewModel rules are duplicated
  carelessly** → Mitigation: define one canonical set of rules in the ViewModel
  and make fragment blur validation call the same checks or mirror the same
  constants/messages
- **Password handling could create confusing empty/hidden states** → Mitigation:
  treat password as optional, gate visibility explicitly, and avoid sending
  blank strings when the field is disabled or empty
- **End-time helper text can become stale when one input changes** → Mitigation:
  recompute helper text whenever date, time, or duration changes and clear it
  when inputs are incomplete or invalid
- **Repository mapping changes may unintentionally affect existing schedule
  defaults** → Mitigation: preserve current defaults as initial UI values, then
  serialize the actual chosen values so backward behavior remains stable unless
  the user changes a setting

## Migration Plan

1. Add `MeetingSettingsInput` and extend `ScheduleMeetingRequest` so domain
   models can represent the richer schedule payload
2. Update `ScheduleViewModel` validation and request construction to use
   optional title, max-length checks, and the new settings object while still
   persisting host video locally
3. Update `MeetingRepositoryImpl.buildScheduleMeetingRequest()` to map the
   richer domain model into `MeetingManagementScheduleMeetingRequest.settings`
4. Restructure `fragment_schedule.xml` and `ScheduleFragment` for the new
   controls, advanced section, accessibility labels, helper text, and loading
   state
5. Add or update string resources and any supporting icons/styles needed by the
   new schedule UI
6. Verify that unchanged user behavior still works with default values, and that
   newly exposed settings affect the backend request as expected

Rollback is straightforward: revert the schedule-form resource and Android
schedule/domain mapping changes without any backend migration because the server
contract remains unchanged.

## Open Questions

- Whether password should have any additional client-side format constraints
  beyond optional/non-empty entry when provided, since the current backend
  schema only indicates a string field
- Whether the advanced settings section should remember expanded/collapsed state
  across configuration changes or simply reset to collapsed on recreation
- Whether helper text should show only the computed end time or also summarize
  selected timezone information for users near day boundaries
