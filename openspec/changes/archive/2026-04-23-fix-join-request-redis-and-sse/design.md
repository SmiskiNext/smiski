# Context

The join request flow in `meeting-management` stores all join request state in
Redis using three key types: a ZSET queue per meeting
(`join_request:{meetingId}`), a STRING meta key per request
(`join_request_meta:{requestId}`), and a STRING device-dedup key
(`join_request_device:{meetingId}:{deviceId}`). Android guests subscribe via
`JoinRequestSseClient` and hosts via `MeetingEventSseClient`, both using OkHttp
EventSource.

Current problems fall into three groups:

- **Redis data integrity**: ZSET keys have no TTL and therefore accumulate
  orphan members indefinitely; `save()`, `removeFromQueue()`, and
  `deleteAllByMeetingId()` are non-atomic (3+ separate commands);
  `findPendingByMeetingId` and `findPendingSummariesByMeetingId` execute an
  individual GET per queue member (N+1); `updateStatus()` does a non-atomic
  GET-modify-SET that can lose updates under concurrent access;
  `JoinRequestCleanupJob` uses `KEYS *` which blocks the Redis event loop.
- **Application logic**: `MeetingEndedJoinRequestHandleUseCase` publishes
  `JoinRequestDeniedEvent` and immediately calls `deleteAllByMeetingId()`
  without first calling `updateStatus(DENIED)`, producing a window where meta
  still shows PENDING while the SSE event already fired.
- **Android SSE resilience**: Both SSE clients call `listener.onError()` on
  connection failure with no retry, so a transient network blip permanently
  breaks the event stream. `JoinRequestSseClient` also parses event payloads
  with fragile `indexOf` string manipulation.

## Goals / Non-Goals

**Goals:**

- Eliminate ZSET TTL orphan accumulation with a rolling TTL on `save()` and
  empty-ZSET cleanup in the job
- Make `save()`, `removeFromQueue()`, and `deleteAllByMeetingId()` atomic using
  Redis pipelines or Lua scripts
- Replace the N+1 GET loop in both `findPending*` methods with `MGET`
- Replace `KEYS` in `JoinRequestCleanupJob` with a non-blocking `SCAN` cursor
- Make `updateStatus()` race-condition-free via an atomic Lua script
- Ensure `MeetingEndedJoinRequestHandleUseCase` marks requests `DENIED` before
  deleting them
- Add exponential-backoff reconnection (1s, 2s, 4s, max 3 attempts) to
  `JoinRequestSseClient` and `MeetingEventSseClient`
- Replace `indexOf` JSON parsing in `JoinRequestSseClient` with Gson
  deserialization
- Surface partial approval failures in `PendingJoinRequestApprover` rather than
  silently failing mid-loop
- Raise `SSE_TIMEOUT_MINUTES` to a safe upper bound (10 minutes) to outlast
  worst-case server TTL

**Non-Goals:**

- No API contract changes (request/response shapes, HTTP status codes, Kafka
  topic schemas)
- No PostgreSQL schema changes or Flyway migrations
- No changes to LiveKit token generation or SSE server-side emitter logic
- No retry logic on the server side for Kafka outbox publishing
- No changes to `MeetingEventSseClient` JSON parsing (its in-event fields are
  non-critical and can be addressed separately)

## Decisions

### D1: Atomicity via Lua scripts rather than Spring `SessionCallback` pipeline

**Decision**: Use Redis Lua scripting for `save()`, `removeFromQueue()`,
`deleteAllByMeetingId()`, and `updateStatus()`.

**Rationale**: `JoinRequestRedisRepositoryAdapter` uses two different
`RedisTemplate` instances (`StringRedisTemplate` for ZSET/device keys and
`RedisTemplate<String, JoinRequestData>` for meta keys). Spring's
`executePipelined(SessionCallback)` operates on a single connection but
serialization is tied to a single template's serializers. Mixing two templates
inside one pipeline session requires manual serialization. A Lua script executes
on a single Redis connection atomically with no serialization mismatch and no
need to restructure the template infrastructure.

**Alternative considered**: Merge both templates into a single
`RedisTemplate<String, Object>` with a custom serializer dispatcher. This would
allow straightforward pipeline use but requires changing the configuration and
all call sites of both templates — higher blast radius than Lua.

### D2: ZSET TTL = request TTL + 120-second buffer, reset on every `save()`

**Decision**: After every ZADD, set
`EXPIRE join_request:{meetingId} (ttl_seconds + 120)`. The cleanup job DELs the
ZSET after successfully processing all expired members if the set is then empty.

**Rationale**: The ZSET lifetime should exceed the longest individual member TTL
so it is not garbage-collected while live members exist. A 120-second buffer
covers clock skew and job scheduling jitter. The cleanup job provides a
second-pass DEL so that ZSETs that become empty between save events are not kept
indefinitely.

**Alternative considered**: Set ZSET TTL equal to the session-max TTL from
config. This requires passing application config into the adapter or reading it
from the environment, which breaks the adapter's current clean interface (TTL is
already passed per-call from the use case). The buffer approach stays
parameter-free.

### D3: `updateStatus()` Lua script uses `PTTL` to preserve sub-second TTL precision

**Decision**: The Lua script calls `PTTL` (millisecond precision) rather than
`TTL` and uses `PEXPIRE` to re-apply the remaining TTL after re-writing the
value.

**Rationale**: The existing Java code uses `getExpire(..., TimeUnit.SECONDS)`
which truncates sub-second remainders to zero, potentially causing `SET` without
an expiry on a key that had under 1 second remaining. Using millisecond
precision avoids silent TTL loss.

### D4: Android SSE retry uses `Handler.postDelayed` on main looper; `terminated` flag gates all retries

**Decision**: Both SSE clients track a `volatile boolean terminated` flag set to
`true` when a terminal event (approved/denied/expired) arrives or `cancel()` is
called. `onFailure` only schedules a retry if `!terminated` and
`retryCount < MAX_RETRIES` (3). Delays: attempt 1 → 1000 ms, attempt 2 → 2000
ms, attempt 3 → 4000 ms. On reconnect the same `subscribe()` call is reused with
the original parameters stored as fields.

**Rationale**: `Handler.postDelayed` is the idiomatic Android mechanism for
deferred work on the main thread and does not require additional thread
management. Storing the original `requestId`/`meetingId`/`authToken`/`listener`
as fields allows reconnect without caller involvement. The `terminated` flag
ensures that intentional cancellations and terminal event completions do not
trigger spurious reconnects.

**Alternative considered**: Use `ScheduledExecutorService` for retry scheduling.
This requires careful shutdown to avoid leaking the executor when the client is
cancelled. `Handler.postDelayed` ties lifecycle naturally to the Android main
looper and is consistent with the existing `mainHandler` already in both
clients.

### D5: Gson deserialization with private static inner classes in `JoinRequestSseClient`

**Decision**: Replace `extractToken()` and `extractReason()` with two private
static inner classes (`ApprovedEventData` with fields `token` and `roomName`,
`DeniedEventData` with field `reason`) deserialized with
`new Gson().fromJson(data, ...)`.

**Rationale**: Gson is already available as a transitive dependency through
Retrofit (confirmed via `build.gradle`). Inner classes avoid polluting the
package namespace. Static avoids an implicit outer-class reference.

**Alternative considered**: Add a `@JsonAdapter` or use the project's Retrofit
`GsonConverterFactory` instance. Both require handing the `Gson` instance
through dependency injection — overcomplicated for simple payload parsing inside
an already-injected class.

### D6: `PendingJoinRequestApprover` collects failures into a summary result rather than fail-fast

**Decision**: Replace early-return `Result.failure(error)` mid-loop with a
failure-list accumulation. Return `Result.success(approvedCount)` if all went
through, or a new
`MeetingError.PartialApprovalFailure(approvedCount, failedIds)` if any token
generation or approval fails.

**Rationale**: Failing fast leaves some requests approved and others pending
with no indication to the host or caller. Accumulating failures allows the
caller (and ultimately the host UI) to show which requests succeeded and which
need manual re-approval.

**Risk**: `MeetingError.PartialApprovalFailure` is a new error variant.
`BaseController` must handle it with a meaningful HTTP response (suggested: 207
Multi-Status or 200 with a partial-failure body). This is a small but
cross-cutting change that must be coordinated with the presentation layer.

## Risks / Trade-offs

- **Lua script maintenance burden**: Scripts are opaque strings embedded in
  Java. Mistyped Lua is only caught at runtime. Mitigation: write integration
  tests against Testcontainers Redis that exercise every Lua path.
- **Double-serialization in Lua `updateStatus()`**: The meta value JSON must be
  decoded in Lua (which has no built-in JSON; Redis provides `cjson`). The
  script uses `cjson.decode`/`cjson.encode`. If the JSON schema of
  `JoinRequestData` gains complex types (e.g., nested objects), the script must
  be updated accordingly. Mitigation: keep `JoinRequestData` a flat record (it
  currently is).
- **`MeetingError.PartialApprovalFailure` presentation mapping**: Adding a new
  error to the sealed interface requires a corresponding branch in
  `BaseController`. Forgetting this causes an `IllegalStateException` at
  runtime. Mitigation: covered by the task list and verified by integration
  test.
- **Handler leak on Android process death**: `Handler.postDelayed` callbacks
  hold a reference to the outer SSE client. If the Activity/Fragment is
  destroyed between retry attempts, the callback fires into a stale listener.
  Mitigation: callers must call `cancel()` in `onStop`/`onDestroy`. The existing
  `cancel()` sets `terminated = true`, which the callback checks before
  reconnecting.
- **`SCAN` cursor consistency**: `SCAN` can return duplicate keys or miss keys
  added during iteration. Duplicates in the cleanup job are idempotent
  (processing an already-expired request is a no-op). Missed keys are picked up
  on the next 60-second cycle. This is acceptable — the pre-existing `KEYS`
  approach also did not guarantee consistency under concurrent writes.

## Migration Plan

1. Deploy the backend service update. Zero downtime — the Lua scripts and MGET
   changes are backward-compatible with existing Redis data. Existing orphan
   ZSETs will be cleaned up by the first cleanup job run after deployment (the
   job now DELs empty ZSETs).
2. Ship the Android app update through normal Play Store review. Both SSE
   clients remain backward-compatible with the current server SSE endpoint.
3. No Flyway migration, no Kafka topic changes, no configuration key renames.
4. Rollback: redeploy the previous JAR. Old code tolerates the new ZSET TTLs (it
   never read ZSET TTL). Old Android APK tolerated missing reconnect (it just
   fails on connection drop as before).

## Open Questions

- Should `MeetingError.PartialApprovalFailure` result in HTTP 207 or HTTP 200
  with a custom body field? The current JSend convention maps all
  `Result.success` to 200. A 207 requires a new status handling path in
  `BaseController`. Decision needed before implementing the presentation layer
  change.
- Should `MeetingEventSseClient.extractField()` also be migrated to Gson in this
  change, or deferred? The plan currently leaves it as-is (Non-Goal), but it
  carries the same fragility risk as the guest-side parser.
