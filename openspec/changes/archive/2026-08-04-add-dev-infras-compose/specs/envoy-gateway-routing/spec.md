## MODIFIED Requirements

### Requirement: Route requests to tenant service

Envoy proxy SHALL route requests with path prefix `/api/1/tenants` to the tenant
service at `tenant:8080`.

#### Scenario: Request routed to tenant service

- **WHEN** client sends `GET /api/1/tenants/123` to Envoy at port 8080
- **THEN** Envoy forwards the request to `http://tenant:8080/api/1/tenants/123`

#### Scenario: Request with query parameters preserved

- **WHEN** client sends `GET /api/1/tenants?filter=active` to Envoy
- **THEN** Envoy forwards to tenant service with query parameters intact

### Requirement: Route requests to meeting service

Envoy proxy SHALL route requests with path prefix `/api/1/meetings` to the meet
service at `meet:8080`, except for the meeting event-stream paths owned by the
notification service, which SHALL be matched by a more specific route evaluated
first.

#### Scenario: Request routed to meeting service

- **WHEN** client sends `POST /api/1/meetings:instant` to Envoy at port 8080
- **THEN** Envoy forwards the request to
  `http://meet:8080/api/1/meetings:instant`

#### Scenario: Parameterized path routed correctly

- **WHEN** client sends `GET /api/1/meetings/abc-123` to Envoy
- **THEN** Envoy forwards to `http://meet:8080/api/1/meetings/abc-123`

#### Scenario: Meeting sub-resource routed to meet service

- **WHEN** client sends `POST /api/1/meetings/abc-123/join-requests:accept`
- **THEN** Envoy forwards to the meet service, because the path is not an
  event-stream path

#### Scenario: Event-stream path not captured by the meeting prefix

- **WHEN** client sends `GET /api/1/meetings/abc-123/events`
- **THEN** the more specific event-stream route matches first and the request is
  not forwarded to the meet service

## ADDED Requirements

### Requirement: Route requests to the issue-scoped meeting collection

Envoy proxy SHALL route requests with path prefix `/api/1/issues` to the meet
service at `meet:8080`, with FIT validation and external authorization applied.

#### Scenario: Issue-scoped meeting listing routed to meet service

- **WHEN** client sends `POST /api/1/issues/ABC-1/meetings` to Envoy with a
  valid FIT
- **THEN** Envoy forwards the request to the meet service and the response is
  not 404

#### Scenario: Project permissions supplied to the backend

- **WHEN** the request passes FIT validation and authorization
- **THEN** the gateway injects the caller's project permissions so the
  endpoint's permission check can be evaluated

#### Scenario: Request without a FIT rejected

- **WHEN** client sends `POST /api/1/issues/ABC-1/meetings` without an
  `Authorization` header
- **THEN** the gateway rejects the request and the meet service is not contacted

### Requirement: Route meeting event streams to the notification service

Envoy proxy SHALL route the meeting event-stream paths
`/api/1/meetings/{id}/events` and
`/api/1/meetings/{id}/join-requests/{requestId}/events` to the notification
service at `notification:8080`. The matching rule SHALL be anchored so that it
matches only these two path shapes, and SHALL be evaluated before the
`/api/1/meetings` prefix route.

#### Scenario: Host event stream routed to notification service

- **WHEN** client sends `GET /api/1/meetings/abc-123/events`
- **THEN** Envoy forwards the request to the notification service

#### Scenario: Join-request decision stream routed to notification service

- **WHEN** client sends
  `GET /api/1/meetings/abc-123/join-requests/def-456/events`
- **THEN** Envoy forwards the request to the notification service

#### Scenario: Sibling meeting path not captured by the stream route

- **WHEN** client sends `GET /api/1/meetings/abc-123/join-requests`
- **THEN** the stream route does not match and the request is routed to the meet
  service

#### Scenario: Deeper path not captured by the stream route

- **WHEN** client sends a path that extends beyond an event-stream path, such as
  `/api/1/meetings/abc-123/events/extra`
- **THEN** the stream route does not match, because the rule is anchored at the
  end of the path

### Requirement: Event streams are not buffered or prematurely terminated

The gateway SHALL deliver `text/event-stream` responses incrementally and SHALL
NOT terminate an event-stream connection before the upstream service closes it.

#### Scenario: Stream survives longer than the default route timeout

- **WHEN** an event-stream connection remains open beyond the gateway's default
  per-route response timeout
- **THEN** the gateway keeps the connection open and continues forwarding frames

#### Scenario: Frames delivered as they are produced

- **WHEN** the upstream service emits an event frame or heartbeat
- **THEN** the client receives it without waiting for the response to complete

#### Scenario: Idle stream not closed before the application closes it

- **WHEN** an event stream emits only periodic heartbeats for its configured
  lifetime
- **THEN** the gateway does not close the connection on idleness; the upstream
  service terminates it at its configured timeout

### Requirement: Route inbound webhooks to their owning services

Envoy proxy SHALL route `/api/1/webhooks/livekit` to the meet service and
`/api/1/webhooks/resend/inbound` to the notification service, matching each on
its exact path.

#### Scenario: Media-server webhook routed to meet service

- **WHEN** the media server posts to `/api/1/webhooks/livekit`
- **THEN** Envoy forwards the request to the meet service

#### Scenario: Inbound email webhook routed to notification service

- **WHEN** the email provider posts to `/api/1/webhooks/resend/inbound`
- **THEN** Envoy forwards the request to the notification service

#### Scenario: Exact matching prevents surface widening

- **WHEN** a client sends a request to a path that merely starts with a webhook
  path, such as `/api/1/webhooks/livekit/extra`
- **THEN** the webhook route does not match, so the unauthenticated surface is
  limited to the exact webhook paths

#### Scenario: Invalid webhook signature rejected by the service

- **WHEN** a request reaches a webhook endpoint with a missing or invalid
  signature
- **THEN** the owning service rejects it, because each webhook endpoint verifies
  its own signature independently of the gateway

### Requirement: Bypassing gateway authentication disables both filters

The gateway SHALL disable both FIT validation and external authorization on any
route configured to bypass gateway authentication.

#### Scenario: Both filters disabled on a bypassed route

- **WHEN** a route is configured to accept requests without a FIT
- **THEN** both the FIT validation filter and the external authorization filter
  are disabled for that route

#### Scenario: Disabling only FIT validation still rejects the request

- **WHEN** a route disables FIT validation but leaves external authorization
  active
- **THEN** the authorization service rejects the request for lacking an
  `Authorization` header, so the route does not function

#### Scenario: Bypass does not affect authenticated routes

- **WHEN** a route bypasses authentication
- **THEN** all other routes continue to require FIT validation and authorization

### Requirement: Routes that bypass authentication are limited and justified

The gateway SHALL bypass authentication only for routes whose callers cannot
present a FIT. Each such route SHALL either carry its own independent
authentication mechanism, or SHALL be recorded as accepted risk with its
exposure and closure options stated.

#### Scenario: Webhook routes carry independent authentication

- **WHEN** a webhook route bypasses gateway authentication
- **THEN** the owning service verifies the caller's request signature, so no
  protection is lost

#### Scenario: Event-stream routes are recorded as accepted risk

- **WHEN** the event-stream routes bypass gateway authentication because the
  frontend transport cannot attach a FIT to a streaming request
- **THEN** the exposure is documented, including that a join-request stream
  delivers a media room token, and the deployment scope is restricted to local
  development

#### Scenario: No other route bypasses authentication

- **WHEN** the route table is reviewed
- **THEN** only the webhook routes and the event-stream routes bypass
  authentication

## REMOVED Requirements

### Requirement: Route requests to notification service

**Reason**: The route matched path prefix `/api/1/notifications`, but no
controller in any backend service declares that path, so the route resolved to
nothing. The notification service's actual endpoints are the two meeting
event-stream paths and the inbound email webhook, each of which now has its own
route with the correct target and authentication level.

**Migration**: Requests intended for notification-owned functionality use the
event-stream routes or the inbound email webhook route. The scenario asserting
that an unmatched path returns 404 is preserved by the routing behaviour of the
remaining route table: any path matching no route is rejected without reaching a
backend.

### Requirement: Cache GET responses

**Reason**: The gateway has no response-caching filter configured, so it never
caches upstream responses. The only cache the gateway configuration declares is
the JWKS key cache used for FIT signature validation, which is covered by the
"Cache JWKS for 1 hour" requirement. Response caching would also be incorrect
for this system: `text/event-stream` responses must never be cached, and
permission data is cached in the authorization service's own store rather than
at the proxy.

**Migration**: Caching of authorization decisions is provided by the
authorization service, which caches Jira permission results with its own
lifetime and stale-retention settings. No proxy-level response cache replaces
this requirement.
