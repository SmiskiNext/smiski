# Tasks

## 1. Backend Redis Reliability — JoinRequestRedisRepositoryAdapter

- [x] 1.1 Write a Lua script for `save()` that atomically executes ZADD, EXPIRE
      on the ZSET key, SET meta with TTL, and SET device with TTL in a single
      Redis round-trip
- [x] 1.2 Replace the three sequential Redis commands in `save()` with an
      invocation of the Lua script via
      `redisTemplate.execute(RedisScript, keys, args)`
- [x] 1.3 Replace the GET+ZREM+DEL+DEL sequence in `removeFromQueue()` with an
      atomic Lua script that reads meta for deviceId, removes the ZSET member,
      deletes meta, and deletes the device key
- [x] 1.4 Replace the loop+DEL+DEL+DEL sequence in `deleteAllByMeetingId()` with
      an atomic Lua script that iterates all ZSET members, deletes each meta and
      device key, then deletes the ZSET key
- [x] 1.5 Replace the GET-modify-SET pattern in `updateStatus()` with a Lua
      script that uses `cjson.decode`/`cjson.encode` to read-modify-write
      atomically and preserves TTL with `PTTL`/`PEXPIRE` ← (verify:
      concurrent-call integration test confirms last-write-wins is eliminated;
      key with sub-1s TTL retains positive expiry after update)
- [x] 1.6 Replace the N+1 GET loop in `findPendingByMeetingId()` with a ZRANGE
      followed by a single `multiGet(metaKeys)` call; filter nulls and
      non-PENDING statuses from the bulk result
- [x] 1.7 Apply the same MGET pattern to `findPendingSummariesByMeetingId()` ←
      (verify: integration test with 100 pending requests confirms exactly 2
      Redis commands are issued: ZRANGE + MGET)

## 2. Backend Redis Reliability — JoinRequestCleanupJob

- [x] 2.1 Replace `redisTemplate.keys("join_request:*")` with
      `redisTemplate.scan(ScanOptions.scanOptions().match("join_request:*").count(100).build())`
      wrapped in try-with-resources
- [x] 2.2 After processing all expired members of a ZSET and before moving to
      the next key, check if the ZSET is now empty and, if so, `DEL` the ZSET
      key ← (verify: integration test confirms no orphan ZSET keys remain after
      cleanup run; no KEYS command appears in Redis slow log)

## 3. Backend Application Logic — MeetingEndedJoinRequestHandleUseCase

- [x] 3.1 In `handle(MeetingEndedEvent)`, add a loop before
      `deleteAllByMeetingId()` that calls
      `joinRequestRepository.updateStatus(joinRequest.getId().value(), JoinRequestStatus.DENIED)`
      for each pending request ← (verify: integration test confirms meta key
      shows DENIED status in the window between the updateStatus call and the
      deleteAllByMeetingId call)

## 4. Backend Application Logic — PendingJoinRequestApprover

- [x] 4.1 Add a new error record
      `PartialApprovalFailure(int approvedCount, List<UUID> failedIds)` to the
      `MeetingError` sealed interface
- [x] 4.2 Replace the early-return `Result.failure(error)` inside the
      preparation loop with failure accumulation into a `List<UUID> failedIds`
      collection
- [x] 4.3 After the preparation loop, if `failedIds` is non-empty return
      `Result.failure(new MeetingError.PartialApprovalFailure(approvedCount, failedIds))`;
      otherwise proceed to the commit loop as before
- [x] 4.4 Add a handler branch in `BaseController` (or the meeting controller's
      result-mapping switch) for `MeetingError.PartialApprovalFailure` that
      returns an appropriate HTTP response (coordinate HTTP status with the open
      question in design.md) ← (verify: integration test for approveAll with one
      token-generation failure returns partial failure response with correct
      approvedCount and failedIds)

## 5. Android SSE Resilience — JoinRequestSseClient

- [x] 5.1 Add fields `volatile boolean terminated`, `int retryCount`,
      `String savedRequestId`, `String savedAuthToken`, and
      `ApprovalEventListener savedListener` to `JoinRequestSseClient`
- [x] 5.2 In `subscribe()`, reset `terminated = false`, `retryCount = 0`, and
      store request parameters into the saved fields before opening the
      EventSource
- [x] 5.3 In `cancel()`, set `terminated = true` before cancelling the
      EventSource and nulling `currentListener`
- [x] 5.4 In `onEvent()`, set `terminated = true` immediately (before posting to
      main handler) upon receiving `join_request_approved`,
      `join_request_denied`, or `join_request_expired`
- [x] 5.5 In `onFailure()`, check `terminated` and `retryCount`; if both allow
      retry, increment `retryCount`, compute delay (1000 ms / 2000 ms / 4000
      ms), and call
      `mainHandler.postDelayed(() -> subscribe(savedRequestId, savedAuthToken, savedListener), delay)`
      ← (verify: manual test confirms guest re-connects automatically after
      simulated WiFi drop; no retry fires after cancel() is called)
- [x] 5.6 Add private static inner classes `ApprovedEventData` (fields
      `String token`, `String roomName`) and `DeniedEventData` (field
      `String reason`) inside `JoinRequestSseClient`
- [x] 5.7 Replace `extractToken()` with
      `new Gson().fromJson(data, ApprovedEventData.class)` and replace
      `extractReason()` with `new Gson().fromJson(data, DeniedEventData.class)`;
      handle null Gson result with fallback values ← (verify: unit test with JWT
      containing embedded quotes and colons confirms correct token extraction)
- [x] 5.8 Change `SSE_TIMEOUT_MINUTES` from 5 to 10 in `JoinRequestSseClient`

## 6. Android SSE Resilience — MeetingEventSseClient

- [x] 6.1 Add fields `volatile boolean cancelled`, `int retryCount`,
      `String savedMeetingId`, `String savedAuthToken`, and
      `MeetingEventListener savedListener` to `MeetingEventSseClient`
- [x] 6.2 In `subscribe()`, reset `cancelled = false`, `retryCount = 0`, and
      store parameters before opening the EventSource
- [x] 6.3 In `cancel()`, set `cancelled = true` before cancelling the
      EventSource and nulling `currentListener`
- [x] 6.4 In `onFailure()`, check `cancelled` and `retryCount`; if both allow
      retry, increment `retryCount`, compute delay, and schedule
      `mainHandler.postDelayed(() -> subscribe(savedMeetingId, savedAuthToken, savedListener), delay)`
      ← (verify: manual test confirms host waiting room refreshes automatically
      after reconnect; onConnected is called on the listener after successful
      reconnect)
- [x] 6.5 Change `SSE_TIMEOUT_MINUTES` from 5 to 10 in `MeetingEventSseClient`

## 7. Tests

- [x] 7.1 Write a Testcontainers Redis integration test for `save()` verifying
      ZSET TTL is set and all three keys exist atomically
- [x] 7.2 Write a Testcontainers Redis integration test for `updateStatus()`
      verifying atomicity under concurrent invocations and TTL preservation near
      expiry
- [x] 7.3 Write a Testcontainers Redis integration test for
      `findPendingByMeetingId()` with 100 entries verifying exactly 2 commands
      (ZRANGE + MGET) via Redis command tracking
- [x] 7.4 Write a Testcontainers Redis integration test for
      `cleanupExpiredRequests()` verifying no orphan ZSETs remain and no KEYS
      command is issued
- [x] 7.5 Write a unit test for `JoinRequestSseClient` covering: Gson parsing
      with special characters in token, null/empty payload fallback, retry
      scheduling on failure, and no-retry after terminal event ← (verify: all
      unit tests pass; edge cases for escaped JSON characters are covered)
- [x] 7.6 Write a `@SpringBootTest` integration test for
      `PendingJoinRequestApprover.approveAll()` with a stubbed LiveKit port
      returning failure for one request, verifying `PartialApprovalFailure` is
      returned with correct counts
