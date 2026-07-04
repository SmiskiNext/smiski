# Tasks

## 1. Dependencies Setup

- [x] 1.1 Add AndroidVeil dependency to app/build.gradle.kts
      (com.github.skydoves:androidveil:1.1.4)
- [x] 1.2 Add Media3 ExoPlayer dependencies (androidx.media3:media3-exoplayer,
      media3-ui)
- [x] 1.3 Add version entries to gradle/libs.versions.toml if not present
- [x] 1.4 Sync Gradle and verify dependencies resolve

## 2. Domain Layer - Models

- [x] 2.1 Create MeetingType enum (INSTANT, SCHEDULED)
- [x] 2.2 Create MeetingStatus enum (SCHEDULED, LIVE, ENDED, CANCELLED)
- [x] 2.3 Create MeetingParticipant record (userId, displayName, role, joinedAt,
      leftAt)
- [x] 2.4 Create MeetingRecording record (id, fileUrl, durationSeconds,
      createdAt)
- [x] 2.5 Create MeetingHistory record for list items (id, title, startTime,
      endTime, type, status)
- [x] 2.6 Create MeetingHistoryDetail record with participants and recordings
      lists

## 3. Domain Layer - Repository & UseCases

- [x] 3.1 Create MeetingHistoryRepository interface with getMeetingHistory() and
      getMeetingDetail()
- [x] 3.2 Create GetMeetingHistoryUseCase with CompletableFuture return type
- [x] 3.3 Create GetMeetingDetailUseCase with CompletableFuture return type

## 4. Data Layer - Repository Implementation

- [x] 4.1 Create MeetingHistoryMapper to convert DTOs to domain models
- [x] 4.2 Create MeetingHistoryRepositoryImpl using UserMeetingsApi
- [x] 4.3 Implement getMeetingHistory with status filter and pagination
- [x] 4.4 Implement getMeetingDetail for single meeting with
      participants/recordings
- [x] 4.5 Add @Binds for MeetingHistoryRepository in RepositoryModule

## 5. Presentation Layer - Meeting History List

- [x] 5.1 Create MeetingHistoryUiState sealed interface (Loading, Success,
      Empty, Error)
- [x] 5.2 Create MeetingHistoryViewModel with LiveData and pagination logic
- [x] 5.3 Create item_meeting_history.xml layout with VeilRecyclerFrameView
      support
- [x] 5.4 Create layout_empty_meeting_history.xml with illustration and CTA
- [x] 5.5 Create fragment_meeting_history.xml with VeilRecyclerFrameView,
      SwipeRefreshLayout
- [x] 5.6 Create MeetingHistoryAdapter with DiffUtil for efficient updates
- [x] 5.7 Implement cancelled meeting styling (strikethrough, opacity, red
      badge)
- [x] 5.8 Create MeetingHistoryFragment with skeleton loading, pagination,
      pull-to-refresh

## 6. Presentation Layer - Meeting Detail

- [x] 6.1 Create MeetingDetailUiState sealed interface (Loading, Success, Error)
- [x] 6.2 Create MeetingDetailViewModel with LiveData
- [x] 6.3 Create item_participant_detail.xml layout with avatar and role badge
- [x] 6.4 Create item_recording.xml layout with play icon and duration
- [x] 6.5 Create fragment_meeting_detail.xml with header, time info,
      participants, recordings sections
- [x] 6.6 Implement MeetingDetailFragment with expandable participants list
- [x] 6.7 Integrate Media3 PlayerView for recording playback
- [x] 6.8 Implement playback lifecycle management (pause/resume/release)

## 7. Navigation Integration

- [x] 7.1 Add meetingHistoryFragment destination to nav_graph_main.xml
- [x] 7.2 Add meetingDetailFragment destination with Safe Args (meetingId
      argument)
- [x] 7.3 Add action_profile_to_meetingHistory action
- [x] 7.4 Add action_meetingHistory_to_meetingDetail action
- [x] 7.5 Wire ProfileFragment btnMeetingHistory click to navigate

## 8. Resources

- [x] 8.1 Add all required strings to strings.xml (titles, empty state, error
      messages)
- [x] 8.2 Add Vietnamese translations to values-vi/strings.xml
- [x] 8.3 Add ic_play icon for recordings (if not exists)
- [x] 8.4 Verify all dimension tokens exist (spacing, corner radius, touch
      targets)

## 9. Unit Tests

- [x] 9.1 Create MeetingHistoryViewModelTest with Mockito
- [x] 9.2 Create MeetingDetailViewModelTest with Mockito
- [x] 9.3 Create GetMeetingHistoryUseCaseTest
- [x] 9.4 Create MeetingHistoryRepositoryImplTest
- [ ] 9.5 Run all tests and ensure passing (blocked by pre-existing environment
      setup: `:services:shared` artifact must be published to `~/.m2` before
      `:app` can resolve its classpath)

## 10. Verification & Polish

- [ ] 10.1 Run ./gradlew build and fix any compilation errors (blocked by
      pre-existing environment setup: shared module needs local publish)
- [ ] 10.2 Run ./gradlew lint and address any warnings (blocked — same)
- [ ] 10.3 Test on emulator: happy path flow (profile → history → detail →
      playback) (manual)
- [ ] 10.4 Test empty state when no meetings (manual)
- [ ] 10.5 Test error state and retry functionality (manual)
- [ ] 10.6 Test pagination with infinite scroll (manual)
- [ ] 10.7 Verify accessibility (TalkBack, touch targets, contrast) (manual)
- [ ] 10.8 Verify dark mode appearance (manual)
