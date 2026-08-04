# envoy-gateway-routing Specification

## Purpose

Defines how the Envoy proxy routes HTTP requests to backend services, validates
Forge Invocation Tokens (FIT), and delegates authorization decisions to an
external authorization service.

## Requirements

### Requirement: Listen on port 8080

Envoy proxy SHALL listen for HTTP requests on port 8080.

#### Scenario: Envoy accepts connections on port 8080

- **WHEN** client connects to `http://envoy:8080`
- **THEN** Envoy accepts the connection and processes the request

#### Scenario: Other ports unavailable

- **WHEN** client attempts to connect to `http://envoy:8081`
- **THEN** connection is refused

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

### Requirement: Preserve request headers and body

Envoy SHALL forward all request headers and body content to backend services
without modification, except for headers explicitly removed or added by filters.

#### Scenario: Original headers forwarded

- **WHEN** client sends request with custom headers `X-Custom: value`
- **THEN** backend service receives the same `X-Custom: value` header

#### Scenario: Request body preserved

- **WHEN** client sends POST request with JSON body
- **THEN** backend service receives the identical JSON body

### Requirement: Validate FIT signature using Atlassian JWKS

The gateway SHALL validate Forge Invocation Token (FIT) signature using RS256
algorithm with public keys fetched from the Atlassian JWKS endpoint at
`https://forge.cdn.prod.atlassian-dev.net/.well-known/jwks.json`, before any
routing or authorization decision occurs.

#### Scenario: Valid FIT signature accepted

- **WHEN** client sends request with FIT signed by valid Atlassian private key
- **THEN** system validates signature successfully and allows request to proceed

#### Scenario: Invalid FIT signature rejected

- **WHEN** client sends request with FIT signed by unknown key
- **THEN** system returns HTTP 401 Unauthorized with message "Invalid token
  signature"

#### Scenario: Tampered FIT rejected

- **WHEN** client sends request with FIT whose payload has been modified after
  signing
- **THEN** system returns HTTP 401 Unauthorized

### Requirement: Validate FIT issuer claim

The gateway SHALL verify that FIT contains `iss` claim with value
`forge/invocation-token`.

#### Scenario: Valid issuer accepted

- **WHEN** FIT contains `"iss": "forge/invocation-token"`
- **THEN** system accepts the token

#### Scenario: Invalid issuer rejected

- **WHEN** FIT contains `"iss": "https://malicious.com"`
- **THEN** system returns HTTP 401 Unauthorized with message "Invalid issuer"

#### Scenario: Missing issuer rejected

- **WHEN** FIT does not contain `iss` claim
- **THEN** system returns HTTP 401 Unauthorized

### Requirement: Validate FIT audience claim

The gateway SHALL verify that FIT contains `aud` claim matching the Forge app ID
in ARI format `ari:cloud:ecosystem::app/{appId}`.

#### Scenario: Valid audience accepted

- **WHEN** FIT contains `"aud": "ari:cloud:ecosystem::app/5e00d851-xxxx"`
- **THEN** system accepts the token

#### Scenario: Invalid audience rejected

- **WHEN** FIT contains `"aud": "ari:cloud:ecosystem::app/different-app-id"`
- **THEN** system returns HTTP 403 Forbidden

#### Scenario: Missing audience rejected

- **WHEN** FIT does not contain `aud` claim
- **THEN** system returns HTTP 401 Unauthorized

#### Scenario: Audience is configurable

- **WHEN** the Forge app ID changes across environments
- **THEN** the expected audience is supplied by configuration without code
  changes

### Requirement: Validate FIT expiration

The gateway SHALL verify that FIT `exp` claim is in the future (token not
expired).

#### Scenario: Non-expired token accepted

- **WHEN** FIT contains `"exp": <timestamp 10 minutes in future>`
- **THEN** system accepts the token

#### Scenario: Expired token rejected

- **WHEN** FIT contains `"exp": <timestamp 5 minutes in past>`
- **THEN** system returns HTTP 401 Unauthorized with message "Token expired"

#### Scenario: Missing expiration rejected

- **WHEN** FIT does not contain `exp` claim
- **THEN** system returns HTTP 401 Unauthorized

### Requirement: Cache JWKS for 1 hour

The gateway SHALL cache public keys fetched from the Atlassian JWKS endpoint for
1 hour to reduce external API calls.

#### Scenario: JWKS fetched on first validation

- **WHEN** system receives first FIT after startup
- **THEN** system fetches JWKS from
  `https://forge.cdn.prod.atlassian-dev.net/.well-known/jwks.json`

#### Scenario: Subsequent validations use cached JWKS

- **WHEN** system receives second FIT within 1 hour
- **THEN** system validates using cached JWKS without making external request

#### Scenario: Cache expires after 1 hour

- **WHEN** system receives FIT after 1 hour since last JWKS fetch
- **THEN** system fetches fresh JWKS from Atlassian endpoint

### Requirement: Handle JWKS fetch failures

The gateway SHALL reject FIT validation if the JWKS endpoint is unreachable and
no cached keys are available.

#### Scenario: JWKS endpoint timeout

- **WHEN** JWKS endpoint does not respond within 5 seconds
- **THEN** system returns HTTP 503 Service Unavailable with message "Unable to
  validate token"

#### Scenario: JWKS endpoint returns error

- **WHEN** JWKS endpoint returns HTTP 500
- **THEN** system returns HTTP 503 Service Unavailable

#### Scenario: Cached JWKS available during endpoint failure

- **WHEN** JWKS endpoint is unreachable but cached keys exist
- **THEN** system validates FIT using cached keys

### Requirement: Publish validated FIT claims to the authorization filter

The gateway SHALL make validated FIT claims available to the downstream
authorization filter as request metadata, so the authorization service does not
re-verify the signature.

#### Scenario: Claims published after successful validation

- **WHEN** FIT passes signature, issuer, audience and expiry validation
- **THEN** the decoded claim set is published as metadata under key
  `fit_payload`

#### Scenario: Invalid token never reaches authorization

- **WHEN** FIT fails validation
- **THEN** the request is rejected at the gateway and the authorization service
  is not invoked

### Requirement: Strip client-supplied trusted headers

The gateway SHALL remove any client-supplied header that the gateway itself is
responsible for producing, before those headers are derived from FIT claims.

#### Scenario: Spoofed internal claim headers removed

- **WHEN** client sends request containing `x-fit-cloud-id` or
  `x-fit-account-id`
- **THEN** the gateway removes those headers before deriving them from validated
  FIT claims

#### Scenario: Spoofed identity headers overwritten

- **WHEN** client sends `X-Tenant-ID`, `X-Account-Id` or `X-Project-Permissions`
- **THEN** the gateway overwrites each with the authoritative value it derives

### Requirement: Delegate authorization through gRPC ext_authz

The gateway SHALL delegate the authorization decision to an external
authorization service over gRPC using the Envoy `ext_authz` protocol, and SHALL
apply the returned decision before routing.

#### Scenario: Authorization consulted before routing

- **WHEN** a request passes FIT validation
- **THEN** the gateway calls the external authorization service and waits for
  the decision before selecting an upstream cluster

#### Scenario: Allowed request forwarded with injected headers

- **WHEN** the authorization service returns an allow decision with headers
- **THEN** the gateway injects those headers and forwards the request upstream

#### Scenario: Denied request not forwarded

- **WHEN** the authorization service returns a deny decision
- **THEN** the gateway returns the denial to the client and no backend service
  receives the request
