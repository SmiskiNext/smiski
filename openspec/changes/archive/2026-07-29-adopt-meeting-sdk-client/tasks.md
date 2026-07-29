## 1. Dependency wiring

- [x] 1.1 Add `@smiskinext/smiski-ts` (workspace) to
      `app/static/smiski-ui/package.json` dependencies and install
- [x] 1.2 Confirm the SDK resolves in `smiski-ui` (import `createInstant`,
      `schedule`, `createClient`, `createConfig`) via a `typecheck` ← (verify:
      SDK imports resolve; Vite can bundle it into dist)

## 2. Forge Remote ↔ SDK adapter

- [x] 2.1 Create `src/api/forgeRemoteFetch.ts`: a `fetch`-shaped adapter that
      receives a `Request`, extracts `pathname+search`, `method`, `headers`, and
      text `body`, and calls `requestRemote('meet-backend', {...})`, returning
      the Response-compatible result
- [x] 2.2 In the adapter module, build the shared SDK client via
      `createClient(createConfig({ baseUrl: <placeholder>, fetch: forgeRemoteFetch }))`
- [x] 2.3 Pass the `version` path parameter from `apiConfig.apiVersion` when
      invoking SDK operations ← (verify: adapter forwards SDK-built path
      `/api/1/meetings:instant` unchanged; body text preserved)

## 3. Instant + scheduled create via SDK

- [x] 3.1 Rewrite `createInstantMeeting` in `src/api/meetings.ts` to call SDK
      `createInstant` with a body conforming to
      `MeetCreateInstantMeetingRequest` (nested `issueLink`, `settings` from
      `DEFAULT_MEETING_SETTINGS`, `host`, resolved `zoneId`, invitees),
      returning the SDK `{ data, error }` result
- [x] 3.2 Rewrite `scheduleMeeting` to call SDK `schedule` with a body
      conforming to `MeetScheduleMeetingRequest` (`organizerEmail`,
      `organizerDisplayName`, `issueLink`, `settings`, `timeRange`, resolved
      `zoneId`, invitees; no `host`), returning the SDK `{ data, error }` result
- [x] 3.3 Update the pure payload builders to produce SDK body types and drop
      the hand-written `InstantMeetingInvokePayload` /
      `ScheduleMeetingInvokePayload` interfaces
- [x] 3.4 Map SDK success snapshots to the app `Meeting` domain type via
      `mappers.ts#meetingFromBackend` ← (verify: instant result exposes meeting
      id + LiveKit token/room; scheduled result is SCHEDULED)

## 4. Hook + modal result-contract wiring

- [x] 4.1 Update `useCreateInstantMeeting` / `useScheduleMeeting` in
      `src/hooks/useMeetingMutations.ts` to pass through the `{ data, error }`
      result and guard `onSuccess` invalidation on `result.data`
- [x] 4.2 Update `StartInstantMeetingModal.tsx` to read `result.error` (mapped
      to a message) and branch on `result.data` for success, replacing the
      `try/catch` around `mutateAsync`; keep `isPending` for the button
- [x] 4.3 Update `ScheduleMeetingModal.tsx` schedule branch to read
      `result.error` / `result.data`; leave the mock-backed edit branch behavior
      intact ← (verify: validation problem keeps modal open with message;
      success closes and reports id)

## 5. Dead code removal

- [x] 5.1 Delete `src/api/client.ts`, `src/api/endpoints.ts`,
      `src/api/recordings.ts`, `src/api/participants.ts`,
      `src/api/permissions.ts`
- [x] 5.2 Remove the never-called functions from `src/api/meetings.ts`:
      `getIssueMeetings`, `getProjectMeetings`, `getMeeting`, `updateMeeting`,
      `cancelMeeting`, `startMeeting`, `endMeeting`,
      `findRunningMeetingHostedByUser`; and remove the `apiRequest` branch of
      `getRoomToken`, keeping the `invoke('getRoomToken')` path
- [x] 5.3 Prune `src/api/mappers.ts` helpers only used by deleted code
      (`meetingsFromBackend`, `roomTokenFromBackend`, `updateMeetingRequest`)
      and update `mappers.test.ts`
- [x] 5.4 Prune `src/api/config.ts` (`transport`, `baseUrl`) and
      `src/api/index.ts` re-exports for deleted modules ← (verify: no remaining
      import references deleted files; `mocks/db` untouched)

## 6. Tests

- [x] 6.1 Rewrite `createInstantMeeting.test.ts`: instant-create issues SDK
      `createInstant` over the requestRemote adapter and returns meeting +
      LiveKit on success (spec: instant creation via SDK over Forge Remote)
- [x] 6.2 Add a test asserting the instant body carries nested `issueLink`,
      `settings`, `host`, `zoneId` and no `X-Tenant-ID`/`X-Account-Id` (spec:
      request body conforms to the instant contract; Forge FIT, no identity
      headers)
- [x] 6.3 Rewrite `scheduleMeeting.test.ts`: scheduled-create issues SDK
      `schedule`, body carries
      `organizerEmail`/`organizerDisplayName`/`issueLink`/`settings`/`timeRange`/`zoneId`
      and no `host` object (spec: scheduled body conforms without host)
- [x] 6.4 Add failure tests: backend rejection/unreachable yields a result with
      `error` present and no mock fallback, for both flows (spec: backend
      failure surfaced as result error, not mocked)
- [x] 6.5 Update/replace `meetings.instant.test.ts` and
      `meetings.schedule.test.ts` for the `{ data, error }` contract and adapter
      transport
- [x] 6.6 Add a modal-level test (or extend existing) that a validation error
      keeps the modal open with a message and a success closes it (spec:
      frontend error handling) ← (verify: all ui-backend-interaction scenarios
      covered)

## 7. Verification gate

- [x] 7.1 Run `pnpm --filter smiski-ui typecheck`
- [x] 7.2 Run `pnpm --filter smiski-ui test`
- [x] 7.3 Run `pnpm --filter smiski-ui build`
- [x] 7.4 Run `biome check src` (in `app/static/smiski-ui`) and `forge lint` (in
      `app/`) ← (verify: typecheck/test/build/lint all pass; dist bundles the
      SDK)
