## MODIFIED Requirements

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

## ADDED Requirements

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
