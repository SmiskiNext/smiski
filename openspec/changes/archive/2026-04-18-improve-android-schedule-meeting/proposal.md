# Why

The Android schedule-meeting screen has drifted from the backend contract: it
still requires a topic that the backend treats as optional, submits hardcoded
meeting settings instead of the user's actual selections, and lacks several
settings that the backend already supports. This change is needed now to prevent
inconsistent validation, fix request/settings mismatches, and make scheduled
meeting creation feel complete and trustworthy for Android users.

## What Changes

- Align Android schedule form validation with backend rules by making title
  optional, enforcing a 255-character maximum, preserving the existing 15-480
  minute duration range, and showing inline validation errors on blur
- Expand the schedule form to capture backend-supported meeting settings,
  including mute on entry, optional password, max participants, screen share
  mode, chat enabled, and recording enabled
- Keep waiting-room behavior mapped to `admissionPolicy` and continue storing
  host-video locally without sending it to the backend because the API schema
  does not support that field
- Update Android domain and repository request-building so scheduled meeting
  submissions use actual user-selected settings instead of repository defaults
- Improve schedule screen UX with Material 3 text input styling, accessible
  picker triggers, end-time helper text, row icons, past-date prevention, and a
  loading indicator on the submit button

## Capabilities

### New Capabilities

- None

### Modified Capabilities

- `android-meeting-creation`: Update scheduled meeting requirements so the
  Android schedule flow matches backend validation rules, submits all supported
  meeting settings from user input, preserves the documented host-video
  local-only limitation, and improves schedule form usability and accessibility

## Impact

**Code Changes:**

- `frontends/android-app/app/src/main/res/layout/fragment_schedule.xml` -
  restructure the form, add primary and advanced settings controls, helper text,
  and Material 3 input styling
- `frontends/android-app/app/src/main/java/io/github/phunguy65/zms/presentation/main/schedule/ScheduleFragment.java` -
  wire new controls, blur validation, picker accessibility, helper text, and
  loading-state behavior
- `frontends/android-app/app/src/main/java/io/github/phunguy65/zms/presentation/schedule/ScheduleViewModel.java` -
  align validation, manage expanded settings state, and build the richer
  schedule request
- `frontends/android-app/app/src/main/java/io/github/phunguy65/zms/domain/model/ScheduleMeetingRequest.java` -
  carry all schedule settings needed for backend submission
- `frontends/android-app/app/src/main/java/io/github/phunguy65/zms/domain/model/MeetingSettingsInput.java` -
  encapsulate schedule settings selected by the user
- `frontends/android-app/app/src/main/java/io/github/phunguy65/zms/data/repository/MeetingRepositoryImpl.java` -
  map schedule settings to `MeetingManagementScheduleMeetingRequest` using
  actual user choices
- `frontends/android-app/app/src/main/res/values/strings.xml` - add labels,
  helper text, accessibility descriptions, validation messages, and setting text

**APIs Used:**

- `POST /api/v1/meetings:schedule`

**Systems Affected:**

- Android scheduled meeting creation flow
- Android meeting settings request mapping to the backend meeting-management API
- Android form validation, accessibility, and Material 3 schedule-form
  presentation
