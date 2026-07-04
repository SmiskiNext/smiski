# Why

Users need to view their past meetings to review what they've attended, check
meeting details, watch recordings, and see who participated. Currently, the
ProfileFragment has a "Meeting History" button that does nothing (TODO comment).
This feature completes the user profile experience and provides value for users
who want to reference their meeting history.

## What Changes

- Add new `MeetingHistoryFragment` screen accessible from ProfileFragment
  showing paginated list of past meetings (ENDED + CANCELLED)
- Add new `MeetingDetailFragment` screen showing full meeting details including
  participants, recordings
- Implement in-app video playback for meeting recordings using Media3/ExoPlayer
- Add domain layer components (models, repository, use cases) following existing
  Clean Architecture patterns
- Add AndroidVeil library for skeleton loading states

## Capabilities

### New Capabilities

- `meeting-history-list`: Display paginated list of user's participated meetings
  with infinite scroll, pull-to-refresh, empty state, and error handling
- `meeting-detail-view`: Display full meeting details including time info,
  description, participants list with roles, and recordings section
- `recording-playback`: In-app video playback for meeting recordings using
  Media3 ExoPlayer

### Modified Capabilities

<!-- No existing spec-level requirements are changing -->

## Impact

**Code Changes:**

- `presentation/main/history/` - New package with 2 fragments, 2 ViewModels, 1
  adapter
- `domain/model/` - New models: MeetingHistory, MeetingHistoryDetail,
  MeetingType, MeetingStatus, MeetingParticipant, MeetingRecording
- `domain/repository/` - New MeetingHistoryRepository interface
- `domain/usecase/history/` - New GetMeetingHistoryUseCase,
  GetMeetingDetailUseCase
- `data/repository/` - New MeetingHistoryRepositoryImpl
- `data/mapper/` - New MeetingHistoryMapper
- `ProfileFragment.java` - Wire navigation to meeting history

**Dependencies:**

- AndroidVeil (com.github.skydoves:androidveil) - Skeleton loading
- Media3 ExoPlayer (androidx.media3:media3-exoplayer, media3-ui) - Video
  playback

**APIs Used:**

- `GET /api/v1/users/{userId}/meetings:filter` - List meetings with status
  filter
- `GET /api/v1/users/{userId}/meetings/{meetingId}` - Meeting detail with
  participants/recordings

**Navigation:**

- Add `meetingHistoryFragment` destination to nav_graph_main.xml
- Add `meetingDetailFragment` destination with Safe Args (meetingId: String)
- Add actions from profile → history → detail
