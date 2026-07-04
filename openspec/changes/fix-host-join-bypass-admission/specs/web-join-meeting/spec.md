## MODIFIED Requirements

### Requirement: Pending join requests react to waiting-room server-sent events

For pending approvals, the web app SHALL subscribe to
`GET /api/v1/joinRequests/{requestId}/events`, interpret terminal event types,
and retry failed connections at most three times with exponential backoff. The
subscription SHALL be opened against the configured API gateway base URL so that
the SSE connection targets the same origin used for REST traffic from the
generated SDK.

#### Scenario: Approval event completes the join flow

- **WHEN** the event stream receives `join_request_approved` with
  `{token, roomName}`
- **THEN** the system SHALL stop the active event subscription
- **THEN** the system SHALL store or pass the approved credentials
- **THEN** the system SHALL navigate to `/workspace/meeting-room`

#### Scenario: Denial event returns the user to actionable feedback

- **WHEN** the event stream receives `join_request_denied` with a denial
  `reason`
- **THEN** the system SHALL stop the active event subscription
- **THEN** the flow SHALL leave the waiting state
- **THEN** the user SHALL see denial feedback mapped from the returned reason

#### Scenario: Expiration event invalidates the pending request

- **WHEN** the event stream receives `join_request_expired`
- **THEN** the system SHALL stop the active event subscription
- **THEN** the flow SHALL enter an expired state
- **THEN** the user SHALL be told that the request expired and must start a new
  join attempt

#### Scenario: Transient SSE failure retries with bounded exponential backoff

- **WHEN** the event stream disconnects before a terminal event is received and
  the retry count is below 3
- **THEN** the system SHALL attempt to reconnect after delays of 1 second, 2
  seconds, and 4 seconds for successive failures
- **THEN** the waiting UI SHALL remain active during retry attempts

#### Scenario: Repeated SSE failure surfaces terminal error

- **WHEN** the event stream fails again after the third retry attempt without
  receiving a terminal event
- **THEN** the system SHALL stop retrying
- **THEN** the flow SHALL enter an error state with retryable user feedback

#### Scenario: SSE subscription targets the configured API gateway

- **WHEN** the join flow opens an event subscription for a pending request and
  the application is configured with an API gateway base URL through
  `NEXT_PUBLIC_API_BASE_URL`
- **THEN** the constructed `EventSource` URL SHALL be the configured base URL
  concatenated with `/api/v1/joinRequests/{requestId}/events`
- **THEN** the constructed URL SHALL NOT be a relative path that the browser
  would resolve against the Next.js page origin
- **THEN** the same base URL SHALL be used as the one configured on the
  generated SDK client

## ADDED Requirements

### Requirement: API gateway base URL is exposed as a single source of truth

The web app SHALL expose a `getApiBaseUrl()` helper from the API client module
that returns the API gateway base URL applied to the generated SDK. Any
component that constructs requests outside the generated SDK (notably
`EventSource` instances for SSE) SHALL use this helper rather than reading
configuration sources directly.

#### Scenario: Helper returns the URL configured on the SDK client

- **WHEN** the API client is configured with a non-empty base URL during
  application bootstrap
- **THEN** `getApiBaseUrl()` SHALL return that exact base URL string
- **THEN** subsequent calls SHALL keep returning the same string until the
  client is reconfigured

#### Scenario: Waiting-room SSE uses the helper

- **WHEN** a hook needs to open an `EventSource` for join-request or
  meeting-scoped events
- **THEN** the hook SHALL build the URL by prefixing the gateway base from
  `getApiBaseUrl()`
- **THEN** the hook SHALL NOT inline a literal relative path or read
  `process.env.NEXT_PUBLIC_API_BASE_URL` at the call site
