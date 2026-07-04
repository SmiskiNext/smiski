## [2026-04-17] Round 1 (from opsx-apply auto-verify)

### opsx-uiux-verifier

- Fixed: CRITICAL — Runtime crash due to btnBackWrapper type mismatch
  (LinearLayout→FrameLayout) in MeetingHistoryFragment.java:44
- Fixed: CRITICAL — Accessibility for cancelled meetings: added
  contentDescription with localized string in MeetingHistoryAdapter binding
- Fixed: WARNING — Badge text size from 10sp→11sp in item_meeting_history.xml,
  fragment_meeting_detail.xml, item_participant_detail.xml
- Fixed: WARNING — Hardcoded 120dp→@dimen/avatar_size_xl in
  layout_empty_meeting_history.xml and fragment_meeting_history.xml error state
- Fixed: WARNING — Hardcoded 40dp→@dimen/icon_container_md in item_recording.xml
  play icon container
- Fixed: WARNING — Localized meeting type/status badges via new
  localizedTypeBadge/localizedStatusBadge methods in MeetingDetailFragment,
  badgeTextForType switch in MeetingHistoryAdapter; added meeting*type\*\*,
  meeting*status\*\*, cd_meeting_cancelled_format strings to EN/VI

### opsx-test-verifier

- Fixed: CRITICAL — Missing GetMeetingDetailUseCaseTest (new file created)
- Fixed: CRITICAL — Missing refresh_failure_whileInSuccess test in
  MeetingHistoryViewModelTest
- Fixed: CRITICAL — Missing loadInitial_canBeRetriedAfterFailure test in
  MeetingHistoryViewModelTest
- Fixed: CRITICAL — Missing null body edge case tests in
  MeetingHistoryRepositoryImplTest
  (getMeetingHistory_successfulResponseWithNullBody_failsFuture,
  getMeetingDetail_successfulResponseWithNullBody_failsFuture)

## [2026-04-17] Round 2 (from re-verify)

### opsx-arch-verifier

- Fixed: CRITICAL — Unmanaged async work in MeetingHistoryViewModel: added
  `List<CompletableFuture<?>> activeFutures`, `trackFuture()` method to track
  all async operations, and `onCleared()` override to cancel in-flight futures.
  Now matches project convention (LoginViewModel, SplashViewModel pattern).
- Fixed: CRITICAL — MeetingDetailViewModel missing async cleanup: added same
  `activeFutures` + `trackFuture()` + `onCleared()` pattern for detail loads.

### opsx-plan-verifier

- Acknowledged: Safe Args vs manual Bundle navigation — current implementation
  uses nav XML argument typing with manual Bundle/SavedStateHandle, which is
  functionally equivalent and acceptable (nav args are typed in XML, just not
  using generated Directions classes).
- Acknowledged: MeetingHistoryMapper vs MeetingMapper — implementation reuses
  existing shared MeetingMapper which is intentional (DRY) and artifact drift is
  minor documentation concern, not code issue.
