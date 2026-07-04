# ADDED Requirements

## Requirement: Exponential-backoff SSE reconnection for JoinRequestSseClient

`JoinRequestSseClient` SHALL automatically retry a failed SSE connection up to 3
times using exponential back-off delays of 1 s, 2 s, and 4 s. Retries SHALL be
suppressed when the client is in a terminated state (terminal event received or
`cancel()` called).

### Scenario: Reconnect after transient network failure

- **WHEN** `onFailure` is called due to a transient network error and
  `terminated` is false and `retryCount < 3`
- **THEN** the client SHALL schedule a reconnect attempt via
  `Handler.postDelayed` after the appropriate back-off delay (1 s on attempt 1,
  2 s on attempt 2, 4 s on attempt 3)

### Scenario: No reconnect after terminal event

- **WHEN** a `join_request_approved`, `join_request_denied`, or
  `join_request_expired` event is received (setting `terminated = true`) and
  `onFailure` is subsequently called
- **THEN** NO reconnect SHALL be attempted

### Scenario: No reconnect after explicit cancel

- **WHEN** `cancel()` is called and `onFailure` fires on the cancelled
  EventSource
- **THEN** NO reconnect SHALL be attempted

### Scenario: No reconnect after max retries exhausted

- **WHEN** `onFailure` fires for the fourth time (retryCount == 3) with no
  successful reconnect
- **THEN** the client SHALL call `listener.onError()` and stop retrying

### Scenario: Retry counter resets on fresh subscribe

- **WHEN** `subscribe()` is called after a previous session exhausted all
  retries
- **THEN** `retryCount` SHALL be reset to 0 and reconnection SHALL be attempted
  on the next failure

---

## Requirement: Exponential-backoff SSE reconnection for MeetingEventSseClient

`MeetingEventSseClient` SHALL automatically retry a failed SSE connection up to
3 times using exponential back-off delays of 1 s, 2 s, and 4 s. Retries SHALL be
suppressed when `cancel()` has been called.

### Scenario: Reconnect after transient network failure (host side)

- **WHEN** `onFailure` is called due to a transient network error and the client
  has not been cancelled and `retryCount < 3`
- **THEN** the client SHALL schedule a reconnect attempt with the appropriate
  back-off delay

### Scenario: onConnected triggered after successful reconnect

- **WHEN** a reconnect attempt succeeds (server returns 200 and SSE stream
  opens)
- **THEN** `listener.onConnected()` SHALL be called, allowing the host UI to
  refresh the waiting room state

### Scenario: No reconnect after explicit cancel (host side)

- **WHEN** `cancel()` is called and `onFailure` fires
- **THEN** NO reconnect SHALL be attempted

### Scenario: No reconnect after max retries exhausted (host side)

- **WHEN** `onFailure` fires for the fourth time with no successful reconnect
- **THEN** the client SHALL call `listener.onError()` and stop retrying

---

## Requirement: Gson-based event payload parsing in JoinRequestSseClient

`JoinRequestSseClient` SHALL parse `join_request_approved` and
`join_request_denied` event payloads using Gson deserialization into typed inner
classes rather than `indexOf`-based string manipulation.

### Scenario: Approved event payload parsed correctly

- **WHEN** a `join_request_approved` event arrives with JSON
  `{"token":"<livekit-jwt>","roomName":"<room>"}`
- **THEN** the `token` field SHALL be extracted correctly and passed to
  `listener.onApproved(token)`

### Scenario: Approved event with escaped special characters

- **WHEN** the `token` field value contains characters that would break naive
  `indexOf` parsing (e.g., `\"`, `:`, `}` inside the JWT)
- **THEN** Gson deserialization SHALL still extract the correct token value

### Scenario: Denied event payload parsed correctly

- **WHEN** a `join_request_denied` event arrives with JSON
  `{"reason":"Meeting has ended"}`
- **THEN** the `reason` field SHALL be extracted correctly and passed to
  `listener.onDenied(reason)`

### Scenario: Null or malformed payload handled gracefully

- **WHEN** the event data is null, empty, or not valid JSON
- **THEN** `listener.onApproved("")` or `listener.onDenied("Request denied")`
  SHALL be called with the safe fallback value; no exception SHALL propagate to
  the caller

---

## Requirement: SSE read timeout raised to safe upper bound

Both `JoinRequestSseClient` and `MeetingEventSseClient` SHALL set their OkHttp
read timeout to 10 minutes (`SSE_TIMEOUT_MINUTES = 10`) to ensure the connection
outlasts the worst-case server-side join request TTL.

### Scenario: Client does not time out before server TTL expires

- **WHEN** the server-side join request TTL is configured at its default (5
  minutes) and no events arrive
- **THEN** the OkHttp read timeout SHALL NOT fire before the server closes the
  stream, because the 10-minute client timeout exceeds the 5-minute server TTL

### Scenario: Client times out if server stream is completely silent beyond 10 minutes

- **WHEN** no SSE data is received for more than 10 minutes (abnormal server
  condition)
- **THEN** OkHttp SHALL invoke `onFailure`, triggering the reconnect logic
