# Verify Fixes

## [2026-04-25] Round 1 (from apply auto-verify)

### Verifier

- Fixed: Added `_meetingEndedForAll` MutableLiveData signal to `CallViewModel`
  so that `ActiveCallFragment` can observe it and call
  `requireActivity().finish()` after a successful end-meeting response. This
  ensures the activity closes asynchronously after the backend confirms rather
  than immediately on dialog dismiss.
- Fixed: Added observer for `getMeetingEndedForAll()` in
  `ActiveCallFragment.setupObservers()` to call `requireActivity().finish()`
  when the signal fires.
- Fixed: Reordered `cancelSuccess` observer in `ScheduleFragment` to show the
  Snackbar before calling `popBackStack()`, matching the pattern used by
  `scheduleSuccess` and `updateSuccess` observers to avoid calling
  `requireView()` on a detached fragment.
- Fixed: Added `SessionInfo` import to `ScheduleViewModel` and removed the
  unnecessary fully-qualified class reference in `getCurrentUserId()`.

## [2026-04-25] Round 2 (from apply auto-verify)

### Verifier

- Fixed: Added `EndMeetingUseCase` mock to `CallViewModelTest.setup()` and
  updated the `CallViewModel` constructor call to pass `endMeetingUseCase`,
  resolving the compilation failure introduced when `EndMeetingUseCase` was
  added to the constructor. Added four new tests covering `endMeetingForAll()`:
  no-meetingId guard, not-host guard, success path (loading cleared and
  `meetingEndedForAll` signalled), and failure path (error code set and user
  stays in call).
- Fixed: Created `ScheduleViewModelTest` with five tests covering
  `cancelMeeting()`: without-edit-mode guard, null-meetingId guard,
  non-SCHEDULED status guard, success path (loading cleared and `cancelSuccess`
  event fired), and failure path (loading cleared and `cancelError` contains
  message).
- Fixed: Replaced hardcoded English error strings and raw backend exception
  messages in `CallViewModel.endMeetingForAll()` with public string constant
  error codes (`ERROR_END_MEETING_NO_ID`, `ERROR_END_MEETING_NOT_HOST`,
  `ERROR_END_MEETING_FAILED`). Updated `ActiveCallFragment` to show
  `R.string.call_end_meeting_error` for any non-null error code instead of the
  raw string.
- Fixed: `ScheduleFragment.isCancelling` observer now also disables
  `btnScheduleMeeting` during cancel to prevent conflicting submissions. The
  `cancelError` observer re-enables both buttons on failure.
