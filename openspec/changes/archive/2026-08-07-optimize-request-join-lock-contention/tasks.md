## 1. Repository Layer - Add Non-Locking Read

- [x] 1.1 Add `Optional<Meeting> findActiveById(UUID id)` method signature to
      `MeetingRepository` port
- [x] 1.2 Add `@Query` method `findActiveById` to `MeetingJpaRepository`
      (without `@Lock`, filter `deleted_at IS NULL`)
- [x] 1.3 Implement `findActiveById` in `MeetingRepositoryAdapter` delegating to
      JPA repository
- [x] 1.4 Add tests for repository adapter covering new method: unit test for
      delegation (`MeetingRepositoryAdapterTest`) plus container-backed
      `MeetingActiveLookupIntegrationTest` because the `deleted_at IS NULL`
      filter lives in the JPQL query ← (verify: non-locking read returns active
      meetings, excludes soft-deleted)

## 2. RequestJoinApplicationService - Restructure to 3 Phases

- [x] 2.1 Extract optimistic pre-check: use `findActiveById` (no lock) +
      `countActiveByMeetingId` to fail fast when meeting not found or capacity
      full
- [x] 2.2 Move invitee lookup (`findByMeetingIdAndAccountId`) to optimistic
      phase (before token generation, no lock)
- [x] 2.3 Move token generation (`liveKitPort.generateToken`) outside any locked
      section (between optimistic and final phases)
- [x] 2.4 Create final verification phase: acquire lock via
      `findActiveByIdWithLock`, re-check capacity, perform Redis reconciliation
      if needed
- [x] 2.5 Add warning log when race detected (optimistic passed, final failed):
      `log.warn("Capacity race detected for meeting {}", meetingId)`
- [x] 2.6 For MANUAL_APPROVAL pending path: remove lock entirely, use
      `findActiveById` to validate meeting exists ← (verify: ALLOW_ALL +
      MANUAL_APPROVAL bypass use 3-phase, pending path uses no lock, race
      logging works)

## 3. AcceptJoinRequestsApplicationService - Pre-Generate Tokens

- [x] 3.1 Extract batch token generation outside lock: iterate `requestIds`,
      load each request, generate token, store in map
- [x] 3.2 Acquire `findActiveByIdWithLock` only after all tokens generated
- [x] 3.3 Read capacity once under lock, decrement in-memory for each approved
      request
- [x] 3.4 Use pre-generated tokens from map when marking requests approved ←
      (verify: tokens generated before lock, capacity enforced under lock, batch
      processing maintains best-effort per-item)

## 4. Update Unit Tests

- [x] 4.1 Update `RequestJoinApplicationServiceTest`: mock `findActiveById` for
      phase 1, `findActiveByIdWithLock` for phase 3
- [x] 4.2 Add test scenario: optimistic check passes, final check fails (verify
      `MeetingFull` returned, no token issued)
- [x] 4.3 Update `AcceptJoinRequestsApplicationServiceTest`: verify token
      generation happens before lock acquisition via explicit
      `Mockito.inOrder()` assertion
- [x] 4.4 Verify existing scenarios still pass: ALLOW_ALL at capacity,
      MANUAL_APPROVAL bypass, pending request creation, reconciliation ←
      (verify: all existing test scenarios pass with new implementation)

## 5. Integration Tests for Concurrency

- [x] 5.1 Add integration test: simulate concurrent join attempts (5+ threads)
      to same meeting with 1 remaining seat
- [x] 5.2 Verify concurrent joins complete without deadlock, admitted callers
      get distinct tokens, and recorded active participants never exceed
      `maxParticipants`; a meeting already at capacity rejects every concurrent
      caller with `MeetingFull`, and a race that fills capacity during token
      generation yields `MeetingFull` (best-effort guarantee — participation
      logs are webhook-written, so callers that all pass the optimistic check
      before any webhook fires are all admitted, per design non-goal "hard
      capacity guarantee")
- [x] 5.3 Add integration test: concurrent MANUAL_APPROVAL bypass joins
      reconcile a same-device pending request exactly once (Redis reconciliation
      idempotent under lock)
- [x] 5.4 Add integration test: accept batch enforces capacity under lock with
      pre-generated tokens, and concurrent accept batches approve each request
      exactly once ← (verify: concurrent access maintains capacity guarantee, no
      deadlocks, Redis reconciliation remains idempotent)

## 6. Code Quality and Documentation

- [x] 6.1 Add inline comments explaining phase boundaries in
      `RequestJoinApplicationService`
- [x] 6.2 Add inline comments explaining token pre-generation in
      `AcceptJoinRequestsApplicationService`
- [x] 6.3 Run `./services/gradlew spotlessApply` to format code
- [x] 6.4 Verify no warnings in `./services/gradlew -p services/meet build` ←
      (verify: code formatted, builds without warnings, javadoc complete for new
      methods)
