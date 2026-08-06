## ADDED Requirements

### Requirement: Forge UI backend transport uses invokeRemote

The Custom UI SHALL reach the backend through the `invokeRemote` bridge method
so that the Forge platform attaches the app system token to every backend
request. The `requestRemote` bridge method SHALL NOT be used for backend calls,
because it omits OAuth tokens and the gateway's authorization service fails
closed without the system token.

The transport SHALL preserve the generated SDK integration: the SDK's typing,
URL building, and response validation remain in effect, and the request path,
method, headers, and body reach the backend unchanged in meaning.

#### Scenario: Backend request carries the app system token

- **WHEN** the Custom UI issues any backend request
- **THEN** the request reaching the gateway includes the `x-forge-oauth-system`
  header, and the authorization service performs the Jira permission check
  instead of denying the request with a missing-system-token fault

#### Scenario: Request body preserved across the transport

- **WHEN** an operation sends a JSON request body
- **THEN** the backend receives the same JSON document the SDK produced, with no
  double-encoding and no dropped members

#### Scenario: Empty-body request omits the body

- **WHEN** an operation sends no request body
- **THEN** the invocation carries no body rather than an empty string

#### Scenario: Successful response reaches the SDK unchanged

- **WHEN** the backend returns a 2xx response with a JSON representation
- **THEN** the SDK receives the status, headers, and body it expects, and
  response validation runs as it does for any other transport

#### Scenario: No-content response handled

- **WHEN** the backend returns `204 No Content`
- **THEN** the operation resolves successfully with an empty representation and
  no parse error is raised

#### Scenario: Error response preserves Problem Details members

- **WHEN** the backend returns a non-2xx response carrying an RFC 9457 body
- **THEN** the `code`, `traceId`, `status`, and `detail` members remain readable
  by the frontend error mapper rather than being replaced by a generic platform
  error message

#### Scenario: Transport failure surfaces as an error result

- **WHEN** the invocation fails because the remote is unreachable, times out, or
  the platform rejects the response
- **THEN** the failure surfaces through the SDK's error channel with a
  human-readable message, and the calling screen renders its error state instead
  of hanging

#### Scenario: Failure reported as a value rather than a rejection

- **WHEN** the bridge reports a failed invocation by resolving with an error
  payload instead of rejecting
- **THEN** the transport still treats it as a failure and surfaces it through
  the SDK's error channel

#### Scenario: Streaming endpoints excluded from the transport

- **WHEN** the app subscribes to a meeting event stream
- **THEN** the subscription does not use the Forge Remote transport, because
  Forge Remote buffers response bodies and cannot deliver `text/event-stream`

### Requirement: UI modules reference the backend endpoint resolver

The Forge app manifest SHALL declare `resolver.endpoint` on every UI module that
calls the backend, referencing the endpoint that binds the backend remote.
`invokeRemote` resolves its target through the invoking module's resolver
endpoint rather than through a remote key supplied at call time.

#### Scenario: Issue panel module references the endpoint

- **WHEN** the manifest defines the `jira:issuePanel` module
- **THEN** the module declares `resolver.endpoint` referencing the endpoint
  whose `remote` is the backend gateway

#### Scenario: Project page module references the endpoint

- **WHEN** the manifest defines the `jira:projectPage` module
- **THEN** the module declares `resolver.endpoint` referencing the same endpoint

#### Scenario: Endpoint and remote definitions unchanged

- **WHEN** the resolver reference is added
- **THEN** the existing endpoint key, its `remote` binding, and its
  `auth.appSystemToken` setting are unchanged

#### Scenario: Direct-egress permission retained for streaming

- **WHEN** the manifest declares client egress permissions
- **THEN** the backend remote remains listed under client fetch permissions,
  because the event streams and LiveKit signalling still leave the iframe
  directly

## MODIFIED Requirements

### Requirement: Inject issue context headers from Forge context

The Forge UI backend transport SHALL inject `X-Issue-Id` or `X-Project-Id`
headers extracted from the Forge context into all backend requests, so the
gateway can resolve the authorization context.

Both identifiers SHALL be the numeric Jira identifiers. The gateway rejects a
non-numeric value as an invalid context identifier, so Jira keys and any
synthetic identifier derived from a key SHALL NOT be sent.

Every surface that calls the backend SHALL supply these identifiers, including
surfaces rendered in a separate platform-modal iframe. Where a surface cannot
observe the identifiers from its own module context, they SHALL be carried in
the payload that opens the surface.

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

#### Scenario: Modal surface supplies the identifiers it was opened with

- **WHEN** a form opened as a platform modal issues a backend request
- **THEN** the request carries the same `X-Issue-Id` and `X-Project-Id` values
  as the surface that opened it, even though the modal renders in its own iframe

#### Scenario: Project page supplies the project identifier

- **WHEN** the project page surface issues a backend request
- **THEN** the request carries `X-Project-Id` with the numeric project
  identifier for the project being viewed

#### Scenario: Key-derived identifiers are never sent

- **WHEN** only a Jira issue key or project key is known and no numeric
  identifier can be resolved
- **THEN** the corresponding header is omitted rather than populated with the
  key or with an identifier synthesized from it

### Requirement: Preserve existing forgeRemoteFetch behavior

The Forge UI backend transport SHALL maintain existing functionality for path,
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

- **WHEN** reading the Forge context does not complete within its time budget
- **THEN** the request proceeds without context headers and logs a warning

#### Scenario: Context unavailable in non-Forge environment

- **WHEN** running outside the Forge iframe
- **THEN** the context fetch fails gracefully and the request proceeds

#### Scenario: Malformed context object handled

- **WHEN** Forge returns a context without the expected structure
- **THEN** headers are not injected and the request proceeds
