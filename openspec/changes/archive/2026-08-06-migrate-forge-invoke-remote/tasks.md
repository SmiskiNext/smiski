## 1. Backend error media type

- [x] 1.1 Change `ResultResponder#problem` in
      `services/shared/src/main/java/io/github/smiskinext/shared/infrastructure/web/ResultResponder.java`
      to serve the Problem Details body as `application/json`
- [x] 1.2 Change `GlobalExceptionHandler#problemResponse` in
      `services/shared/.../web/GlobalExceptionHandler.java` to the same media
      type
- [x] 1.3 Change the forbidden-response writer in
      `services/shared/.../identity/PermissionFilter.java` to the same media
      type, keeping the existing JSON body
- [x] 1.4 Change `PROBLEM_MEDIA_TYPE` in
      `services/shared/.../web/ProblemDetailOpenApiCustomizer.java` so the
      documented common error responses (405, 415, 500) match what is emitted
- [x] 1.5 Change the `accessDeniedHandler` and `authenticationEntryPoint`
      writers in `services/meet/.../infrastructure/security/SecurityConfig.java`
      to the same media type, keeping the existing body ← (verify: no remaining
      `application/problem+json` string in any `src/main` of shared, meet,
      tenant, notification; error bodies still carry `type`, `title`, `status`,
      `detail`, `code`, `traceId`)

## 2. Backend test and specification alignment

- [x] 2.1 Update the media-type assertions across the affected `src/test` and
      `src/integrationTest` suites in `shared`, `meet`, and `tenant`
- [x] 2.2 Run `./services/gradlew build` and resolve failures
- [x] 2.3 Regenerate the OpenAPI specifications with `pnpm run openapi` and
      confirm the three service specs document `application/json` for error
      statuses ← (verify: `services/meet/openapi.yaml` has no
      `application/problem+json` entry; success responses unchanged; diff
      limited to media type)

## 3. Forge manifest

- [x] 3.1 Add `resolver.endpoint: meet-endpoint` to the `jira:issuePanel` module
      in `app/manifest.yml`
- [x] 3.2 Add `resolver.endpoint: meet-endpoint` to the `jira:projectPage`
      module
- [x] 3.3 Update the manifest comments that describe `requestRemote` and state
      that `appSystemToken` is inert, so they reflect the new transport
- [ ] 3.4 Run `pnpm manifest:render` then `pnpm exec forge lint` from `app/` ←
      BLOCKED: `forge lint` exits with "Not logged in"; no Forge credentials in
      this environment. `pnpm manifest:render` succeeded and the manifest parses
      as valid YAML with the expected structure (verified: `endpoint` key, its
      `remote` binding and `auth.appSystemToken` unchanged;
      `external.fetch.client` still lists `- remote: meet-backend`). Needs
      manual execution.

## 4. Forge context identifiers

- [x] 4.1 Retain `extension.project?.id` alongside `extension.issue.id` in
      `app/static/smiski-ui/src/App.tsx` for the issue-panel surface, and
      capture `extension.project?.id` for the project-page surface
- [x] 4.2 Add the numeric issue and project identifiers to the modal context
      payloads in `utils/scheduleMeetingModalContext.ts`,
      `utils/instantMeetingModalContext.ts`, and
      `utils/issuePanelModalContext.ts`
- [x] 4.3 Pass the identifiers through `hooks/useIssuePanelScheduleModal.ts` and
      `hooks/useIssuePanelInstantModal.ts` when opening a platform modal
- [x] 4.4 Add a module-scoped context store the transport reads when composing
      headers, and publish to it from every surface root (issue panel, project
      page, and the three modal roots)
- [x] 4.5 Resolve the numeric project identifier via
      `requestJira GET /rest/api/3/project/{key}` when only the project key is
      known, caching the result ← (verify: no code path sends a Jira key or a
      `project-${key}` synthetic value as `x-project-id`; the header is omitted
      when no numeric identifier is available)

## 5. Forge backend transport

- [x] 5.1 Replace `requestRemote` with `invokeRemote` in
      `app/static/smiski-ui/src/api/forgeRemoteFetch.ts`, dropping the remote
      key and keeping the `fetch`-shaped signature the SDK client expects
- [x] 5.2 Convert the SDK-serialized request body to an object before invoking,
      and omit the body when the request carries none
- [x] 5.3 Reconstruct a `Response` from the `{ status, headers, body }` result
      so the SDK client continues to read `ok`, `status`, `headers`, and
      `text()`
- [x] 5.4 Attach `x-issue-id` and `x-project-id` from the context store without
      overwriting caller-supplied headers
- [x] 5.5 Treat both a rejected promise and a resolved error payload as a
      failure, so behavior does not depend on the installed bridge pre-release ←
      (verify: 15 SDK call sites in `api/meetings.ts` compile unchanged;
      `toMeetingProblem` still receives `code`, `traceId`, `status`, `detail`
      from an error response; SSE and LiveKit paths untouched)

## 6. Frontend test coverage

- [x] 6.1 Test that a backend request carries the app system token path — the
      invocation is issued through `invokeRemote` rather than `requestRemote`
- [x] 6.2 Test that a JSON request body reaches the invocation as an object with
      no double-encoding
- [x] 6.3 Test that a request with no body omits the body rather than sending an
      empty string
- [x] 6.4 Test that a 2xx JSON response is returned to the SDK with its status,
      headers, and body intact
- [x] 6.5 Test that a `204 No Content` response resolves successfully with an
      empty representation
- [x] 6.6 Test that a non-2xx response preserves `code`, `traceId`, `status`,
      and `detail` through to the mapped error
- [x] 6.7 Test that an unreachable remote or timeout surfaces through the SDK
      error channel with a readable message
- [x] 6.8 Test that a failure reported as a resolved error payload is still
      treated as a failure
- [x] 6.9 Test that `x-issue-id` and `x-project-id` are injected from the issue
      context, including a numeric identifier rendered as a string
- [x] 6.10 Test that both headers are omitted when no context is available, and
      that a project exposing only a key omits `x-project-id`
- [x] 6.11 Test that caller-supplied headers such as `Content-Type` are not
      overwritten by context injection
- [x] 6.12 Test that a request issued from a modal surface carries the same
      identifiers as the surface that opened it
- [x] 6.13 Update the existing suites that mock `requestRemote`
      (`createInstantMeeting.test.ts`, `scheduleMeeting.test.ts`,
      `meetings.invitees.test.ts`, `meetings.settings.test.ts`,
      `meetings.joinFlow.test.ts`, `meetings.joinRequests.test.ts`, and the
      `@forge/bridge` module mocks in the remaining `api/` and component tests)
      ← (verify: every scenario in the `permission-checking` delta has a test;
      no suite still asserts a `requestRemote` call for a backend operation)

## 7. Verification

- [x] 7.1 Run `pnpm lint`, `pnpm typecheck`, and `pnpm test` from `app/` ← all
      pass: biome clean over 162 files, `tsc --noEmit` clean, 183 tests across
      34 files pass
- [x] 7.2 Run `./services/gradlew build` and `pnpm lint` from the repository
      root ← `build` is a composite build, so each included build was built
      explicitly (`shared`, `meet`, `tenant`, `notification`) — all BUILD
      SUCCESSFUL; `spotlessApply` clean; root `pnpm lint` (markdownlint) reports
      0 issues; `pnpm run openapi` regenerates and validates all four specs with
      the diff limited to the media-type line
- [ ] 7.3 Deploy with `pnpm run deploy` followed by
      `pnpm exec forge install --upgrade`, since a tunnel restart does not apply
      manifest changes ← BLOCKED: requires a real Jira site and Forge
      credentials. `forge` CLI reports "Not logged in". Needs manual execution.
- [ ] 7.4 Confirm on a real site that the gateway logs report
      `hasSystemToken=true` with a populated `issueID` or `projectID`, that a
      permitted user reaches the backend, and that a denied user sees a message
      carrying its `code` and `traceId` ← (verify: no request is denied with
      `missing system token`; the join-request event stream still connects,
      confirming the SSE path was not migrated) ← BLOCKED: requires a deployed
      app on a real Jira site. Needs manual execution after 7.3.
