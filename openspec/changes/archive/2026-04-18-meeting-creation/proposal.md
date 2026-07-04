# Why

The Android app still treats meeting creation as placeholder UI: the dashboard
uses static quick-action cards, instant meeting creation does not call the real
backend, and scheduled meeting submission never persists to the server. This
change is needed now to turn the primary entry point for hosting meetings into a
production-ready flow that launches real meetings and confirms scheduled ones.

## What Changes

- Replace the dashboard "New Meeting" quick action card with a Material FAB that
  opens a popup menu for "Start Instant Meeting" and "Schedule Meeting"
- Implement full Android integration for `MeetingsApi.createInstantMeeting()`
  including request construction, success/error handling, and launch of
  `VideoCallActivity`
- Implement full Android integration for `MeetingsApi.scheduleMeeting()`
  including request construction from form data, success confirmation, and
  return to Dashboard
- Add or complete meeting-domain repository, mapper, and use-case support for
  instant and scheduled meeting creation flows
- Update dashboard, create-meeting, and schedule presentation layers to expose
  observable UI state instead of stub-only actions
- Document the current backend limitation that the host-video preference is not
  exposed in the generated OpenAPI schema, so Android preserves the preference
  locally but cannot send that field in the create/schedule request payload yet

## Capabilities

### New Capabilities

- `android-meeting-creation`: Provide the Android host flow for creating instant
  and scheduled meetings through the dashboard FAB, backend API calls, and
  success/error state handling

### Modified Capabilities

- `android-navigation`: Update dashboard entry behavior so meeting creation is
  launched from a FAB menu and scheduled meeting navigation continues through
  the existing full-screen fragment flow
- `android-videocall-shell`: Clarify that successful instant meeting creation
  from the dashboard launches `VideoCallActivity` with the created meeting
  context

## Impact

**Code Changes:**

- `frontends/android-app/app/src/main/res/layout/fragment_dashboard.xml` -
  convert root layout for FAB support and remove the old "New Meeting" card
- `frontends/android-app/app/src/main/res/menu/menu_fab_new_meeting.xml` - add
  popup menu actions for instant/scheduled meeting creation
- `frontends/android-app/app/src/main/res/values/strings.xml` - add labels,
  confirmations, and error messages for meeting creation flows
- `frontends/android-app/app/src/main/java/.../domain/repository/MeetingRepository.java`
    - add meeting creation contracts
- `frontends/android-app/app/src/main/java/.../data/repository/MeetingRepositoryImpl.java`
    - implement `MeetingsApi` calls and error translation
- `frontends/android-app/app/src/main/java/.../data/mapper/MeetingMapper.java` -
  map meeting creation responses into domain models used by presentation
- `frontends/android-app/app/src/main/java/.../domain/usecase/meeting/` - add or
  complete use cases for instant and scheduled creation
- `frontends/android-app/app/src/main/java/.../presentation/main/dashboard/` -
  handle FAB interactions and instant-meeting UI state
- `frontends/android-app/app/src/main/java/.../presentation/main/meeting/` and
  `.../presentation/schedule/` - replace TODO flows with real service-backed
  execution
- `frontends/android-app/app/src/main/java/.../di/` - bind repository/use-case
  dependencies as needed

**APIs Used:**

- `POST /api/v1/meetings:instant`
- `POST /api/v1/meetings:schedule`

**Systems Affected:**

- Android dashboard quick actions and navigation
- Android meeting creation and scheduling flows
- Android video-call launch entry points
