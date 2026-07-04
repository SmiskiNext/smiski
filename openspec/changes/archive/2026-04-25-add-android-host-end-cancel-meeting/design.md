# Context

The Android app already follows MVVM with a clean separation between repository
contracts, use cases, Hilt wiring, and fragment/view-model presentation logic.
Backend support for host-only `cancel` and `end` meeting transitions already
exists, but Android currently exposes only a local leave flow inside
`ActiveCallFragment` and no host action on the meeting detail screen.

This change spans two Android surfaces that share meeting lifecycle authority
but differ in state source and UX constraints:

- The in-call surface (`ActiveCallFragment` + `CallViewModel`) must preserve
  room connection state and only finish `VideoCallActivity` after a successful
  backend end-meeting response.
- The detail surface (`MeetingDetailFragment` + `MeetingDetailViewModel`) must
  reveal cancellation only for the host on `SCHEDULED` meetings and update the
  rendered meeting state immediately after success.

The implementation must stay aligned with existing Java, LiveData,
`CompletableFuture`, Material dialog, and Hilt patterns already used by cancel
flows and dashboard meeting actions.

## Goals / Non-Goals

**Goals:**

- Add a repository and domain-use-case path for Android to invoke the existing
  backend end-meeting endpoint.
- Expose host-aware end-meeting state from `CallViewModel` so the in-call UI can
  show loading, success, and recoverable error feedback.
- Update the active-call leave dialog so hosts can choose between local leave
  and ending the meeting for all, while non-host behavior remains unchanged.
- Add a host-only scheduled-meeting cancel action to the meeting detail screen,
  using the existing cancel use case and updating visible state after success.
- Keep all new behavior localized, lifecycle-safe, and consistent with existing
  Android architecture and resource patterns.

**Non-Goals:**

- Changing backend APIs, authorization rules, or meeting lifecycle semantics.
- Introducing Kotlin, Compose, Flow, coroutines, or new asynchronous primitives.
- Adding new meeting-management entry points outside the active call screen and
  meeting detail screen.
- Reworking the existing local leave/disconnect flow for non-hosts or for the
  host's "Leave Meeting" option.
- Adding optimistic state transitions that claim success before backend
  confirmation.

## Decisions

### Reuse the existing clean-architecture call chain for end meeting

`MeetingRepository` will gain
`CompletableFuture<Void> endMeeting(String meetingId)`, implemented in
`MeetingRepositoryImpl` through generated `MeetingsApi.endMeeting(meetingId)`. A
dedicated `EndMeetingUseCase` will mirror the shape and ownership of existing
meeting use cases, and Hilt will provide it through the same DI module pattern
already used for `CancelMeetingUseCase`.

Rationale:

- Keeps backend orchestration out of fragments and view models.
- Matches the codebase's repository/use-case boundaries and minimizes
  special-case wiring.
- Makes end-meeting behavior testable independently from UI.

Alternatives considered:

- Call `MeetingsApi` directly from `CallViewModel`: rejected because it bypasses
  clean-architecture boundaries and duplicates error-handling conventions.
- Reuse `CancelMeetingUseCase` with a mode flag: rejected because cancel and end
  are distinct backend transitions with different allowed states and different
  screens.

### Model end-meeting as explicit `CallViewModel` action state, separate from local leave

`CallViewModel` will expose dedicated LiveData for end-meeting progress,
success, and error rather than overloading existing room connection or leave
state. `endMeetingForAll()` will validate that both the meeting id and host role
are available, trigger the use case, and only on success invoke the same room
teardown/finish path used for local leave.

Rationale:

- The UI must distinguish between a local disconnect and a host-only backend
  meeting termination.
- Failure must keep the user inside the call, which is easier to guarantee when
  backend action state is separate from room connection state.
- Dedicated state avoids accidental coupling with existing connection and leave
  observers.

Alternatives considered:

- Finish the activity immediately and fire the backend request in parallel:
  rejected because it can hide failures and leave the room active server-side
  from the host perspective.
- Reuse one generic action LiveData for all in-call operations: rejected because
  it makes observers ambiguous and error messages harder to scope.

### Keep host branching in `ActiveCallFragment`, with dialog behavior determined by existing `isHost` state

`ActiveCallFragment.showLeaveDialog()` will keep the current non-host
confirmation flow. When `isHost` is true, it will present a two-option Material
dialog that clearly separates local leave from ending the meeting for all
participants.

Rationale:

- The decision is presentation-specific and already depends on `CallViewModel`
  state consumed by the fragment.
- Preserves current behavior for non-hosts with minimal regression surface.
- Keeps the host-only destructive action behind explicit confirmation and action
  labeling.

Alternatives considered:

- Always show the host dialog and disable the destructive option for non-hosts:
  rejected because it adds unnecessary complexity and confusing affordances.
- Replace the leave button with different controls for hosts: rejected because
  it would diverge from the current control layout and exceed the scope.

### Update meeting detail UI from locally held detail state after cancel success

`MeetingDetailViewModel` will inject `CancelMeetingUseCase`, expose cancel
action LiveData, and on successful cancellation update the current meeting
detail state so the fragment can immediately render a `CANCELLED` status and
hide the cancel button without requiring full screen reload.

Rationale:

- The user needs immediate visual confirmation on the same screen.
- Local state mutation after a successful backend response is simpler and faster
  than forcing a refetch.
- The detail screen already owns the loaded meeting model and current-user host
  check inputs.

Alternatives considered:

- Always refetch meeting detail after cancel: rejected because it adds network
  latency, duplicate loading states, and more failure points after a confirmed
  cancel.
- Let the fragment manually mutate views without changing view-model state:
  rejected because it breaks MVVM consistency and risks stale state on
  recreation.

### Gate cancellation visibility and execution using both host identity and meeting status

The meeting detail cancel button will only render when
`detail.hostId().equals(currentUserId())` and status is `SCHEDULED`. The view
model will also enforce the same conditions before invoking the use case so
UI-only checks are not the sole guard.

Rationale:

- Prevents accidental exposure of unsupported actions in the UI.
- Keeps the frontend aligned with backend authorization and lifecycle
  constraints.
- Redundant checks reduce the chance of incorrect calls during stale UI or race
  conditions.

Alternatives considered:

- Rely on backend authorization only: rejected because it produces avoidable
  failing requests and poor UX.
- Gate only in the fragment: rejected because the view model still needs
  defensive validation for correctness.

## Risks / Trade-offs

- Delayed backend end-meeting responses could leave the host staring at the
  active call longer than a local leave action would. → Surface loading state
  during the end-meeting request and finish only after success.
- Local mutation of the meeting detail model after cancel success could drift
  from server state if fields other than status change server-side. → Limit
  local updates to status-driven UI concerns and preserve the option to refresh
  later if needed.
- Separate LiveData channels for end and cancel actions add more observer
  wiring. → Keep state narrowly scoped and use the same naming and reset
  patterns as existing dashboard cancel behavior.
- Host status or meeting id could be missing transiently in the call screen. →
  Validate preconditions in `CallViewModel.endMeetingForAll()` and emit a
  recoverable error instead of attempting the request.

## Migration Plan

No backend or data migration is required. Implementation can ship as a normal
Android client update because it consumes existing endpoints.

1. Add repository, use case, and DI wiring for end meeting.
2. Extend `CallViewModel` and `ActiveCallFragment` for host end-meeting
   behavior.
3. Extend `MeetingDetailViewModel`, fragment, and layout for host cancellation.
4. Add string resources and verify host/non-host flows on supported meeting
   states.

Rollback strategy: revert the Android client changes. Backend behavior remains
unchanged and compatible with older clients.

## Open Questions

- None. The requested flows, gating rules, and error handling behavior are
  sufficiently defined for implementation.
