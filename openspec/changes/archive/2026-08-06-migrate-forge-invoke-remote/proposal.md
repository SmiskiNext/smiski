## Why

Every authenticated backend route is currently denied. The Custom UI calls the
`meet` backend with `requestRemote`, which per Atlassian's documentation "will
not include OAuth tokens, even if configured for the remote". The gateway's
authorization service therefore never receives `x-forge-oauth-system` and fails
closed with `missing system token`
(`services/gateway/internal/authz/service.go:165`), returning HTTP 500 before
the Jira permission check runs.

The app also never sends the `x-issue-id` / `x-project-id` headers the gateway
reads (`service.go:47-48`). Without them the service skips the permission check
entirely and returns an empty permission set, which the backend's
`@PreAuthorize("hasAuthority('view-meeting'|'edit-meeting')")` guards reject on
all 21 annotated endpoints in `MeetingController`.

`openspec/specs/permission-checking/spec.md:782-930` already requires both the
context headers and the system token, so this change closes an existing gap
between specification and implementation rather than introducing new behavior.

## What Changes

- Replace the `requestRemote` transport in
  `app/static/smiski-ui/src/api/forgeRemoteFetch.ts` with `invokeRemote`, which
  is proxied through the Forge platform and therefore carries the app system
  token. The adapter keeps its `fetch`-shaped signature so the generated
  `@smiskinext/smiski-ts` SDK, its URL building, and its zod validation are
  untouched.
- Adapt to the three `invokeRemote` contract differences: it takes no remote
  key, it serializes `body` itself (so the adapter passes an object rather than
  a pre-stringified string), and it resolves to `{ status, headers, body }`
  rather than a WHATWG `Response`. The adapter reconstructs a `Response` so the
  SDK client (`sdks/typescript/src/generated/client/client.gen.ts`) continues to
  read `.ok`, `.status`, and `.headers` as it does today.
- Inject `x-issue-id` and `x-project-id` on every backend request, sourced from
  the Forge module context and threaded through the platform-modal payloads so
  the three modal roots — which render in their own iframes — can supply them.
- **BREAKING** Change the `Content-Type` of error responses from
  `application/problem+json` to `application/json` across all three services.
  `invokeRemote` rejects any non-2xx response whose `content-type` is not
  `application/json`, replacing the body with a generic platform error. The
  response body keeps its full RFC 9457 shape (`type`, `title`, `status`,
  `detail`, `code`, `traceId`), so only the media type changes.
- **BREAKING** Add `resolver.endpoint: meet-endpoint` to the `jira:issuePanel`
  and `jira:projectPage` modules. `invokeRemote` resolves its target through the
  invoking module's resolver endpoint, and editing `endpoint` entries forces a
  major version upgrade requiring re-consent.
- Leave the SSE join-request streams on raw browser `fetch`. Forge Remote
  buffers response bodies and cannot stream `text/event-stream`, a limitation
  documented for both bridge methods.

## Capabilities

### New Capabilities

None. This change modifies existing behavior only.

### Modified Capabilities

- `permission-checking`: the Forge UI transport requirement moves from
  `requestRemote` to `invokeRemote`; the manifest requirement gains
  `resolver.endpoint` on both UI modules; the context-header requirement gains
  the modal-surface propagation path and the numeric-identifier constraint the
  gateway's `strconv.ParseInt` enforces.
- `api-convention`: error responses carry `Content-Type: application/json`
  instead of `application/problem+json`, while the RFC 9457 body shape is
  retained unchanged.
- `ui-backend-interaction`: the scenarios describing the backend transport and
  the frontend error path are restated for `invokeRemote` and the new error
  media type.

## Impact

**Forge app** (`app/`)

- `manifest.yml` — `resolver.endpoint` on both UI modules;
  `external.fetch.client` retains `- remote: meet-backend` because SSE and
  LiveKit still egress directly.
- `static/smiski-ui/src/api/forgeRemoteFetch.ts` — transport rewrite.
- `static/smiski-ui/src/App.tsx` — retains `project.id` (discarded today at
  line 157) and `issue.id`.
- `static/smiski-ui/src/utils/{scheduleMeetingModalContext,instantMeetingModalContext,issuePanelModalContext}.ts`
  and `hooks/useIssuePanel{Schedule,Instant}Modal.ts` — carry the identifiers.
- Redeploy sequence: `forge deploy` then `forge install --upgrade`; a tunnel
  restart does not apply manifest changes.

**Backend** (`services/`)

- `shared/.../web/ResultResponder.java:65`,
  `shared/.../web/GlobalExceptionHandler.java:124`,
  `shared/.../identity/PermissionFilter.java:64`,
  `shared/.../web/ProblemDetailOpenApiCustomizer.java:26` — media type only.
  Because these live in `shared`, `tenant`, `meet`, and `notification` change
  together and stay consistent.
- `meet/.../security/SecurityConfig.java` — the `accessDeniedHandler` and
  `authenticationEntryPoint` writers.
- Roughly 75 assertions across ~20 test files reference the old media type.
- `pnpm run openapi` regenerates the three specs; 120 error-response entries in
  `services/meet/openapi.yaml` change media type.

**Not in scope**

- SSE transport (`api/sseClient.ts`, `api/meetingEvents.ts`), LiveKit
  signalling, `requestJira` Jira-native calls, and `appUserToken`.
- Two pre-existing spec drifts are documented but not corrected here:
  `permission-checking` describes tenant and notification endpoints that the
  manifest does not define, and it specifies `baseUrl: ${SMISKI_API_BASE_URL}`
  while Forge's server-side egress check rejects manifest variables, which is
  why `scripts/render-manifest.sh` exists.

**Risk**

- `invokeRemote` applies a 25s timeout with no retries, against the 15s
  per-route timeout Envoy already enforces.
- Backend calls become metered Forge invocations; `usePendingJoinRequests` polls
  at 60s intervals, which stays well inside the 500-calls-per-25s bridge limit.
- The installed `@forge/bridge` is `6.1.0-next.8`, a pre-release whose
  `invoke-endpoint.js` returns an error object where the documentation describes
  a rejected promise. The adapter must handle both shapes.
