# Tasks

## 1. Enrich settings update events

- [x] 1.1 Add `oldSettings` and `newSettings` to `MeetingSettingsUpdatedEvent`
      and update event metadata tests to assert the richer payload
- [x] 1.2 Update `Meeting.updateSettings()` and related use-case tests so the
      published event captures both pre-update and post-update settings ←
      (verify: successful PUT settings updates publish unchanged event metadata
      plus correct old/new settings snapshots)

## 2. Unify LiveKit grant derivation

- [x] 2.1 Extend `LiveKitTokenRequest` and all token-generation call sites
      (`RequestJoinUseCase`, `ApproveJoinRequestUseCase`,
      `PendingJoinRequestApprover`) to pass `MeetingSettings`
- [x] 2.2 Add shared grant computation from `MeetingSettings` and
      `ParticipantRole` in `ParticipantGrants` or an equivalent centralized
      helper
- [x] 2.3 Update `LiveKitAdapter.generateToken()` to apply host/guest fixed
      behavior and participant source/data restrictions from meeting settings ←
      (verify: generated tokens keep HOST full access, keep GUEST
      subscribe-only, and restrict PARTICIPANT publish/data grants exactly per
      meeting settings)

## 3. Sync active participant permissions after live settings changes

- [x] 3.1 Create `MeetingSettingsChangedHandler` as an asynchronous listener for
      `MeetingSettingsUpdatedEvent`
- [x] 3.2 Implement LIVE-only and permission-fields-only filtering before any
      repository or LiveKit work
- [x] 3.3 Load active sessions with
      `ParticipationLogRepository.findActiveByMeetingId()`, skip HOST and GUEST
      sessions, and update each PARTICIPANT via
      `LiveKitPort.updateParticipantPermissions()`
- [x] 3.4 Add best-effort error handling and structured logging so one failed
      participant update does not stop the rest ← (verify: relevant LIVE changes
      update only active PARTICIPANT sessions and continue processing after
      individual LiveKit failures)

## 4. Cover permission behavior with tests

- [x] 4.1 Update existing unit tests for join flows and settings updates to
      match the new token request shape and event payload
- [x] 4.2 Add focused tests for shared grant computation and
      `LiveKitAdapter.generateToken()` permission mapping
- [x] 4.3 Add handler tests for non-LIVE meetings, irrelevant setting changes,
      active participant filtering, and best-effort continuation ← (verify: test
      suite covers token issuance and runtime sync paths for HOST, PARTICIPANT,
      and GUEST behavior)

## 5. Run verification

- [x] 5.1 Run `./gradlew :services:meeting-management:test` ← (verify: backend
      tests pass with enriched event payloads, token restrictions, and live
      permission sync behavior)
