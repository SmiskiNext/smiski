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
service at `tenant:8081`.

#### Scenario: Request routed to tenant service

- **WHEN** client sends `GET /api/1/tenants/123` to Envoy at port 8080
- **THEN** Envoy forwards the request to `http://tenant:8081/api/1/tenants/123`

#### Scenario: Request with query parameters preserved

- **WHEN** client sends `GET /api/1/tenants?filter=active` to Envoy
- **THEN** Envoy forwards to tenant service with query parameters intact

### Requirement: Route requests to meeting service

Envoy proxy SHALL route requests with path prefix `/api/1/meetings` to the meet
service at `meet:8082`.

#### Scenario: Request routed to meeting service

- **WHEN** client sends `POST /api/1/meetings:instant` to Envoy at port 8080
- **THEN** Envoy forwards the request to
  `http://meet:8082/api/1/meetings:instant`

#### Scenario: Parameterized path routed correctly

- **WHEN** client sends `GET /api/1/meetings/abc-123` to Envoy
- **THEN** Envoy forwards to `http://meet:8082/api/1/meetings/abc-123`

### Requirement: Route requests to notification service

Envoy proxy SHALL route requests with path prefix `/api/1/notifications` to the
notification service at `notification:8083`.

#### Scenario: Request routed to notification service

- **WHEN** client sends `GET /api/1/notifications` to Envoy at port 8080
- **THEN** Envoy forwards the request to
  `http://notification:8083/api/1/notifications`

#### Scenario: Unmatched path returns 404

- **WHEN** client sends `GET /api/1/unknown` to Envoy
- **THEN** Envoy returns HTTP 404 Not Found

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

### Requirement: Cache GET responses

The gateway SHALL cache upstream responses for GET requests to reduce backend
load.

#### Scenario: GET response served from cache

- **WHEN** an identical cacheable GET request arrives within the cache lifetime
- **THEN** the gateway serves the cached response without contacting the backend

#### Scenario: Non-GET requests never cached

- **WHEN** the client sends POST, PUT or DELETE
- **THEN** the gateway always forwards the request to the backend
