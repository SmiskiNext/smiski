# Why

Android hosts can already cancel scheduled meetings and end live meetings
through the backend, but the Android app does not expose those host-only actions
in the places where hosts need them. Adding these controls now closes a
functional gap between backend support and Android meeting management, and
prevents hosts from being forced into incorrect local-only leave behavior.

## What Changes

- Add a host-only in-call leave dialog path that offers both local leave and
  "End Meeting for All" for live meetings.
- Wire the Android meeting domain and data layers to call the existing backend
  end-meeting API.
- Extend `CallViewModel` with end-meeting action state so the active-call UI can
  show loading, success, and recoverable error feedback.
- Add a host-only "Cancel Meeting" action to the meeting detail screen when the
  meeting is still `SCHEDULED`.
- Extend `MeetingDetailViewModel` and the detail UI to confirm cancellation,
  execute the existing cancel-meeting use case, and refresh the visible meeting
  status after success.
- Add required Android string and dependency-injection wiring for the new host
  meeting actions.

## Capabilities

### New Capabilities

- `android-host-meeting-termination`: Host-only Android actions for ending a
  live meeting for all participants and cancelling a scheduled meeting from
  meeting detail.

### Modified Capabilities

- `android-videocall-shell`: Active-call host leave behavior changes from a
  single local leave confirmation to a host-aware leave-or-end-meeting flow.
- `meeting-detail-view`: Meeting detail behavior changes to expose host-only
  cancellation controls and post-cancel status updates for scheduled meetings.

## Impact

- Android app meeting data layer: `MeetingRepository`, `MeetingRepositoryImpl`,
  generated `MeetingsApi` integration.
- Android domain layer: new `EndMeetingUseCase`, existing `CancelMeetingUseCase`
  consumption, Hilt provisioning.
- Android presentation layer: `CallViewModel`, `ActiveCallFragment`,
  `MeetingDetailViewModel`, `MeetingDetailFragment`,
  `fragment_meeting_detail.xml`, and `strings.xml`.
- Backend APIs are unchanged; the app will consume already available
  host-authorized cancel and end endpoints.
