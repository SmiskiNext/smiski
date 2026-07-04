# Tasks

## 1. Critical media control correctness (P0)

- [x] 1.1 Update `ParticipantsBottomSheet` mute-all UI to hidden/disabled state
      when backend mute-all is unsupported, remove success Snackbar path, and
      add explicit TODO marker near the unavailable action.
- [x] 1.2 Implement `LiveKitRepositoryImpl.switchCamera()` by resolving the
      active local camera track and toggling front/back camera position using
      current LiveKit SDK APIs.
- [x] 1.3 Refactor room connection contract so
      `CallViewModel.connectToRoom(...)` passes desired initial mic/cam states
      into repository connect flow.
- [x] 1.4 Apply initial microphone and camera states only after successful
      LiveKit room connection, and add warning logs for pre-room toggle calls in
      repository media-control methods. ← (verify: joining with mic/cam pre-join
      toggles consistently applies final local AV state without race-related
      mismatch)

## 2. Waiting-room consistency and SSE lifecycle fixes (P0 + P2)

- [x] 2.1 Change `CallViewModel.syncPendingRequests()` to merge API response
      into current local pending list by request ID, preserving SSE-added
      entries and only appending missing items.
- [ ] 2.2 Move waiting-room SSE connected-state assignment so
      `_isWaitingRoomSseConnected` is set true only from confirmed SSE
      `onConnected` callback.
- [ ] 2.3 Update `WaitingRoomBottomSheet` to observe
      `CallViewModel.getPendingJoinRequests()` and drive adapter updates from
      LiveData changes rather than one-time sheet load.
- [ ] 2.4 Validate waiting-room host flow across connect/reconnect/event bursts
      to ensure pending list, badge count, and sheet contents remain
      synchronized. ← (verify: simulated `join_request_created` before/after
      sync API completion does not overwrite newer local state)

## 3. Participant identity and role resolution hardening (P0)

- [ ] 3.1 Update LiveKit participant ID extraction (`getParticipantId()` and
      related mapping) to use participant `identity` as canonical ID rather than
      display name.
- [ ] 3.2 Refactor participant role resolution in
      `ParticipantsViewModel`/`ParticipantRepositoryImpl` to match roles by
      stable participant ID only and remove display-name fallback mapping.
- [ ] 3.3 Confirm unmatched or missing-role participants default to
      `PARTICIPANT` while preserving visibility and live mic/cam state. ←
      (verify: duplicate display names in one meeting no longer cause role
      collisions or incorrect host badges)

## 4. RecyclerView performance optimizations (P1)

- [ ] 4.1 Convert `ParticipantAdapter` from manual adapter updates to
      `ListAdapter` + `DiffUtil.ItemCallback` with ID-based item matching and
      content checks for name/role/mic/cam fields.
- [ ] 4.2 Convert `JoinRequestAdapter` to `ListAdapter` +
      `DiffUtil.ItemCallback` keyed by request ID, and replace manual refresh
      method usage with `submitList(...)`.
- [ ] 4.3 Refactor `VideoGridAdapter.setActiveSpeakers()` to compute old/new
      active-speaker membership delta and call `notifyItemChanged()` only for
      affected positions.
- [ ] 4.4 Remove redundant participant-list emission from
      `LiveKitRepositoryImpl.updateActiveSpeakers()` so active speaker changes
      propagate only through speaker-specific callback/state path. ← (verify:
      one active-speaker event causes only targeted tile highlight updates and
      no full participant list rebind)

## 5. Waiting-room repository error handling hardening (P2)

- [ ] 5.1 Add `IllegalArgumentException` handling alongside `IOException` in all
      `WaitingRoomRepositoryImpl` methods that parse UUID strings via
      `UUID.fromString(...)`.
- [ ] 5.2 Ensure malformed UUID input returns existing repository
      error/empty-result responses without crashing callers. ← (verify: invalid
      UUID strings in each waiting-room method are handled gracefully and
      preserve current UI error behavior)
