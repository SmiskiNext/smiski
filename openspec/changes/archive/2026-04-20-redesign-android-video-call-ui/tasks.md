# Tasks

## 1. Extend shared Android meeting models and repository contracts

- [x] 1.1 Add `VideoLayout` and any meeting-settings result/model types needed
      by the redesigned video-call and edit-mode flows
- [x] 1.2 Extend `MeetingRepository` with methods for loading meeting
      detail/settings, replacing meeting settings, and invoking upcoming-meeting
      option actions needed by the dashboard
- [x] 1.3 Implement the new meeting repository methods in
      `MeetingRepositoryImpl` using the generated meetings API, response
      mapping, and localized error translation
- [x] 1.4 Add or update use cases/view-model dependencies so call, dashboard,
      and schedule flows can consume the new repository capabilities ← (verify:
      repository contracts, API calls, and domain mappings cover live settings,
      scheduled settings, meeting detail loading, and upcoming-meeting actions
      end-to-end)

## 2. Build compact active-call controls and overflow actions UI

- [x] 2.1 Add the new compact call-control, bottom-sheet, and layout-card
      dimensions to `res/values/dimens.xml`
- [x] 2.2 Create `bottom_sheet_meeting_actions.xml` with rows for screen share,
      chat badge, participants count, change layout, and host-only settings
- [x] 2.3 Update `fragment_active_call.xml` to replace the six-button bar with
      microphone, camera, end-call, and more-actions controls plus a top-bar
      layout entry point
- [x] 2.4 Create `MeetingActionsBottomSheet.java` and wire `ActiveCallFragment`
      callbacks for overflow actions, host-only row visibility, and existing
      chat/participants entry points ← (verify: compact control bar matches the
      redesigned structure, secondary actions open from the sheet, and
      host/non-host visibility behaves correctly)

## 3. Implement layout picker state and participant arrangement behavior

- [x] 3.1 Create the layout-picker resources (`bottom_sheet_layout_picker.xml`
      and `item_layout_option.xml`) and the `LayoutPickerBottomSheet.java`
      presentation component
- [x] 3.2 Extend `CallViewModel` with observable `currentLayout` state,
      selection methods, and any saved UI state needed by the picker
- [x] 3.3 Update `ActiveCallFragment` to observe `currentLayout`, open the
      picker from both entry points, and apply Auto, Tiled, Spotlight, and
      Sidebar participant arrangements
- [x] 3.4 Preserve layout selection and selected-state rendering across
      bottom-sheet reopen, participant changes, and fragment view recreation ←
      (verify: all four layout options can be selected, the active choice is
      visible, and the participant surface updates without breaking call
      controls or self-view)

## 4. Add host-only in-meeting settings management

- [x] 4.1 Create `bottom_sheet_meeting_settings.xml` with the simplified
      meeting-settings controls required by the live host flow
- [x] 4.2 Implement `MeetingSettingsBottomSheet.java` to load current values,
      submit validated changes, and report loading/success/error back to the
      active call screen
- [x] 4.3 Extend `CallViewModel` with `meetingSettings`, `isHost`,
      `loadMeetingSettings()`, and `updateMeetingSettings()` so live settings
      can be refreshed from repository responses
- [x] 4.4 Connect the meeting settings sheet to `ActiveCallFragment` and ensure
      successful updates refresh local state while failures preserve the live
      session ← (verify: only hosts can open settings, successful PUT updates
      repopulate state from the response, and failed updates do not disconnect
      the call)

## 5. Add upcoming meeting card options on the dashboard

- [x] 5.1 Update `item_upcoming_meeting.xml` to add the more-options button
      without regressing the existing join action layout
- [x] 5.2 Extend `UpcomingMeetingAdapter` with a more-options callback and bind
      the new affordance for each meeting item
- [x] 5.3 Create `menu_upcoming_meeting.xml` and update `DashboardFragment` to
      handle Edit Meeting, Copy Link, Add to Calendar, and Cancel Meeting
      actions
- [x] 5.4 Refresh dashboard state after option actions and wire clipboard,
      calendar-intent, and cancellation feedback flows ← (verify: each upcoming
      meeting card exposes the menu, edit navigation passes the correct meeting
      identifier, copy/calendar actions succeed, and cancelled meetings
      disappear after refresh)

## 6. Add pre-meeting settings edit mode to the schedule flow

- [x] 6.1 Add navigation arguments and fragment/view-model state needed for
      `ScheduleFragment` to distinguish create mode from edit mode for an
      existing meeting
- [x] 6.2 Load existing meeting detail and settings into `ScheduleFragment` edit
      mode, prepopulate the form, and change the primary CTA label from Schedule
      to Update
- [x] 6.3 Reuse the existing settings API submission path for edit mode so valid
      pre-meeting settings updates return the user to the dashboard with
      refreshed upcoming meetings
- [x] 6.4 Preserve validation, loading, and error handling across both create
      and edit flows without implying unsupported metadata updates ← (verify:
      edit mode loads current meeting values, update submissions use the
      scheduled meeting ID, success returns to the dashboard, and unsupported
      fields remain safely handled)

## 7. Finish Android resources, accessibility, and regression coverage

- [x] 7.1 Add all new string resources for layout selection, meeting actions,
      meeting settings, dashboard menu actions, and edit-mode labels
- [x] 7.2 Add the required drawable resources for more-actions, settings, grid
      view, and the four layout-option icons
- [x] 7.3 Update accessibility content descriptions, badge/count labels, and any
      theme-aware styling needed by the new controls, sheets, and menu
      affordances
- [x] 7.4 Run targeted validation for active-call UI, host-only settings,
      upcoming-meeting menu actions, and schedule edit mode on emulator/device
      builds ← (verify: resources resolve cleanly, strings/icons are complete,
      accessibility labels are present, and the redesigned Android flows work
      without regressions)
