# Verify Fixes Log

## [2026-04-20] Round 1 (from apply auto-verify)

### Applied Fixes

**CRITICAL 1: Host/meeting context not connected**

- Fixed: `CallViewModel.handleJoinResult()` now calls
  `initializeMeetingContext()` to set meeting ID and fetch host status
- Added: `MeetingDetail` record now includes `hostId` field
- Added: `MeetingMapper.toMeetingDetail()` now maps hostId from API response
- Added: `CallViewModel.fetchHostStatus()` compares session user ID with meeting
  host ID

**CRITICAL 2: MeetingSettingsBottomSheet async success handling**

- Fixed: Removed immediate success Snackbar and dismiss from `applySettings()`
- Added: `CallViewModel._settingsUpdateSuccess` LiveData to track update success
- Added: Observer in `MeetingSettingsBottomSheet.setupObservers()` for success
  event
- Fixed: Sheet now dismisses only after successful API response via observer

**WARNING 1: Vietnamese translations incomplete**

- Added: `schedule_update_button`, `schedule_edit_title`,
  `schedule_update_success`, `schedule_load_error` to values-vi/strings.xml
- Added: All `upcoming_action_*` strings to values-vi/strings.xml with proper
  Vietnamese translations

**Build status:** Compilation successful

## [2026-04-20] Round 2 (from apply fix)

### Applied Fixes

**CRITICAL 3: Join flow breaks when app uses shortCode instead of UUID**

Root cause: `JoinRoomRepositoryImpl.requestJoin()` called
`UUID.fromString(meetingCode)` immediately, but callers (Dashboard,
CreateMeeting, PreJoin) pass shortCodes not UUIDs.

- Fixed: `JoinRoomRepository.requestJoin()` interface now accepts optional
  `meetingUuid` parameter alongside `meetingCode`
- Fixed: `JoinRoomRepositoryImpl.requestJoin()` now uses `resolveMeetingId()`
  helper that:
    1. First tries `meetingUuid` if provided (from Dashboard/CreateMeeting
       flows)
    2. Falls back to parsing `meetingCode` as UUID (for backward compatibility)
    3. If neither works, calls `getMeetingByShortCode(meetingCode)` API to
       resolve
- Fixed: `CallViewModel.requestJoinRoom()` now passes `meetingUuid` field to
  repository
- Added: `MeetingsApi` dependency injection to `JoinRoomRepositoryImpl`

**WARNING 2: Dashboard doesn't refresh after editing a meeting**

- Fixed: Added `onResume()` lifecycle method to `DashboardFragment`
- Fixed: Dashboard now calls `viewModel.loadUpcomingMeetings()` on every resume,
  ensuring the list refreshes after navigating back from edit screen

**Build status:** Compilation successful (assembleDebug)

## [2026-04-20] Round 3 (from verification feedback)

### Applied Fixes

**WARNING 1: Post-join meeting context wrong for short-code-only joins**

Root cause: `CallViewModel.initializeMeetingContext()` used `meetingCode` as
fallback when `meetingUuid` was null, but downstream API calls expect UUID.

- Fixed: Added `meetingUuid` field to `JoinRoomResult` model
- Fixed: `JoinRoomRepositoryImpl.mapToJoinRoomResult()` now passes resolved UUID
  to result
- Fixed: `CallViewModel.handleJoinResult()` updates `meetingUuid` field from
  result
- Fixed: `initializeMeetingContext()` now uses the resolved UUID correctly

**WARNING 2: Specific "meeting not found" errors lost in translation**

- Fixed: `translateException()` now preserves `JoinRoomException` messages
- "Meeting not found" error from 404 is now shown correctly to users

**WARNING 3: Join error handling may expose wrapped exception text**

- Fixed: `CallViewModel.requestJoinRoom()` now unwraps
  `CompletionException.getCause()`
- Error messages are now user-friendly, consistent with other ViewModel flows

**WARNING 4: Caller parameter integrity drift when prefilled code is edited**

- Fixed: `CallViewModel.setMeetingCode()` now clears cached `meetingUuid` when
  code changes
- When user edits the prefilled meeting code, the join flow resolves fresh from
  API

**Build status:** Compilation successful
