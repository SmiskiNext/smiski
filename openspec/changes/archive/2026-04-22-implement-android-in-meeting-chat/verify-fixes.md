## [2026-04-22] Round 1 (from apply auto-verify)

### CRITICAL: 3 failing unit tests

- Fixed: Added `testImplementation("org.json:json:20231013")` to
  `app/build.gradle.kts` so that `org.json.JSONObject` is available in JVM unit
  tests, resolving
  `ChatMessageMapperTest.fromLiveKitPayload_validSystemPayload_mapsFields` and
  `fromLiveKitPayload_missingRequiredFields_returnsNull`
- Fixed: Changed `ChatMessageMerger.upsert` guard condition from
  `candidate.getId() == null && candidate.getSeqNum() <= 0` to
  `candidate.getId() == null` so that messages with null IDs are always filtered
  regardless of seqNum, resolving `ChatMessageMergerTest.merge_nullIdsIgnored`

### WARNING: Send-failure UX does not preserve typed message

- Fixed: Removed immediate `edtMessage.setText("")` from send button click
  listener in `MeetingChatBottomSheet`
- Fixed: Added `_sendSuccess` one-shot LiveData signal to `MeetingChatViewModel`
  that emits `true` only after successful send
- Fixed: Added `getSendSuccess()` observer in
  `MeetingChatBottomSheet.setupObservers()` that clears the input field only on
  successful send, preserving typed text on failure for retry

### WARNING: Hardcoded error messages instead of string resources

- Fixed: Added string resources `chat_meeting_not_active`,
  `chat_failed_to_load`, and `chat_failed_to_send` to both `values/strings.xml`
  and `values-vi/strings.xml`
- Fixed: Expanded `ChatUiState.Error` record to hold both
  `@StringRes int messageResId` and `String message` with convenience
  constructors for each usage pattern
- Fixed: Updated `MeetingChatViewModel.initialize()` and `loadHistory()` to use
  `R.string.chat_meeting_not_active` and `R.string.chat_failed_to_load` resource
  IDs instead of hardcoded strings
- Fixed: Updated `MeetingChatBottomSheet.showError()` to prefer resource ID when
  available, falling back to string message, then to `R.string.error_unknown`
- Fixed: Updated `MeetingChatBottomSheet.initializeChat()` to use
  `getString(R.string.chat_meeting_not_active)` for the fallback error case

### WARNING: No test for malformed payload handling in ChatDataMessageHandler

- Fixed: Created `ChatDataMessageHandlerTest.java` with 10 test cases covering
  null data, empty data, non-JSON text, malformed JSON, binary garbage, empty
  JSON object, missing required fields, valid payload dispatch, and
  mapper-returns-null scenarios
- Fixed: Used `MockedStatic<Log>` to mock `android.util.Log` since the handler's
  catch block calls `Log.d()` which is not available in JVM unit tests
