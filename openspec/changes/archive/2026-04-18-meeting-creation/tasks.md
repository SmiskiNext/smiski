# Tasks

## 1. Domain and data contracts

- [x] 1.1 Expand `MeetingRepository` with instant-meeting and schedule-meeting
      creation methods returning `CompletableFuture` domain results
- [x] 1.2 Add or complete domain models needed for meeting creation results and
      request-building inputs
- [x] 1.3 Implement `CreateMeetingUseCase` for instant meeting creation
- [x] 1.4 Add a dedicated use case for scheduled meeting creation ← (verify: use
      cases expose only domain contracts, and both creation flows are available
      to presentation without DTO leakage)

## 2. Repository and mapper implementation

- [x] 2.1 Implement `MeetingRepositoryImpl` with `MeetingsApi`, `MeetingMapper`,
      and `@IoExecutor` background execution
- [x] 2.2 Build `MeetingManagementCreateInstantMeetingRequest` with default
      settings including waiting room enabled and saved host-video preference
- [x] 2.3 Build `MeetingManagementScheduleMeetingRequest` from schedule form
      data including timing and settings fields
- [x] 2.4 Extend `MeetingMapper` to map `MeetingManagementMeetingResponse` into
      the new domain meeting creation result models
- [x] 2.5 Integrate localized error translation/fallback messaging for network,
      fail, and server exceptions ← (verify: repository returns mapped success
      data and user-safe localized failures for both API endpoints)

## 3. Dashboard FAB and instant meeting flow

- [x] 3.1 Restructure `fragment_dashboard.xml` to use `CoordinatorLayout` and
      `NestedScrollView`, and remove the old "New Meeting" quick-action card
- [x] 3.2 Add `menu_fab_new_meeting.xml` and any supporting icon/string
      resources for the FAB popup actions
- [x] 3.3 Implement `DashboardViewModel` state and one-shot events for instant
      meeting loading, success, and failure
- [x] 3.4 Update `DashboardFragment` to show the FAB menu, route Schedule to
      `action_dashboard_to_schedule`, and observe ViewModel state with
      `getViewLifecycleOwner()`
- [x] 3.5 Launch `VideoCallActivity` from dashboard success state with
      `FLAG_ACTIVITY_NEW_TASK` and the created meeting short code ← (verify:
      dashboard no longer depends on the old New Meeting card, FAB menu works,
      and successful instant meeting creation launches the call flow correctly)

## 4. Legacy create-meeting screen completion

- [x] 4.1 Update `CreateMeetingViewModel` to call the real instant-meeting use
      case after persisting mic/camera preferences
- [x] 4.2 Add observable UI state/events in `CreateMeetingViewModel` for
      loading, success, copy-link readiness, and errors
- [x] 4.3 Update `CreateMeetingFragment` to observe ViewModel state, avoid
      immediate stub navigation, and reuse the real instant-meeting result ←
      (verify: the legacy fragment can still create a real instant meeting and
      no longer uses TODO-only behavior)

## 5. Schedule submission flow

- [x] 5.1 Implement `ScheduleViewModel` using the scheduled meeting use case,
      `SessionRepository`, and main-thread state updates
- [x] 5.2 Add schedule form validation and request parsing for date, time, and
      duration before submission
- [x] 5.3 Update `ScheduleFragment` to observe submission state, disable
      duplicate submits while loading, and only pop back on success
- [x] 5.4 Show localized Snackbar feedback for schedule failures and
      confirmation for success ← (verify: schedule submissions call the real
      API, remain on-screen on failure, and return to dashboard only after
      success)

## 6. Resources, DI, and navigation consistency

- [x] 6.1 Update `strings.xml` and `values-vi/strings.xml` with FAB labels,
      meeting creation feedback, and fallback error text
- [x] 6.2 Update Hilt bindings or providers required for the new
      repository/use-case graph
- [x] 6.3 Verify `nav_graph_main.xml` and `VideoCallActivity` extras/intent
      usage remain aligned with the updated navigation and video-call specs ←
      (verify: resources are localized, DI resolves cleanly, and
      navigation/call-launch contracts match the OpenSpec requirements)

## 7. Validation and regression checks

- [x] 7.1 Run Android compilation checks for the updated app module and fix any
      meeting-creation build errors
- [ ] 7.2 Manually test dashboard FAB flows: open menu, instant meeting success,
      instant meeting failure, and schedule navigation
- [ ] 7.3 Manually test schedule submission success and failure states
- [ ] 7.4 Verify accessibility and visual behavior for the FAB, popup menu,
      Snackbar messaging, and dashboard layout in light/dark mode ← (verify:
      end-to-end meeting creation flows, UI states, and accessibility behaviors
      match proposal, design, and spec scenarios)
