# permission-checking Specification

## Purpose

Defines the external authorization service that validates Forge Invocation
Tokens (FIT), extracts tenant and user identity, checks Jira project permissions
via the Jira API, and returns authorization decisions with injected identity
headers to the gateway.

## Requirements

### Requirement: Implement envoy.service.auth.v3.Authorization interface

The authorization service SHALL implement the
`envoy.service.auth.v3.Authorization` gRPC interface with a `Check` method,
listening on port 9001.

#### Scenario: Check method accepts CheckRequest

- **WHEN** the gateway calls `Check` with a `CheckRequest` protobuf message
- **THEN** the authorization service receives the request and processes it

#### Scenario: Check method returns CheckResponse

- **WHEN** authorization logic completes
- **THEN** the authorization service returns a `CheckResponse` protobuf message

#### Scenario: gRPC server accepts connections on port 9001

- **WHEN** the gateway connects to `gateway:9001`
- **THEN** the authorization service accepts the gRPC connection

#### Scenario: HTTP/2 protocol required

- **WHEN** a client attempts an HTTP/1.1 connection
- **THEN** the connection is rejected because gRPC requires HTTP/2

#### Scenario: Invalid protobuf message rejected

- **WHEN** a malformed CheckRequest is sent
- **THEN** the gRPC framework returns an error before reaching the Check method

### Requirement: Extract request attributes from CheckRequest

The authorization service SHALL read the FIT, authorization context and system
token from `CheckRequest.Attributes.Request.Http.Headers`.

#### Scenario: Authorization header extracted

- **WHEN** CheckRequest contains `headers["authorization"] = "Bearer <FIT>"`
- **THEN** the service extracts the FIT for claim parsing

#### Scenario: Context headers extracted

- **WHEN** CheckRequest contains `headers["x-issue-id"] = "10001"`
- **THEN** the service extracts the issueId for the permission check

#### Scenario: Project context header extracted

- **WHEN** CheckRequest contains `headers["x-project-id"] = "10001"`
- **THEN** the service extracts the projectId for the permission check

#### Scenario: System token extracted

- **WHEN** CheckRequest contains `headers["x-forge-oauth-system"] = "<token>"`
- **THEN** the service extracts the system token for the Jira API call

#### Scenario: Missing header handled gracefully

- **WHEN** CheckRequest does not contain an expected header
- **THEN** the service treats it as a missing value without panicking

#### Scenario: Non-numeric context identifier rejected

- **WHEN** CheckRequest contains `headers["x-issue-id"] = "SMISKI-101"` or
  `headers["x-project-id"] = "SMISKI"`
- **THEN** the service does NOT send that value to the Jira API, logs the
  rejected value, and treats the permission check as failed rather than silently
  omitting the context

### Requirement: Extract cloudId from FIT claims

The authorization service SHALL extract the cloudId (tenant identifier) from the
FIT, preferring the `context.cloudId` claim and falling back to parsing the
trailing path segment of `app.apiBaseUrl`.

#### Scenario: CloudId read from context.cloudId

- **WHEN** FIT contains `"context": {"cloudId": "abc123-def456"}`
- **THEN** system extracts cloudId as `abc123-def456`

#### Scenario: context.cloudId preferred over app.apiBaseUrl

- **WHEN** FIT contains both `"context": {"cloudId": "context-id"}` and
  `"app": {"apiBaseUrl": "https://api.atlassian.com/ex/jira/url-id"}`
- **THEN** system extracts cloudId as `context-id`

#### Scenario: CloudId extracted from Jira app.apiBaseUrl fallback

- **WHEN** FIT omits `context.cloudId` and contains
  `"app": {"apiBaseUrl": "https://api.atlassian.com/ex/jira/abc123-def456"}`
- **THEN** system extracts cloudId as `abc123-def456`

#### Scenario: CloudId extracted from Confluence app.apiBaseUrl fallback

- **WHEN** FIT omits `context.cloudId` and contains
  `"app": {"apiBaseUrl": "https://api.atlassian.com/ex/confluence/xyz789"}`
- **THEN** system extracts cloudId as `xyz789`

#### Scenario: Missing both cloudId sources rejected

- **WHEN** FIT contains neither `context.cloudId` nor `app.apiBaseUrl`
- **THEN** system rejects the request with a missing-claims error

### Requirement: Extract accountId from FIT principal claim

The authorization service SHALL extract the accountId (user identifier) from the
FIT `principal` claim, normalising the Forge colon-prefixed and ARI forms to a
bare account identifier.

#### Scenario: AccountId extracted from Forge colon-prefixed principal

- **WHEN** FIT contains
  `"principal": "655362:312d3308-8954-42b0-aa38-771a10c88656"`
- **THEN** system extracts accountId as `312d3308-8954-42b0-aa38-771a10c88656`

#### Scenario: AccountId extracted from ARI principal

- **WHEN** FIT contains
  `"principal": "ari:cloud:identity::user/5f8c9d1234567890abcdef01"`
- **THEN** system extracts accountId as `5f8c9d1234567890abcdef01`

#### Scenario: AccountId extracted from bare principal

- **WHEN** FIT contains `"principal": "5f8c9d1234567890abcdef01"`
- **THEN** system extracts accountId as `5f8c9d1234567890abcdef01`

#### Scenario: Missing principal rejected

- **WHEN** FIT does not contain `principal` claim
- **THEN** system returns HTTP 400 Bad Request with message "Missing principal
  claim"

#### Scenario: Empty principal rejected

- **WHEN** FIT contains `"principal": ""`
- **THEN** system returns HTTP 400 Bad Request

### Requirement: Parse FIT payload without signature verification

The authorization service SHALL parse the FIT JWT payload to extract claims
without performing signature verification, because the signature is already
validated by the gateway.

#### Scenario: FIT payload parsed from validated token

- **WHEN** FIT has been validated by the gateway JWT filter
- **THEN** the authorization service decodes the JWT payload and extracts claims

#### Scenario: Base64 decoding failure handled

- **WHEN** FIT payload is not valid base64
- **THEN** system returns HTTP 400 Bad Request with message "Invalid token
  format"

#### Scenario: Multiple missing claims reported

- **WHEN** FIT is missing `principal` and both cloudId sources
  (`context.cloudId`, `app.apiBaseUrl`)
- **THEN** system returns HTTP 400 Bad Request with a message listing all
  missing claims

#### Scenario: Invalid claim type reported

- **WHEN** FIT contains `"principal": 123` (number instead of string)
- **THEN** system returns HTTP 400 Bad Request with message "Invalid principal
  claim type"

### Requirement: Call Jira permissions/check API with system token

The authorization service SHALL call `POST /rest/api/3/permissions/check` using
the app system token from the `x-forge-oauth-system` header to verify user
permissions. The request body SHALL contain only the members defined by the Jira
`BulkPermissionsRequestBean` schema — `accountId`, `globalPermissions` and
`projectPermissions` — because the schema forbids additional properties. The
permission keys and the issue or project context SHALL be nested inside a
`projectPermissions` entry. Issue and project identifiers SHALL be sent as
numbers, not strings.

#### Scenario: Permission check with issueId context

- **WHEN** user requests a resource and `X-Issue-Id: 10001` header is present
- **THEN** system calls Jira API with body
  `{"accountId": "<accountId>", "projectPermissions": [{"permissions": ["<viewMeetingAri>","<editMeetingAri>"], "issues": [10001]}]}`

#### Scenario: Permission check with projectId context

- **WHEN** user requests a resource and `X-Project-Id: 10001` header is present
- **THEN** system calls Jira API with body
  `{"accountId": "<accountId>", "projectPermissions": [{"permissions": ["<viewMeetingAri>","<editMeetingAri>"], "projects": [10001]}]}`

#### Scenario: Issue context preferred when both are present

- **WHEN** the request carries both `X-Issue-Id` and `X-Project-Id`
- **THEN** system sends the `issues` context and omits `projects`

#### Scenario: Missing system token rejected

- **WHEN** request does not contain the `x-forge-oauth-system` header
- **THEN** system returns HTTP 500 Internal Server Error with message "Missing
  system token"

#### Scenario: No context headers provided

- **WHEN** request contains neither `X-Issue-Id` nor `X-Project-Id`
- **THEN** system skips the permission check and returns an empty permissions
  array

### Requirement: Use correct Jira API base URL for tenant

The authorization service SHALL construct the Jira API URL using the cloudId
extracted from FIT.

#### Scenario: API URL constructed with cloudId

- **WHEN** cloudId is `abc123-def456`
- **THEN** system calls
  `https://api.atlassian.com/ex/jira/abc123-def456/rest/api/3/permissions/check`

#### Scenario: Configurable API base URL

- **WHEN** environment variable `JIRA_API_BASE` is set to a custom value
- **THEN** system uses the custom base URL instead of default
  `https://api.atlassian.com`

### Requirement: Verify custom project permission keys

The authorization service SHALL check the custom Jira project permissions
`view-meeting` and `edit-meeting` declared in the Forge app manifest. Because
Jira does not accept the bare manifest key, the service SHALL send each key as a
fully-qualified permission identifier of the form
`ari:cloud:ecosystem::extension/{appId}/{environmentId}/static/{key}`, derived
from the FIT `app.id` and `app.environment.id` claims.

#### Scenario: Custom permissions checked

- **WHEN** calling the Jira permissions/check API
- **THEN** system requests the qualified identifiers for `view-meeting` and
  `edit-meeting`

#### Scenario: Only defined permissions checked

- **WHEN** calling the Jira API
- **THEN** system does NOT request built-in Jira permissions like
  `BROWSE_PROJECTS` or `EDIT_ISSUES`

#### Scenario: Qualified identifier built from FIT claims

- **WHEN** FIT yields app id `ari:cloud:ecosystem::app/app-uuid` and environment
  id `ari:cloud:ecosystem::environment/env-uuid`
- **THEN** the `view-meeting` identifier sent to Jira is
  `ari:cloud:ecosystem::extension/app-uuid/env-uuid/static/view-meeting`

#### Scenario: Environment identifier read from the trailing segment

- **WHEN** the FIT environment claim is
  `ari:cloud:ecosystem::environment/app-uuid/env-uuid`
- **THEN** system uses `env-uuid` as the environment identifier

#### Scenario: Missing app or environment claim rejected

- **WHEN** FIT omits `app.id` or `app.environment.id`
- **THEN** system does NOT call the Jira API with an incomplete identifier and
  treats the permission check as failed with a missing-claims error

#### Scenario: Unrecognised permission reported distinctly

- **WHEN** Jira returns HTTP 400 naming an unrecognised permission
- **THEN** system logs the rejected identifier distinctly from an ordinary
  permission denial and treats the check as an API failure

### Requirement: Parse Jira permissions/check response

The authorization service SHALL parse the Jira API response according to the
`BulkPermissionGrants` schema, in which `globalPermissions` is an array of
permission identifier strings and `projectPermissions` is an array of entries
each carrying a singular `permission` identifier together with the `issues` and
`projects` it grants access to. The response contains only permissions the user
holds, so the presence of an identifier SHALL be interpreted as a grant. The
service SHALL map each granted identifier back to its bare manifest key before
publishing it downstream, and SHALL validate that both required members are
present before parsing.

#### Scenario: User has all permissions

- **WHEN** Jira returns
  `{"globalPermissions": [], "projectPermissions": [{"permission": "<viewMeetingAri>", "issues": [10001], "projects": []}, {"permission": "<editMeetingAri>", "issues": [10001], "projects": []}]}`
- **THEN** system extracts permissions as `["view-meeting", "edit-meeting"]`

#### Scenario: User has partial permissions

- **WHEN** Jira returns a single `projectPermissions` entry whose `permission`
  is the `view-meeting` identifier
- **THEN** system extracts permissions as `["view-meeting"]`

#### Scenario: User has no permissions

- **WHEN** Jira returns `{"globalPermissions": [], "projectPermissions": []}`
- **THEN** system extracts permissions as empty array `[]` and caches that
  result

#### Scenario: Identifiers mapped back to manifest keys

- **WHEN** Jira returns the qualified identifier
  `ari:cloud:ecosystem::extension/app-uuid/env-uuid/static/view-meeting`
- **THEN** the permission published downstream is the bare key `view-meeting`

#### Scenario: Unknown identifier ignored

- **WHEN** Jira returns a granted identifier that does not correspond to a
  permission the service requested
- **THEN** system omits it from the published permissions and logs a warning

#### Scenario: Jira API error response handled

- **WHEN** Jira returns HTTP 400 with an error message
- **THEN** system logs the error and treats it as an API failure

#### Scenario: Malformed response rejected

- **WHEN** Jira returns JSON omitting the required `projectPermissions` or
  `globalPermissions` member
- **THEN** system treats the response as an API failure that triggers the
  stale-cache fallback, and does NOT publish an empty permission set as though
  the call had succeeded

#### Scenario: Unexpected JSON structure logged

- **WHEN** Jira returns a response with additional unknown fields
- **THEN** system logs a warning but continues processing known fields

### Requirement: Handle Jira API timeouts and rate limits

The authorization service SHALL handle Jira API timeouts with bounded retries
and SHALL respect rate limit responses.

#### Scenario: Jira API timeout triggers retry

- **WHEN** Jira API does not respond within 2 seconds
- **THEN** system retries the request up to 3 times with exponential backoff

#### Scenario: All retries exhausted

- **WHEN** all 3 retry attempts fail due to timeout
- **THEN** system falls back to stale cache or returns empty permissions

#### Scenario: Successful retry

- **WHEN** the first request times out but the second retry succeeds
- **THEN** system uses permissions from the successful retry response

#### Scenario: Rate limit response received

- **WHEN** Jira returns HTTP 429 Too Many Requests
- **THEN** system logs a warning and falls back to stale cache or empty
  permissions

#### Scenario: Retry-After header respected

- **WHEN** Jira returns HTTP 429 with `Retry-After: 60` header
- **THEN** system does NOT retry for at least 60 seconds

#### Scenario: Retry-After bounded by the request deadline

- **WHEN** Jira returns HTTP 429 with a `Retry-After` interval longer than the
  remaining request deadline
- **THEN** system abandons the retry and falls back to stale cache rather than
  waiting past the deadline

### Requirement: Cache permission check results in Valkey

The authorization service SHALL cache permission check results in Valkey using
key format `perm:{cloudId}:{accountId}:{context}` with a 15-minute TTL,
serialised as a JSON array. The `{context}` segment SHALL be the issue
identifier when the check was made for an issue, and the project identifier when
it was made for a project.

#### Scenario: Permission result cached after Jira API call

- **WHEN** Jira API returns permissions `["view-meeting", "edit-meeting"]` for
  user `user123` on issue `10001` in tenant `cloud456`
- **THEN** system stores the result in Valkey with key
  `perm:cloud456:user123:10001` and TTL 900 seconds

#### Scenario: Issue and project contexts do not collide

- **WHEN** the same user is checked for issue `10001` and for project `10001`
- **THEN** system uses distinct cache keys so a project result is never served
  for an issue check

#### Scenario: Cached permission result used on subsequent request

- **WHEN** the same user requests the same resource within 15 minutes
- **THEN** system retrieves permissions from Valkey without calling the Jira API

#### Scenario: Cache miss triggers Jira API call

- **WHEN** no cached result exists for the permission key
- **THEN** system calls the Jira API and caches the result

#### Scenario: Cache entry expires after 15 minutes

- **WHEN** a permission result is cached at time T
- **THEN** Valkey automatically removes the entry at time T+15 minutes

#### Scenario: Configurable cache TTL

- **WHEN** environment variable `CACHE_TTL` is set to `10m`
- **THEN** system uses a 10-minute TTL instead of the default 15 minutes

#### Scenario: Permissions serialized to JSON

- **WHEN** caching permissions `["view-meeting", "edit-meeting"]`
- **THEN** Valkey stores the value as `["view-meeting","edit-meeting"]`

#### Scenario: Empty permissions cached

- **WHEN** the user has no permissions
- **THEN** Valkey stores the value as `[]`

#### Scenario: Cached JSON deserialized correctly

- **WHEN** retrieving cached permissions
- **THEN** system parses the JSON array back to a string slice

#### Scenario: Retention copy written alongside the primary entry

- **WHEN** a permission result is cached under key `perm:cloud456:user123:10001`
- **THEN** system also stores the same JSON array under
  `perm:cloud456:user123:10001:stale` with the retention TTL, so the value
  outlives the primary entry and remains available to the stale-while-error
  fallback

#### Scenario: Configurable stale retention window

- **WHEN** environment variable `CACHE_STALE_RETENTION` is set to `6h`
- **THEN** system retains the stale copy for 6 hours instead of the default 24
  hours

### Requirement: Cache key format ensures uniqueness

The authorization service SHALL construct cache keys that uniquely identify the
permission context across tenant, user and resource.

#### Scenario: Different users have separate cache entries

- **WHEN** user A and user B access the same issue
- **THEN** system creates separate cache entries `perm:cloud:userA:10001` and
  `perm:cloud:userB:10001`

#### Scenario: Different issues have separate cache entries

- **WHEN** the same user accesses issue 10001 and issue 10002
- **THEN** system creates separate cache entries `perm:cloud:user:10001` and
  `perm:cloud:user:10002`

#### Scenario: Different tenants have separate cache entries

- **WHEN** the same accountId exists in two different cloudIds
- **THEN** system creates separate cache entries `perm:cloudA:user:10001` and
  `perm:cloudB:user:10001`

### Requirement: Implement stale-while-error fallback

The authorization service SHALL serve stale cached permissions when the Jira API
is unavailable, even if the cache TTL has expired. Because the primary entry is
removed by Valkey once its TTL elapses, the fallback SHALL read the retention
copy held under the `:stale` companion key.

#### Scenario: Stale cache used on Jira API failure

- **WHEN** the Jira API returns an error and stale cached permissions exist
- **THEN** system returns the stale cached permissions and logs a warning

#### Scenario: Stale copy outlives the expired primary entry

- **WHEN** a permission result was cached more than 15 minutes ago and the Jira
  API returns an error
- **THEN** the primary lookup misses, and system serves the permissions from the
  retention copy

#### Scenario: Stale copy discarded after the retention window

- **WHEN** a permission result was cached longer ago than the retention window
  and the Jira API returns an error
- **THEN** no stale permissions are available and system returns an empty
  permissions array

#### Scenario: No stale cache available on API failure

- **WHEN** the Jira API returns an error and no cached permissions exist
- **THEN** system returns an empty permissions array and logs an error

#### Scenario: Fresh cache preferred over stale

- **WHEN** the Jira API is available
- **THEN** system always calls the API for expired cache entries and does NOT
  use stale data

### Requirement: Handle Valkey connection failures

The authorization service SHALL handle Valkey connection failures gracefully by
bypassing the cache rather than failing the request.

#### Scenario: Valkey unavailable on read

- **WHEN** Valkey is unreachable during cache lookup
- **THEN** system calls the Jira API directly without caching the result

#### Scenario: Valkey unavailable on write

- **WHEN** Valkey is unreachable during cache write
- **THEN** system logs a warning and continues with the response

#### Scenario: Valkey recovers automatically

- **WHEN** Valkey becomes available after a temporary failure
- **THEN** system resumes normal caching behaviour without restart

#### Scenario: Service starts while Valkey is unreachable

- **WHEN** the service starts and Valkey cannot be reached
- **THEN** the service still starts and serves authorization requests, logging a
  warning rather than terminating

#### Scenario: Connection attempts are time-bounded

- **WHEN** establishing a connection to Valkey stalls
- **THEN** the attempt fails within a bounded timeout so concurrent
  authorization requests are not blocked waiting on the cache

### Requirement: Support managed Valkey endpoints

The authorization service SHALL be deployable against a managed Valkey endpoint
such as AWS ElastiCache without code changes, configured entirely through
environment variables.

#### Scenario: In-transit encryption enabled

- **WHEN** environment variable `REDIS_TLS` is set to `true`
- **THEN** system connects using TLS 1.2 or higher

#### Scenario: Plaintext connection by default

- **WHEN** `REDIS_TLS` is unset
- **THEN** system connects without TLS, matching the local Valkey container

#### Scenario: Named user authentication

- **WHEN** environment variables `REDIS_USERNAME` and `REDIS_PASSWORD` are set
- **THEN** system authenticates with that user rather than the default user

#### Scenario: Credentials resolved per connection

- **WHEN** system opens a connection to Valkey
- **THEN** credentials are resolved at that moment rather than captured once at
  startup, so short-lived secrets can be adopted without a restart

#### Scenario: Credential resolution failure surfaces as a cache error

- **WHEN** credentials cannot be resolved for a connection attempt
- **THEN** the cache operation fails and the request falls back to the Jira API

#### Scenario: Deployment topology detected automatically

- **WHEN** `REDIS_ADDR` points at a cluster-mode endpoint
- **THEN** system operates in cluster mode, and falls back to single-node mode
  when the endpoint does not advertise cluster support

#### Scenario: Server-assisted client-side caching disabled

- **WHEN** system connects to any Valkey deployment
- **THEN** system does not issue `CLIENT TRACKING`, which managed proxy-based
  endpoints do not support

### Requirement: Handle concurrent cache writes

The authorization service SHALL handle concurrent permission checks for the same
key without data corruption.

#### Scenario: Concurrent requests for same resource

- **WHEN** two requests for the same user and issue arrive simultaneously
- **THEN** both may call the Jira API, but the final cached value is consistent

#### Scenario: Last write wins

- **WHEN** two Jira API responses arrive with different results
- **THEN** Valkey stores the last written value

### Requirement: Return allow decision with injected identity headers

The authorization service SHALL return a `CheckResponse` containing an
`OkHttpResponse` with `X-Tenant-ID`, `X-Account-Id` and `X-Project-Permissions`
headers when authorization succeeds.

#### Scenario: Success response carries OK status

- **WHEN** authorization succeeds
- **THEN** CheckResponse contains `Status{Code: Code_OK}` and an
  `OkHttpResponse`

#### Scenario: X-Tenant-ID carries cloudId

- **WHEN** FIT yields cloudId `abc123-def456`
- **THEN** the response injects header `X-Tenant-ID: abc123-def456`

#### Scenario: X-Account-Id carries accountId

- **WHEN** FIT yields accountId `5f8c9d1234567890abcdef01`
- **THEN** the response injects header `X-Account-Id: 5f8c9d1234567890abcdef01`

#### Scenario: Multiple permissions injected as comma-separated list

- **WHEN** the user has permissions `["view-meeting", "edit-meeting"]`
- **THEN** the response injects header
  `X-Project-Permissions: view-meeting,edit-meeting`

#### Scenario: Single permission injected

- **WHEN** the user has permissions `["view-meeting"]`
- **THEN** the response injects header `X-Project-Permissions: view-meeting`

#### Scenario: No permissions results in empty header

- **WHEN** the user has no permissions
- **THEN** the response injects header `X-Project-Permissions` with an empty
  value

#### Scenario: Headers added as HeaderValueOption with append false

- **WHEN** building the OkHttpResponse
- **THEN** each header is added as
  `HeaderValueOption{Header: {Key, Value}, Append: false}` so the backend
  receives exactly one instance of each

### Requirement: Return deny decision on authorization failure

The authorization service SHALL return a `CheckResponse` with a
`DeniedHttpResponse` when authorization fails, and the request SHALL NOT reach
any backend service.

#### Scenario: Deny response with 403 status

- **WHEN** authorization fails due to missing permissions
- **THEN** the service returns
  `CheckResponse{Status: PERMISSION_DENIED, HttpResponse: DeniedHttpResponse{Status: 403}}`

#### Scenario: Deny response with error message

- **WHEN** authorization fails
- **THEN** DeniedHttpResponse includes body
  `{"error":"authorization_failed","message":"<reason>"}`

#### Scenario: Deny response with 401 for invalid token

- **WHEN** authorization fails due to an unparseable or invalid token
- **THEN** DeniedHttpResponse includes status 401 instead of 403

#### Scenario: Missing identity prevents header injection

- **WHEN** FIT does not yield a valid cloudId or accountId
- **THEN** the request is denied and no identity headers are injected

### Requirement: Remove authorization header before forwarding

The FIT-bearing `Authorization` header SHALL NOT be forwarded to backend
services.

#### Scenario: Authorization header removed

- **WHEN** client sends request with `Authorization: Bearer <FIT>`
- **THEN** the backend service does NOT receive the Authorization header

#### Scenario: Backend receives only identity headers

- **WHEN** the request is forwarded to a backend
- **THEN** the backend receives `X-Tenant-ID`, `X-Account-Id` and
  `X-Project-Permissions` but NOT `Authorization`

### Requirement: Preserve unrelated request headers

Headers not managed by the gateway SHALL be forwarded to backend services
unchanged.

#### Scenario: Content-Type header preserved

- **WHEN** client sends `Content-Type: application/json`
- **THEN** the backend receives the same `Content-Type: application/json` header

#### Scenario: Custom headers preserved

- **WHEN** client sends `X-Custom-Header: value`
- **THEN** the backend receives the same `X-Custom-Header: value` header

#### Scenario: X-Issue-Id and X-Project-Id preserved

- **WHEN** client sends `X-Issue-Id: 10001` and `X-Project-Id: 10002`
- **THEN** the backend receives both headers unchanged

### Requirement: Handle authorization service errors

The authorization service SHALL handle runtime errors gracefully and remain
available under concurrency.

#### Scenario: Panic recovered

- **WHEN** the Check method panics due to an unexpected error
- **THEN** the gRPC server recovers, logs the error, and returns INTERNAL status

#### Scenario: Context cancellation handled

- **WHEN** the gateway cancels the request due to timeout or disconnect
- **THEN** the service stops processing and returns immediately

#### Scenario: Concurrent requests handled

- **WHEN** multiple gateway instances call Check simultaneously
- **THEN** the service handles all requests concurrently without blocking

### Requirement: Support gRPC health check protocol

The authorization service SHALL implement the gRPC health check protocol so the
gateway can health check it.

#### Scenario: Health check returns SERVING

- **WHEN** the gateway calls the gRPC health check
- **THEN** the service returns status SERVING

#### Scenario: Unhealthy service returns NOT_SERVING

- **WHEN** the service cannot connect to Valkey
- **THEN** the health check returns NOT_SERVING

### Requirement: Log authorization decisions and cache outcomes

The authorization service SHALL log each Check invocation, its outcome, and
cache hit/miss events for debugging and monitoring.

#### Scenario: Request logged on entry

- **WHEN** the Check method is called
- **THEN** the service logs "Check request: issueID=10001, accountId=user123"

#### Scenario: Response logged on exit

- **WHEN** the Check method completes
- **THEN** the service logs "Check response: status=OK,
  permissions=view-meeting,edit-meeting"

#### Scenario: Error logged on failure

- **WHEN** authorization fails
- **THEN** the service logs the error with reason and context

#### Scenario: Cache hit logged

- **WHEN** a permission result is found in Valkey
- **THEN** the service logs "Cache hit for perm:cloud:user:issue"

#### Scenario: Cache miss logged

- **WHEN** a permission result is not found in Valkey
- **THEN** the service logs "Cache miss for perm:cloud:user:issue"

#### Scenario: Cache error logged

- **WHEN** a Valkey operation fails
- **THEN** the service logs the error with failure details

### Requirement: Inject issue context headers from Forge context

The Forge UI remote fetch helper SHALL inject `X-Issue-Id` or `X-Project-Id`
headers extracted from the Forge context into all backend requests, so the
gateway can resolve the authorization context.

#### Scenario: X-Issue-Id injected from Forge issue context

- **WHEN** Forge context contains `context.extension.issue.id = "10001"`
- **THEN** the request includes header `X-Issue-Id: 10001`

#### Scenario: X-Project-Id injected from Forge project context

- **WHEN** Forge context contains `context.extension.project.id = "10002"`
- **THEN** the request includes header `X-Project-Id: 10002`

#### Scenario: Both headers injected when available

- **WHEN** Forge context contains both issue and project
- **THEN** the request includes both `X-Issue-Id` and `X-Project-Id` headers

#### Scenario: No context headers when Forge context unavailable

- **WHEN** Forge context does not contain issue or project
- **THEN** the request is sent without `X-Issue-Id` or `X-Project-Id` headers

#### Scenario: Numeric issue IDs converted to strings

- **WHEN** Forge context contains `context.extension.issue.id = 10001` (number)
- **THEN** the request includes header `X-Issue-Id: 10001` (string)

#### Scenario: Project without an identifier omits the header

- **WHEN** Forge context contains a project that exposes only `key` and no `id`
- **THEN** the request is sent without the `X-Project-Id` header

### Requirement: Preserve existing forgeRemoteFetch behavior

The Forge UI remote fetch helper SHALL maintain existing functionality for path,
method, headers and body forwarding, and SHALL degrade gracefully when the Forge
context cannot be read.

#### Scenario: Original Authorization header preserved

- **WHEN** Forge adds the FIT to the Authorization header
- **THEN** the request still includes the original Authorization header

#### Scenario: Custom headers not overwritten

- **WHEN** the caller provides custom headers such as `Content-Type`
- **THEN** context headers are added without overwriting existing headers

#### Scenario: Request body unchanged

- **WHEN** the caller provides a request body
- **THEN** the body is forwarded unchanged with the new context headers added

#### Scenario: Context fetch timeout

- **WHEN** `view.getContext()` times out after 5 seconds
- **THEN** the request proceeds without context headers and logs a warning

#### Scenario: Context unavailable in non-Forge environment

- **WHEN** running outside the Forge iframe
- **THEN** the context fetch fails gracefully and the request proceeds

#### Scenario: Malformed context object handled

- **WHEN** Forge returns a context without the expected structure
- **THEN** headers are not injected and the request proceeds

### Requirement: Enable app system token for all backend endpoints

The Forge app manifest SHALL enable `endpoint.auth.appSystemToken` for all
endpoints that route to backend services, so the gateway receives the token
required for the Jira permission check.

#### Scenario: System token enabled for meeting endpoints

- **WHEN** the manifest defines endpoints with paths starting with
  `/api/1/meetings`
- **THEN** each endpoint has `auth.appSystemToken.enabled: true`

#### Scenario: System token enabled for tenant endpoints

- **WHEN** the manifest defines endpoints with paths starting with
  `/api/1/tenants`
- **THEN** each endpoint has `auth.appSystemToken.enabled: true`

#### Scenario: System token enabled for notification endpoints

- **WHEN** the manifest defines endpoints with paths starting with
  `/api/1/notifications`
- **THEN** each endpoint has `auth.appSystemToken.enabled: true`

#### Scenario: System token appears in request headers

- **WHEN** Forge calls a backend endpoint with the system token enabled
- **THEN** the request includes the `x-forge-oauth-system` header with the app
  system token

### Requirement: Configure remote base URL to the gateway

The Forge app manifest SHALL configure the remote base URL to point to the Envoy
gateway instead of direct backend services, while preserving existing route
definitions.

#### Scenario: Remote base URL points to the gateway

- **WHEN** the manifest defines a remote with key `meet-backend`
- **THEN** the remote `baseUrl` is `${SMISKI_API_BASE_URL}` which resolves to
  the Envoy gateway

#### Scenario: All endpoints use the gateway remote

- **WHEN** the manifest defines backend endpoints
- **THEN** each endpoint references the remote pointing to the gateway

#### Scenario: Endpoint paths unchanged

- **WHEN** updating the manifest to enable the system token
- **THEN** endpoint route paths such as `/api/1/meetings:instant` remain
  unchanged

#### Scenario: Endpoint operations unchanged

- **WHEN** updating the manifest
- **THEN** endpoint operations remain unchanged

### Requirement: Deploy manifest changes with upgrade

The Forge app SHALL be deployed and upgraded in existing installations to apply
the manifest authentication changes.

#### Scenario: Manifest deployed to development environment

- **WHEN** running `forge deploy -e development`
- **THEN** manifest changes are deployed to the development environment

#### Scenario: Existing installation upgraded

- **WHEN** running `forge install --upgrade --site <site>`
- **THEN** the installed app receives the updated manifest with new scopes and
  auth configuration

#### Scenario: System token becomes available after upgrade

- **WHEN** the app is upgraded with `appSystemToken.enabled: true`
- **THEN** subsequent requests include the `x-forge-oauth-system` header

### Requirement: Extract Forge environment identifier from FIT claims

The authorization service SHALL extract the Forge application identifier and
environment identifier from the FIT `app.id` and `app.environment.id` claims, so
custom permission identifiers can be qualified. Both claims are Atlassian
Resource Identifiers whose trailing slash-separated segment carries the
identifying UUID.

#### Scenario: Application identifier extracted

- **WHEN** FIT contains `"app": {"id": "ari:cloud:ecosystem::app/app-uuid"}`
- **THEN** system extracts the application identifier as `app-uuid`

#### Scenario: Environment identifier extracted from single-segment form

- **WHEN** FIT contains
  `"app": {"environment": {"id": "ari:cloud:ecosystem::environment/env-uuid"}}`
- **THEN** system extracts the environment identifier as `env-uuid`

#### Scenario: Environment identifier extracted from two-segment form

- **WHEN** FIT contains
  `"app": {"environment": {"id": "ari:cloud:ecosystem::environment/app-uuid/env-uuid"}}`
- **THEN** system extracts the environment identifier as `env-uuid`

#### Scenario: Missing environment claim rejected

- **WHEN** FIT omits `app.environment.id`
- **THEN** system reports a missing-claims error and does not construct a
  permission identifier

#### Scenario: Empty trailing segment rejected

- **WHEN** the environment claim ends with a trailing separator and yields an
  empty final segment
- **THEN** system reports a missing-claims error rather than emitting an
  identifier containing an empty segment

### Requirement: Allow the project context header through the gateway filter

The gateway SHALL forward the `x-project-id` header to the authorization service
in the external authorization request, alongside the existing authorization and
context headers.

#### Scenario: Project context header reaches the authorization service

- **WHEN** a client sends `X-Project-Id: 10002` to an authenticated route
- **THEN** the authorization service receives `x-project-id` in the CheckRequest
  headers

#### Scenario: Header absent when not sent

- **WHEN** a client sends no `X-Project-Id` header
- **THEN** the authorization service receives no `x-project-id` value and treats
  the project context as absent
