# Why

The Android meeting experience currently exposes too many primary call controls,
lacks an in-call layout switcher, and does not give hosts a direct way to adjust
meeting settings during a live session or from upcoming meeting cards. This
redesign is needed now because the backend settings API and the newer Android
dashboard scheduling flows already exist, but the client UI does not yet expose
them in a focused, host-friendly way.

## What Changes

- Redesign `ActiveCallFragment` with a compact four-slot control bar that keeps
  microphone, camera, and end-call actions primary while moving secondary
  actions into an overflow bottom sheet.
- Add a top-bar layout switcher and a layout picker bottom sheet that lets users
  choose Auto, Tiled, Spotlight, or Sidebar video arrangements.
- Add host-only in-meeting settings editing from the active call flow, backed by
  the existing `PUT /api/v1/meetings/{id}/settings` API.
- Extend call presentation state so `CallViewModel` can expose the current video
  layout, host status, and editable meeting settings needed by the redesigned
  UI.
- Add upcoming meeting item overflow actions on the dashboard so hosts can edit
  scheduled meetings and access related meeting options from each card.
- Extend `ScheduleFragment` to support editing an existing scheduled meeting
  with prefilled values and an update-oriented submission flow.
- Add the Android resources required for the redesign, including new dimensions,
  strings, menus, bottom-sheet layouts, and layout-selection icons.

## Capabilities

### New Capabilities

- `android-video-call-layout-controls`: Android in-call layout selection and
  overflow action surfaces for compact video-call controls.

### Modified Capabilities

- `android-videocall-shell`: Update active-call requirements to support a
  compact control bar, overflow actions, host-only in-meeting settings access,
  and layout switching.
- `android-host-upcoming-meetings`: Update upcoming meeting cards to expose a
  host options menu in addition to the join action.
- `android-meeting-creation`: Update the scheduling flow so `ScheduleFragment`
  can edit an existing scheduled meeting and submit an update flow with
  preloaded settings.

## Impact

- Android UI layer: `fragment_active_call.xml`, new bottom-sheet layouts,
  `item_upcoming_meeting.xml`, menu and drawable resources, and string/dimen
  additions.
- Android presentation layer: `ActiveCallFragment`, `CallViewModel`,
  `DashboardFragment`, `UpcomingMeetingAdapter`, `ScheduleFragment`, and new
  bottom-sheet classes.
- Android domain/data layer: `VideoLayout` model, `MeetingRepository` contract,
  and `MeetingRepositoryImpl` support for meeting-settings updates.
- Backend/API dependency: existing `PUT /api/v1/meetings/{id}/settings` endpoint
  is reused; no backend API contract changes are required for this change.
