# Context

The Android app already has the structural pieces for hosting meetings, but the
end-to-end creation path is incomplete. `DashboardFragment` still exposes a
legacy three-card quick-action row, `CreateMeetingViewModel` and
`ScheduleViewModel` stop at TODO stubs, `MeetingRepository` and
`MeetingRepositoryImpl` do not define creation contracts, and
`VideoCallActivity` is available but not launched from a successful
instant-meeting API response.

The backend contract already exists through generated OpenAPI clients:
`MeetingsApi.createInstantMeeting()` and `MeetingsApi.scheduleMeeting()` both
return `MeetingManagementMeetingResponse`. The Android app also already has
Hilt, Retrofit, executor qualifiers, `SessionRepository` for current-user and
device preferences, and examples of `CompletableFuture`-based
repository/use-case flows from the meeting history feature.

**Constraints:**

- Must preserve MVVM + Clean Architecture boundaries described in
  `frontends/android-app/app/codemap.md`
- Must use existing Android Navigation for in-app fragment transitions, while
  still allowing task handoff into `VideoCallActivity`
- Must keep Material 3 and accessibility expectations from existing Android
  specs (theme attributes, 48dp touch targets, content descriptions)
- Must support localized error and confirmation messaging through Android string
  resources and the existing error translation approach

## Goals / Non-Goals

**Goals:**

- Replace the dashboard's static "New Meeting" card with a FAB-based host entry
  point that better matches a primary creation action
- Implement real repository/use-case support for instant and scheduled meeting
  creation using `MeetingsApi`
- Move instant-meeting creation initiation to `DashboardViewModel`, exposing
  observable loading/success/error state to `DashboardFragment`
- Upgrade `CreateMeetingViewModel` and `ScheduleViewModel` from preference-only
  or stub behavior to real server-backed actions
- Launch `VideoCallActivity` after instant meeting creation with the created
  meeting short code and user-selected AV preferences

**Non-Goals:**

- Redesigning `ScheduleFragment` form fields beyond what is needed for API
  submission and validation
- Implementing invitee search, recurring meetings, or advanced scheduling
  options
- Implementing full in-call connection to a media backend beyond handing off to
  the existing `VideoCallActivity`
- Removing `CreateMeetingFragment` from the codebase in this change, even if the
  dashboard no longer uses it as the primary entry point

## Decisions

### D1: Dashboard owns the instant-meeting launch flow

**Decision:** The new FAB in `DashboardFragment` will be the primary instant
meeting trigger, and `DashboardViewModel` will own the API call and one-shot
launch event for `VideoCallActivity`.

**Rationale:** The user-selected interaction pattern is a single FAB with popup
menu. Keeping the API call in the dashboard layer avoids a redundant navigation
hop into `CreateMeetingFragment` before creating an instant meeting. It also
keeps the schedule option consistent with the popup menu by allowing each action
to branch directly from the dashboard.

**Alternatives considered:**

- Keep navigating to `CreateMeetingFragment` first, then call the API there →
  adds an unnecessary screen for the chosen FAB pattern
- Trigger API calls directly inside `DashboardFragment` → violates MVVM and
  makes activity launch/error state harder to test

### D2: Reuse the existing meeting layer instead of creating parallel schedule-only repositories

**Decision:** `MeetingRepository` will become the single contract for both
instant and scheduled meeting creation, with `MeetingRepositoryImpl` backed by
`MeetingsApi` and `MeetingMapper`.

**Rationale:** The OpenAPI contract groups both operations under `MeetingsApi`,
and the existing codebase already intends `MeetingRepository` to represent
meeting room operations. Expanding it avoids splitting one backend capability
across `MeetingRepository` and `ScheduleRepository`, which would create
confusing boundaries.

**Alternatives considered:**

- Put `scheduleMeeting` into `ScheduleRepository` and `createInstantMeeting`
  into `MeetingRepository` → inconsistent ownership for one backend resource
  family
- Skip repository changes and let ViewModels call Retrofit interfaces directly →
  breaks Clean Architecture boundaries

### D3: Use dedicated creation result models rather than overloading empty placeholder domain models

**Decision:** Add meaningful domain models or records for meeting creation
results/settings payloads needed by the presentation layer, and map
`MeetingManagementMeetingResponse` into those models.

**Rationale:** `Meeting` and `Schedule` are currently empty placeholders and do
not communicate the fields the UI needs after creation, such as meeting ID,
short code, title, and timing metadata. Explicit result models make use cases
clearer and reduce accidental DTO leakage into presentation.

**Alternatives considered:**

- Return generated DTOs from repositories → couples domain/presentation to
  generated API code
- Reuse empty `Meeting` / `Schedule` classes without new fields → provides no
  useful contract for UI state or navigation

### D4: Follow the meeting-history `CompletableFuture` + executor pattern for repository and ViewModel async work

**Decision:** The new meeting creation repository methods and use cases will use
`CompletableFuture`, with Retrofit `.execute()` work on `@IoExecutor` and UI
state updates on `@MainExecutor`.

**Rationale:** This is the most established async pattern already implemented in
the Android app. Reusing it keeps threading behavior consistent and lets the new
ViewModels mirror existing lifecycle cleanup and error propagation patterns.

**Alternatives considered:**

- Introduce coroutines/RxJava → inconsistent with the current Java codebase
- Use Retrofit callbacks directly in ViewModels → harder to compose and cancel

### D5: Translate backend meeting-creation failures in the repository layer, then expose plain UI-safe messages

**Decision:** `MeetingRepositoryImpl` will catch API/network exceptions,
including JSend fail/error exceptions where present, and map them to localized
messages using the existing `AndroidErrorTranslator` pattern before bubbling
errors through `CompletableFuture`.

**Rationale:** Centralizing translation near the API boundary keeps ViewModels
focused on state transitions instead of parsing transport-layer exceptions. This
also aligns meeting creation behavior with existing auth/error translation
infrastructure.

**Alternatives considered:**

- Let fragments convert raw exceptions to Snackbar text → duplicates parsing
  logic and spreads backend concerns into UI
- Show only generic error messages → simpler, but loses useful localization and
  field-specific server feedback

### D5a: Treat host-video preference as a documented backend schema limitation

**Decision:** The Android flow will continue reading and preserving the saved
host-video preference locally, but it will not require the generated
`MeetingManagementCreateInstantMeetingRequest` or
`MeetingManagementScheduleMeetingRequest` payload to carry that field until the
backend OpenAPI schema exposes it.

**Rationale:** Verification closed two critical issues and confirmed the build
passes, but also surfaced that host-video is currently a backend API limitation
instead of an Android implementation gap. The generated client models do not
contain the field, so the spec must reflect that the preference is documented
and preserved locally rather than transmitted today.

**Alternatives considered:**

- Inject unsupported fields through manual JSON patching → brittle and outside
  the generated API contract
- Pretend the field is sent already → creates spec drift versus the actual
  implementation and backend schema

### D6: FAB integration will restructure the dashboard root to `CoordinatorLayout` + `NestedScrollView`

**Decision:** `fragment_dashboard.xml` will move from a `ScrollView` root to a
`CoordinatorLayout` containing a `NestedScrollView` and an anchored
`FloatingActionButton`.

**Rationale:** This layout pattern is the standard Material foundation for a FAB
over scrollable content. It avoids overlay clipping issues and leaves room for
future Snackbar behavior and scroll-aware FAB interactions.

**Alternatives considered:**

- Overlay a FAB on the existing `ScrollView` using a `FrameLayout` → workable
  but less aligned with Material patterns and future behavior hooks
- Keep the old card row and add a FAB as a duplicate action → conflicting entry
  points and unnecessary UI clutter

## Risks / Trade-offs

- **Repository contract expansion may ripple into multiple layers** →
  Mitigation: keep method signatures narrowly scoped to instant/scheduled
  creation and add focused domain models for response mapping
- **Dashboard now mixes in-app navigation and external activity launch** →
  Mitigation: keep fragment navigation on `NavController` and expose
  `VideoCallActivity` launch as a one-shot ViewModel event
- **Schedule form parsing may fail on locale/date-time edge cases** →
  Mitigation: define one parsing strategy for the existing date/time/duration UI
  and validate before issuing the API call
- **Server-side validation/error codes may not yet be mapped in Android
  strings** → Mitigation: add meeting-specific string mappings where known and
  fall back to safe generic messaging for unmapped codes
- **`CreateMeetingFragment` may become partially redundant after FAB rollout** →
  Mitigation: retain it as a secondary/manual flow for now and avoid deleting it
  until the product team confirms it is obsolete

## Migration Plan

1. Add domain models, repository contracts, and use cases for instant and
   scheduled creation
2. Implement `MeetingRepositoryImpl` with `MeetingsApi`, `MeetingMapper`,
   executors, and translated error propagation
3. Restructure dashboard layout/resources, add FAB popup menu, and wire the new
   UI to `DashboardViewModel`
4. Update `ScheduleViewModel` / `ScheduleFragment` to call the real scheduling
   flow and return to dashboard on success
5. Update `CreateMeetingViewModel` so the legacy fragment can also create real
   instant meetings if still reached from elsewhere
6. Validate navigation and `VideoCallActivity` launch handoff for meeting short
   code and guest/authenticated flags

Rollback is low-risk: revert dashboard layout/menu changes and restore the prior
stubbed ViewModel behavior while leaving backend APIs untouched.

## Open Questions

- Whether instant-meeting default settings should also persist the camera
  preference back into `SessionRepository` when launched from the dashboard FAB,
  not only from `CreateMeetingFragment`
- Whether `VideoCallActivity` should receive additional extras beyond
  `meetingCode`, such as server meeting ID or host AV defaults, in this change
- Whether localized error-code mappings for meeting-management failures already
  exist in backend enums or need to be added incrementally as generic fallback
  messages first
