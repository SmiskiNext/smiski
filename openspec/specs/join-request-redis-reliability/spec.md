# ADDED Requirements

## Requirement: ZSET key TTL propagation on save

The Redis ZSET key `join_request:{meetingId}` SHALL receive an `EXPIRE` command
on every `save()` call, set to the request TTL plus a 120-second buffer. When
the cleanup job processes the last expired entry for a meeting and the resulting
ZSET is empty, it SHALL `DEL` the ZSET key.

### Scenario: ZSET TTL is set when a request is saved

- **WHEN** `save(request, ttl)` is called for a join request
- **THEN** the ZSET key `join_request:{meetingId}` SHALL have a TTL equal to
  `ttl + 120 seconds`

### Scenario: ZSET TTL is refreshed on subsequent saves for same meeting

- **WHEN** a second `save()` is called for a different request in the same
  meeting within the TTL window
- **THEN** the ZSET TTL SHALL be reset to `ttl + 120 seconds` from the time of
  the second save

### Scenario: Cleanup job DELs empty ZSET after processing

- **WHEN** the cleanup job removes the last expired member from a ZSET and the
  ZSET becomes empty
- **THEN** the cleanup job SHALL `DEL` the `join_request:{meetingId}` key so no
  orphan ZSET remains

---

## Requirement: Atomic multi-key Redis writes

`save()`, `removeFromQueue()`, and `deleteAllByMeetingId()` in
`JoinRequestRedisRepositoryAdapter` SHALL each execute all their Redis commands
atomically via a Lua script so that a crash between commands never leaves data
in a partially-written state.

### Scenario: Atomic save — all three keys written or none

- **WHEN** `save()` is called and a simulated crash occurs between the ZADD and
  the meta SET
- **THEN** Redis SHALL contain either all three keys (ZSET member, meta, device)
  or none of them

### Scenario: Atomic removeFromQueue — ZSET member, meta, and device key removed together

- **WHEN** `removeFromQueue(meetingId, requestId)` is called
- **THEN** the ZSET member, meta key, and device key SHALL all be absent from
  Redis after the call, regardless of concurrent writes to other keys

### Scenario: Atomic deleteAllByMeetingId — full meeting cleanup

- **WHEN** `deleteAllByMeetingId(meetingId)` is called
- **THEN** all meta keys, all device keys, and the ZSET key for that meeting
  SHALL be deleted in a single atomic operation

---

## Requirement: Bulk MGET for pending request reads

`findPendingByMeetingId()` and `findPendingSummariesByMeetingId()` SHALL
retrieve all meta keys in a single `MGET` call rather than issuing one `GET` per
request ID.

### Scenario: N+1 queries eliminated for pending list

- **WHEN** `findPendingByMeetingId(meetingId)` is called with 100 pending
  requests in the ZSET
- **THEN** exactly two Redis commands SHALL be issued: one `ZRANGE` and one
  `MGET`

### Scenario: Null entries from MGET are filtered

- **WHEN** some meta keys returned from ZRANGE have already expired and `MGET`
  returns null for them
- **THEN** those entries SHALL be silently excluded from the returned list

---

## Requirement: SCAN-based queue key discovery in cleanup job

`JoinRequestCleanupJob` SHALL use `SCAN` with match pattern `join_request:*` and
a batch count hint of 100 instead of `KEYS`, and SHALL use try-with-resources on
the returned cursor to guarantee resource cleanup.

### Scenario: SCAN replaces KEYS for queue discovery

- **WHEN** `cleanupExpiredRequests()` runs
- **THEN** no `KEYS` command SHALL be issued to Redis; instead one or more
  `SCAN` iterations SHALL be issued

### Scenario: Cursor is always closed

- **WHEN** `cleanupExpiredRequests()` throws an unexpected exception during
  iteration
- **THEN** the cursor resource SHALL still be closed without resource leakage

---

## Requirement: Atomic updateStatus via Lua script

`updateStatus(requestId, status)` SHALL use a Lua script that atomically reads
the current meta value, updates the status field, and re-writes the value with
the remaining TTL in a single Redis round-trip.

### Scenario: Status update is atomic under concurrent modification

- **WHEN** two threads each call `updateStatus()` on the same request ID
  simultaneously
- **THEN** the final stored status SHALL be the result of exactly one of the two
  writes with no partial or corrupted intermediate state

### Scenario: updateStatus is a no-op for non-existent keys

- **WHEN** `updateStatus(requestId, EXPIRED)` is called for a requestId whose
  meta key does not exist
- **THEN** the Lua script SHALL return without writing anything and no exception
  SHALL be thrown

### Scenario: Remaining TTL is preserved with millisecond precision

- **WHEN** `updateStatus()` is called on a key with 500 ms of TTL remaining
- **THEN** the re-written key SHALL retain a positive TTL (not be persisted with
  no expiry)

---

## Requirement: Status set to DENIED before bulk delete on meeting end

`MeetingEndedJoinRequestHandleUseCase` SHALL call
`joinRequestRepository.updateStatus(requestId, DENIED)` for every pending
request before calling `deleteAllByMeetingId()`.

### Scenario: Meta key reflects DENIED before it is deleted

- **WHEN** a `MeetingEndedEvent` is handled and there are 3 pending requests
- **THEN** `updateStatus(requestId, DENIED)` SHALL be called for all 3 requests
  before `deleteAllByMeetingId()` is called, so any concurrent SSE read of meta
  sees DENIED rather than PENDING

---

## Requirement: Partial approval failure reporting

`PendingJoinRequestApprover.approveAll()` SHALL collect per-request failures
into a `MeetingError.PartialApprovalFailure` result rather than failing fast, so
callers can report partial success to the host.

### Scenario: All requests approved successfully

- **WHEN** all pending requests are approved without error
- **THEN** `approveAll()` SHALL return `Result.success(count)` where count
  equals the number of approved requests

### Scenario: Some requests fail token generation

- **WHEN** token generation fails for one request and succeeds for the others
- **THEN** `approveAll()` SHALL return
  `Result.failure(MeetingError.PartialApprovalFailure)` containing the approved
  count and the IDs of the failed requests

### Scenario: All requests fail

- **WHEN** token generation fails for every request
- **THEN** `approveAll()` SHALL return
  `Result.failure(MeetingError.PartialApprovalFailure)` with approved count 0
  and the full list of failed request IDs
