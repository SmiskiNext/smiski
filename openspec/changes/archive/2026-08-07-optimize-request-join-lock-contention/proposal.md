## Why

Meeting join operations currently hold a pessimistic database lock while
performing expensive JWT token generation (50-500ms), causing severe lock
contention when multiple users attempt to join concurrently. This serializes all
join attempts to the same meeting and blocks other operations (host
ending/canceling meeting, accepting other join requests). The lock does not
actually guarantee capacity enforcement (webhook-based participation log is the
source of truth, creating an inevitable race window), making the current lock
duration unnecessarily expensive for a best-effort protection.

## What Changes

- Introduce optimistic pre-check for meeting existence and capacity (dirty read,
  no lock)
- Move JWT token generation outside the locked section for both
  `RequestJoinApplicationService` and `AcceptJoinRequestsApplicationService`
- Add pessimistic final verification phase (short lock) to re-check capacity and
  persist results
- Remove pessimistic lock entirely from MANUAL_APPROVAL pending request path (no
  capacity check needed)
- Add non-locking `findActiveById` repository method for optimistic reads
- Log race occurrences when optimistic check passes but final verification fails

## Capabilities

### New Capabilities

<!-- None - this is a performance optimization that modifies existing join behavior -->

### Modified Capabilities

- `meeting-join`: Change lock acquisition timing and duration - optimistic
  pre-check + token generation outside lock + short final verification lock
  instead of holding lock for entire operation
- `join-request-approval`: Change lock acquisition timing - pre-generate tokens
  outside lock, only lock for final capacity verification and state persistence

## Impact

- **Services affected**: `services/meet`
- **Classes modified**:
    - `RequestJoinApplicationService` - restructure to 3-phase: optimistic check
      → generate token → lock for final verify
    - `AcceptJoinRequestsApplicationService` - pre-generate tokens outside lock,
      lock only for final batch verification
    - `MeetingRepository` (port) + `MeetingJpaRepository` +
      `MeetingRepositoryAdapter` - add non-locking `findActiveById` method
- **Behavior changes**:
    - Lock hold time reduced from ~50-500ms to ~5ms for join operations
    - Race condition handling: return `MeetingFull` error when optimistic check
      passes but final verification fails, with warning log
    - MANUAL_APPROVAL pending path no longer acquires lock (idempotency handled
      by Redis)
- **No schema changes required**
- **No API contract changes** - all endpoints maintain same request/response
  structure
- **Capacity guarantee remains best-effort** (unchanged from current behavior
  due to webhook-based participation logging)
