# Context

The Android app follows MVVM + Clean Architecture with Hilt DI. ProfileFragment
currently has a "Meeting History" menu item with a TODO click handler. The
backend API (`UserMeetingsApi`) is already generated and wired in Hilt's
NetworkModule, providing endpoints for listing user meetings and getting meeting
details.

**Current State:**

- `ProfileFragment` has `btnMeetingHistory` with empty click listener (TODO)
- `UserMeetingsApi` provides `listParticipatedMeetings()` and
  `getParticipatedMeetingDetail()`
- DTOs are generated: `MeetingManagementMeetingResponse`,
  `MeetingManagementMeetingDetailResponse`
- `CursorPageResponse<T>` exists in shared module for pagination
- `InitialsDrawable` exists for avatar fallback
- No video player library currently in dependencies

**Constraints:**

- Must follow existing patterns (Java, MVVM, Hilt, CompletableFuture)
- Must support both light and dark themes
- Must be accessible (48dp touch targets, content descriptions)

## Goals / Non-Goals

**Goals:**

- Display user's meeting history with infinite scroll pagination
- Show meeting details including participants and recordings
- Enable in-app playback of meeting recordings
- Follow existing architectural patterns and conventions
- Provide proper loading, empty, and error states

**Non-Goals:**

- Offline caching of meeting history (future enhancement)
- Downloading recordings for offline viewing
- Editing or deleting meetings from history
- Real-time updates when new meetings end
- Search/filter within meeting history

## Decisions

### D1: Feature-specific sealed UiState over shared generic UiState

**Decision:** Use feature-specific sealed interfaces (`MeetingHistoryUiState`,
`MeetingDetailUiState`) instead of the shared `UiState<T>`.

**Rationale:** Meeting history requires pagination state (`isLoadingMore`,
`nextPageToken`, `hasMore`) which doesn't fit the generic pattern.
Feature-specific states also provide better type safety and clearer intent.

**Alternatives considered:**

- Shared `UiState<T>` + separate pagination LiveData → More complex, two sources
  of truth
- Generic `PaginatedUiState<T>` → Over-engineering for single use case

### D2: AndroidVeil for skeleton loading

**Decision:** Use AndroidVeil library (`com.github.skydoves:androidveil:1.1.4`)
for shimmer/skeleton loading states.

**Rationale:** Provides `VeilRecyclerFrameView` which wraps RecyclerView and
automatically handles skeleton placeholders. Minimal integration effort,
well-maintained library.

**Alternatives considered:**

- Facebook Shimmer → More manual setup, need custom shimmer layout
- Custom ObjectAnimator → More code, reinventing the wheel
- Simple ProgressBar → Poor UX compared to skeleton loading

### D3: Media3 ExoPlayer for video playback

**Decision:** Use AndroidX Media3 (`androidx.media3:media3-exoplayer`,
`media3-ui`) for recording playback.

**Rationale:** Media3 is Google's recommended media library (successor to
ExoPlayer), actively maintained, handles streaming/buffering, provides
ready-made `PlayerView` UI component.

**Alternatives considered:**

- Open URL in browser → Poor UX, leaves app
- System video player intent → Inconsistent UX across devices
- Legacy ExoPlayer → Deprecated in favor of Media3

### D4: ScheduleFragment-style back navigation

**Decision:** Use ImageView `btnBack` with
`Navigation.findNavController(v).popBackStack()` pattern.

**Rationale:** Consistent with existing full-screen fragments (ScheduleFragment,
CreateMeetingFragment). Simple, no custom back press handling needed.

**Alternatives considered:**

- MaterialToolbar with NavigationUI → Overkill for simple back navigation
- OnBackPressedCallback → More complex, not needed for simple pop

### D5: Cancelled meeting visual treatment

**Decision:** Strikethrough title text + 0.7 alpha opacity + red CANCELLED
badge.

**Rationale:** Clear visual distinction without hiding information. User can
still read the title and see it was cancelled. Follows common UI patterns for
cancelled/deleted items.

**Alternatives considered:**

- Only badge, no opacity → Less obvious at a glance
- Dimmed only, no strikethrough → Could be confused with disabled state
- Hide cancelled meetings → User loses information

### D6: Domain model structure

**Decision:** Create separate `MeetingHistory` (list item) and
`MeetingHistoryDetail` (full detail) domain models rather than using one model
with nullable fields.

**Rationale:** List items don't need participants/recordings arrays. Separate
models provide clear contracts and avoid null checks. Follows existing pattern
(e.g., `User` vs detailed user responses).

## Risks / Trade-offs

### R1: New dependencies increase APK size

**Risk:** AndroidVeil (~50KB) and Media3 (~2MB) increase app size.
**Mitigation:** Media3 is essential for video playback; consider R8/ProGuard
optimization. Size increase is acceptable for the functionality gained.

### R2: Video playback complexity

**Risk:** Media3 has a learning curve; streaming issues could occur.
**Mitigation:** Start with basic `PlayerView` setup. Add error handling for
playback failures. Consider adding retry mechanism for failed playback.

### R3: API returns empty recordings for most meetings

**Risk:** Recordings section may often be empty, wasting screen space.
**Mitigation:** Hide entire recordings section when empty. Only show header
"Recordings (N)" when N > 0.

### R4: Large participant lists

**Risk:** Meetings with many participants could make detail screen very long.
**Mitigation:** Show first 5 participants + "Show all N participants"
expandable. Keep initial view compact.

### R5: userId format mismatch

**Risk:** Domain uses String IDs, API expects UUID format. **Mitigation:**
Handle conversion in repository layer with `UUID.fromString()`. Add
validation/error handling for malformed IDs.
