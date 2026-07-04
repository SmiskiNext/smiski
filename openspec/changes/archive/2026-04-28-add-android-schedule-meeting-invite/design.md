# Context

The Android app already supports scheduled meeting creation and scheduled
meeting edit mode through a shared `ScheduleFragment`, but the create flow
cannot currently collect invitees even though the backend schedule API and
generated Android DTOs already support an `invitees` payload. This change is
limited to the Android client and must fit the existing MVVM + Clean
Architecture split, preserve current submission and error-handling patterns, and
avoid introducing new screens, dependencies, or backend contracts.

The main implementation constraint is that invitees are valid only during create
mode. The same schedule screen also supports edit mode for existing meetings,
and the backend does not support updating invitees after creation through that
flow. The design therefore needs to add invitee state and validation without
leaking Android framework concerns into the domain layer and without creating
ambiguity between create and edit submission behavior.

## Goals / Non-Goals

**Goals:**

- Add an invitee entry section to the Android schedule meeting form in create
  mode.
- Let hosts add and remove up to 10 invitee email addresses with inline
  validation for invalid format, duplicates, and max-count violations.
- Propagate invitee data from the fragment through the `ScheduleViewModel` and
  `ScheduleMeetingRequest` into the existing repository request mapper.
- Preserve current backend-driven error handling for unresolved invitee emails
  while keeping client-side validation limited to format, duplicates, and count.
- Keep edit mode behavior explicit by hiding or disabling invitee controls when
  editing an existing scheduled meeting.

**Non-Goals:**

- Adding backend changes, DTO generation changes, or API contract changes.
- Supporting attendee search, autocomplete, or contact picking.
- Allowing invitee edits after a meeting has already been created.
- Raising the backend invitee limit above the Android-specific cap of 10.

## Decisions

### 1. Represent invitees in the ViewModel as `LiveData<List<String>>`

The `ScheduleViewModel` will own invitee state as a list of normalized email
strings exposed through `LiveData`, with fragment interactions routed through
`addInvitee` and `removeInvitee` methods. Validation outcomes for add attempts
will be emitted as a one-shot event so the fragment can show inline
`TextInputLayout` errors without coupling the validation logic to the view.

This keeps email format checks, duplicate detection, and max-count enforcement
in the presentation layer where Android utilities such as
`Patterns.EMAIL_ADDRESS` are already available, while preserving a
framework-free domain request model.

Alternative considered:

- Put validation in the domain model. Rejected because the domain layer is pure
  Java and should not depend on Android `Patterns`, and the validation is
  UI-entry-specific rather than a reusable business rule across platforms.

### 2. Extend the existing schedule request model with a nullable invitee list

`ScheduleMeetingRequest` will gain a nullable `List<String>` invitees field and
accessor. The fragment will pass the current invitee list into the existing
create submission path, and the repository will translate each string into
`MeetingManagementInviteeRequest` before attaching it to
`MeetingManagementScheduleMeetingRequest`.

This preserves the existing layer boundaries: the domain request stays simple
and API-agnostic, while the repository remains responsible for mapping domain
data into generated transport models.

Alternatives considered:

- Pass generated DTOs from the fragment or ViewModel. Rejected because it breaks
  Clean Architecture boundaries and couples presentation/domain code to
  generated API classes.
- Store invitees as a comma-separated string. Rejected because it complicates
  validation, removal, and DTO mapping.

### 3. Rebuild invitee chips from observed state instead of mutating chip views directly

`ScheduleFragment` will observe the invitee list and rebuild the `ChipGroup` and
counter text from the latest state. Add/remove interactions will always go
through the ViewModel, and the fragment will derive enabled/disabled status for
the email field and add button from the current count.

This follows the existing observable-state pattern used by the schedule screen
and avoids view drift when configuration changes or one-shot validation events
occur.

Alternative considered:

- Add and remove chips imperatively without observation. Rejected because the
  chip UI could diverge from the source of truth after recreation or if the list
  changes from another event path.

### 4. Treat invitee UI as create-only and hide it in edit mode

The invitee section will be rendered only for create mode. In edit mode, the
layout section will be hidden or disabled before the user interacts with the
form, and invitees will not be included in update submissions.

This prevents the Android UI from advertising unsupported post-creation invitee
changes and keeps the shared screen aligned with the backend’s current update
contract.

Alternative considered:

- Show existing invitees in read-only form during edit mode. Rejected because
  the provided scope does not include loading invitee data for existing
  meetings, and partial read-only support would add ambiguity without a
  corresponding update path.

## Risks / Trade-offs

- [Invitee validation uses Android email heuristics] → Mitigation: keep client
  validation limited to `Patterns.EMAIL_ADDRESS` and let the backend remain the
  source of truth for user existence and deeper acceptance rules.
- [Shared create/edit screen can accidentally submit invitees in edit mode] →
  Mitigation: gate invitee visibility and request population by mode, and cover
  the mode split in the spec and task verification points.
- [Chip rebuilding can be noisy if state updates are frequent] → Mitigation:
  invitee lists are capped at 10 items, so full chip regeneration remains simple
  and low-cost.
- [Case-insensitive duplicate handling may preserve user-entered casing
  inconsistently] → Mitigation: compare using a normalized form for uniqueness
  while retaining a single canonical stored value per entry during that session.

## Migration Plan

No backend or persisted-data migration is required. Deploy the Android client
changes normally. Rollback is low risk because the invitee payload is additive
and nullable; reverting the Android client simply stops sending invitees while
leaving backend compatibility intact.

## Open Questions

- No blocking open questions. This change assumes existing schedule error
  translation already surfaces backend `INVITEE_NOT_FOUND` responses through the
  current Snackbar observer path.
