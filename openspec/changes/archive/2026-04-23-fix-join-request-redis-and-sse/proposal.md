# Why

The join request flow has 11 identified bugs across the backend Redis layer and
Android SSE clients causing data inconsistency (orphaned ZSET keys, non-atomic
multi-key writes, race conditions) and silent guest/host failures (no SSE
reconnection on transient network errors). These issues silently corrupt meeting
state and block guests from joining approved meetings.

## What Changes

- Fix ZSET TTL orphan: apply TTL on the ZSET key in `save()` and DEL empty ZSETs
  in the cleanup job
- Atomicize multi-key Redis writes: wrap `save()`, `removeFromQueue()`, and
  `deleteAllByMeetingId()` in pipeline/Lua to prevent partial-write
  inconsistency
- Replace N+1 GET loop with `MGET` in `findPendingByMeetingId()` and
  `findPendingSummariesByMeetingId()`
- Replace blocking `KEYS` scan with `SCAN` cursor in `JoinRequestCleanupJob`
- Fix `updateStatus()` race condition with an atomic Lua script
  (read-modify-write)
- Fix `MeetingEndedJoinRequestHandleUseCase` to call `updateStatus(DENIED)`
  before `deleteAllByMeetingId()`
- Add exponential-backoff SSE reconnection to `JoinRequestSseClient` and
  `MeetingEventSseClient`
- Replace fragile manual JSON parsing in `JoinRequestSseClient` with Gson data
  classes
- Raise `SSE_TIMEOUT_MINUTES` to a safe upper bound aligned with server-side TTL
- Add partial-failure summary to `PendingJoinRequestApprover` approve-all loop

## Capabilities

### New Capabilities

- `join-request-redis-reliability`: Atomic, TTL-safe, and N+1-free Redis
  persistence for join requests, including race-condition-free status updates
  via Lua script
- `join-request-sse-resilience`: Exponential-backoff SSE reconnection and
  Gson-based event parsing for both guest (`JoinRequestSseClient`) and host
  (`MeetingEventSseClient`) Android clients

### Modified Capabilities

None — these are implementation-level correctness fixes with no
requirement-level behavior changes visible to external API consumers.

## Impact

- `../../../../services/meeting-management/src/main/java/io/github/smiskinext/meetingmanagement/infrastructure/persistence/JoinRequestRedisRepositoryAdapter.java`
  — primary backend change target
- `services/meeting-management/.../infrastructure/jobs/JoinRequestCleanupJob.java`
  — SCAN replacement
- `services/meeting-management/.../application/usecase/MeetingEndedJoinRequestHandleUseCase.java`
  — status update before delete
- `services/meeting-management/.../application/helper/PendingJoinRequestApprover.java`
  — partial-failure handling
- `frontends/android-app/.../data/remote/sse/JoinRequestSseClient.java` — SSE
  resilience + Gson parsing
- `frontends/android-app/.../data/remote/sse/MeetingEventSseClient.java` — SSE
  resilience
- No database schema changes, no API contract changes, no Kafka topic changes
