# Why

Android hosts can already schedule meetings, but they cannot attach invitees
even though the backend fully supports invitee submission and validation. Adding
invitee entry to the Android schedule flow closes that platform gap now, so
hosts can invite attendees during creation without waiting for backend or API
changes.

## What Changes

- Extend the Android schedule meeting form with an invitee entry section that
  lets hosts add up to 10 attendee email addresses before submitting a new
  meeting.
- Add Android-side invitee validation for email format, duplicate detection
  using case-insensitive comparison, and client-side max-count enforcement with
  inline form errors.
- Carry invitees through the Android schedule creation flow from presentation
  state to the domain request model and repository-to-API request mapping.
- Hide the invitee section in schedule edit mode because invitees are only
  supported during meeting creation in the Android client.
- Add localized Android strings and accessibility labels required for invitee
  chips, removal actions, and validation feedback.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `android-meeting-creation`: Expand Android scheduled meeting creation
  requirements to support invitee entry, validation, request mapping, and
  create-only invitee behavior.

## Impact

- Affected Android UI and presentation files: `ScheduleFragment.java`,
  `fragment_schedule.xml`, `strings.xml`, and `ScheduleViewModel.java`.
- Affected Android domain/data layers: `ScheduleMeetingRequest.java` and
  `MeetingRepositoryImpl.java`.
- No backend, navigation, dependency, or generated API DTO changes are required.
