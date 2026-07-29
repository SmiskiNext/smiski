## 1. Manifest — declare the meet backend as a Forge remote

- [x] 1.1 Add a `remotes` entry in `app/manifest.yml` for the `meet` backend
      (`key`, `baseUrl`, `operations: [compute]`, storage/EUD flags as
      appropriate)
- [x] 1.2 Add a `modules.endpoint` binding the remote with `auth` token flags so
      Forge attaches the FIT (and `x-forge-oauth-*` if needed later)
- [x] 1.3 Reconcile the `permissions.external.fetch.backend` placeholder with
      the declared remote origin
- [x] 1.4 Run `forge lint` to validate the manifest ← (verify: `remotes` +
      `endpoint` are valid; remote key/route match what `api/meetings.ts` will
      call)

## 2. Frontend — call the backend via requestRemote

- [x] 2.1 In `app/static/smiski-ui/src/api/meetings.ts`, change
      `createInstantMeeting` to call
      `requestRemote(<remoteKey>, { path, method: 'POST', headers: { 'Content-Type': 'application/json' }, body })`
      instead of `invoke('createInstantMeeting', …)`
- [x] 2.2 Keep `buildInstantMeetingPayload` (JSON body) and `meetingFromBackend`
      (response mapping) unchanged; parse the `requestRemote` `Response` body
      before mapping
- [x] 2.3 Ensure NO `X-Tenant-ID`/`X-Account-Id` (or any client-asserted
      identity) header is set anywhere in the instant-create path
- [x] 2.4 Map a non-2xx `Response` (problem+json) to an error the existing modal
      renders, preserving the in-form validation message behavior ← (verify:
      FIT-only request, no identity headers, backend error surfaced to the form
      — matches spec "Forge attaches the FIT and the app asserts no identity
      headers" + "Backend failure is surfaced, not mocked")

## 3. Remove the dead resolver + backend-SDK path

- [x] 3.1 Re-grep to confirm `@smiskinext/smiski-ts` and `meetSdkClient` are
      only used by the instant-create resolver path before removing anything
- [x] 3.2 Delete the `createInstantMeeting` resolver definition and its imports
      (`createMeetClient`, `smiski-ts` types/`createInstant`) from
      `app/src/index.ts`; keep `getRoomToken`, `searchWorkspaceUsers`, and other
      stubs intact
- [x] 3.3 Delete `app/src/meetSdkClient.ts`
- [x] 3.4 Remove the `@smiskinext/smiski-ts` dependency from `app/package.json`
      ← (verify: no remaining importer of `meetSdkClient` or
      `@smiskinext/smiski-ts` in `app/src`; resolver still compiles)

## 4. Tests

- [x] 4.1 Update `app/static/smiski-ui/src/api/createInstantMeeting.test.ts` to
      mock `requestRemote` (instead of `invoke`), assert it is called with the
      correct remote key / path / JSON body, and that the `{meeting, livekit}`
      response maps to a `Meeting` (spec: "Instant creation calls the backend
      via Forge Remote")
- [x] 4.2 Add/adjust a test asserting the instant-create request sets no
      `X-Tenant-ID`/`X-Account-Id` header (spec: "Forge attaches the FIT and the
      app asserts no identity headers")
- [x] 4.3 Keep the backend-failure test: a rejected/non-2xx `requestRemote`
      surfaces to the caller with no mock fallback (spec: "Backend failure is
      surfaced, not mocked")

## 5. Verification

- [x] 5.1 `pnpm --filter smiski-ui typecheck`, `pnpm --filter smiski-ui test`,
      `pnpm --filter smiski-ui build`
- [x] 5.2 `pnpm typecheck` at `app/` (resolver compiles after removals)
- [x] 5.3 `forge lint` if runnable (else note the manifest was validated
      structurally) ← (verify: full typecheck/test/build green; manifest valid;
      instant flow uses requestRemote with FIT only)
