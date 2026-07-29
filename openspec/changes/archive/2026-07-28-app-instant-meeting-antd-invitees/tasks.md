## 1. Dependencies, SDK packaging, and manifest

- [x] 1.1 Add `antd`, `@ant-design/icons`, `clsx`, `tailwind-merge` to
      `app/static/smiski-ui/package.json` and install
- [x] 1.2 Add `@smiskinext/smiski-ts` (workspace package `sdks/typescript`) as a
      dependency of `app` and build its `dist` (`pnpm sdk:build`) so the
      resolver can import it
- [x] 1.3 Add `permissions.content.styles: ['unsafe-inline']` to
      `app/manifest.yml`
- [x] 1.4 Run `forge lint` and confirm the manifest is valid ← (verify: manifest
      declares content style allowance and passes Forge validation — ui-design
      "Forge content security allowance") — NOTE: `forge lint` requires
      Atlassian login/network (unavailable here); validated manifest YAML
      syntax + `permissions.content.styles: ['unsafe-inline']` shape
      independently instead.

## 2. Resolver backend clients (app/src)

- [x] 2.1 Create `app/src/jiraSdkClient.ts`: configure the
      `@smiskinext/sdks-jira` client with a fetch adapter that routes through
      `@forge/api` `asUser().requestJira(assumeTrustedRoute(url))`
- [x] 2.2 In `jiraSdkClient.ts` add `searchUsers(query?)`: empty query →
      `getAllUsers`, non-empty → `findUsers`; filter to active `atlassian`
      accounts; map to `{accountId, displayName, email, avatarUrl}`
- [x] 2.3 Create `app/src/meetSdkClient.ts`: configure the
      `@smiskinext/smiski-ts` client with `baseUrl = SMISKI_API_BASE_URL` and a
      resolver-side fetch adapter attaching `X-Tenant-ID` (cloudId),
      `X-Account-Id` (accountId), `Accept`, and `Content-Type`
- [x] 2.4 Add resolver `searchWorkspaceUsers` in `app/src/index.ts` delegating
      to `jiraSdkClient.searchUsers`, deriving identity from `req.context` ←
      (verify: uses asUser, excludes inactive/non-human, surfaces permission
      errors — ui-backend-interaction "Workspace-user search through the
      resolver")
- [x] 2.5 Implement the `createInstantMeeting` resolver in `app/src/index.ts`:
      build `MeetCreateInstantMeetingRequest` from the payload, call `smiski-ts`
      `createInstant({body, path:{version:1}})`, return `{meeting, livekit}` or
      a mapped error ← (verify: identity headers attached server-side, no mock
      fallback, backend errors surfaced — ui-backend-interaction "Instant
      meeting creation through the backend SDK")

## 3. Frontend theming and utility

- [x] 3.1 Upgrade `app/static/smiski-ui/src/components/ui/cn.ts` to
      `twMerge(clsx(inputs))` accepting `ClassValue[]`
- [x] 3.2 Wrap `App.tsx` children in Ant Design `ConfigProvider` with brand
      token `#3385f0` and `darkAlgorithm`/`defaultAlgorithm` driven by the
      resolved color mode ← (verify: dark/light mode propagates to antd —
      ui-design "Ant Design theming aligned to app color mode")

## 4. Frontend data layer (workspace users)

- [x] 4.1 Create `api/workspaceUsers.ts` calling
      `invoke('searchWorkspaceUsers', {query})`, typed to
      `{accountId, displayName, email, avatarUrl}[]`
- [x] 4.2 Create `mocks/workspaceUsers.ts` returning filtered `MOCK_USERS` for
      standalone `vite dev`
- [x] 4.3 Create `hooks/useWorkspaceUsers.ts` with debounced query and DEV↔Forge
      source switch; add a query key in `hooks/queryKeys.ts` and export from
      `hooks/index.ts`

## 5. Ant Design components

- [x] 5.1 Convert `components/shared/IssuePicker.tsx` internals to Ant Design
      `Select` (search enabled) while preserving its props/selection contract ←
      (verify: ScheduleMeetingModal still selects issues correctly — ui-design
      "Issue selector remains usable by dependent forms")
- [x] 5.2 Create `components/shared/WorkspaceUserPicker.tsx`: Ant Design
      `Select` multiple with server-side typeahead via `useWorkspaceUsers`,
      retaining full identity per selection, with loading/empty/error/permission
      states ← (verify: selection persists across searches, empty + error
      states, keeps selection on failure — ui-design "Workspace-user invite
      picker")
- [x] 5.3 Rewrite `components/shared/StartInstantMeetingModal.tsx` fully in Ant
      Design (`Modal`, `Form`, `Input`, `Select`); add props `issueKey?` (hide
      IssuePicker when present) and `chrome`; use `WorkspaceUserPicker`; keep
      selected invitees as full objects ← (verify: shared modal renders in antd
      from both surfaces — ui-design "Create-instant surfaces use Ant Design
      components")

## 6. Instant-create wiring (real backend)

- [x] 6.1 Update `api/meetings.ts`: `CreateInstantMeetingInput` carries full
      `invitees[] {email, accountId, displayName}`, host `deviceId`, `zoneId`;
      `createInstantMeeting` calls `invoke('createInstantMeeting', ...)`
- [x] 6.2 Update `hooks/useMeetingMutations.ts` `useCreateInstantMeeting` to
      call the real API and remove the mock-db branch for the instant flow ←
      (verify: no mock fallback; backend failure surfaced —
      ui-backend-interaction "Backend failure is surfaced, not mocked")
- [x] 6.3 Ensure the modal maps backend problem responses to an inline form
      error and keeps the modal open on failure ← (verify: validation problem
      shown in form — ui-backend-interaction "Frontend error handling for
      resolver failures")

## 7. Issue Panel opens the shared modal

- [x] 7.1 Add `utils/instantMeetingModalContext.ts` (kind + payload/result
      types)
- [x] 7.2 Add `hooks/useIssuePanelInstantModal.ts` (Forge platform modal open +
      DEV in-page fallback) mirroring `useIssuePanelScheduleModal`
- [x] 7.3 Add `features/shared/InstantMeetingModalRoot.tsx` rendering the shared
      modal with `chrome='embedded'` and `view.close(result)`
- [x] 7.4 Register the new modal kind in `App.tsx` modal-surface routing
- [x] 7.5 Update `features/issue-panel/StartInstantMeetingButton.tsx` to open
      the modal after the host-conflict gate (instead of creating immediately),
      with DEV in-page fallback in `IssueMeetingsPanel.tsx` ← (verify: conflict
      gate precedes the form; form opens prefilled for the issue —
      create-instant-meeting "App creates instant meetings from dashboard and
      Issue Panel")

## 8. Tests (derived from spec scenarios)

- [x] 8.1 `useWorkspaceUsers`/mock: empty query returns seeded users; typed
      query filters (ui-backend-interaction: empty vs query search) —
      `mocks/workspaceUsers.test.ts` (mock fn). Hook itself needs a QueryClient
      wrapper; its behavior is the debounced pass-through to the mock/resolver
      fn tested here.
- [x] 8.2 Resolver `searchUsers` mapping/filter: inactive and non-human accounts
      excluded; fields mapped (ui-backend-interaction: exclusion scenario) —
      extracted pure `app/src/workspaceUserMapping.ts` (`toWorkspaceUsers`,
      type-only jira-SDK import, no `@forge/api`), tested via
      `api/workspaceUserMapping.resolver.test.ts`.
- [x] 8.3 `WorkspaceUserPicker`: selected invitee persists across a non-matching
      search; empty-result state; error state keeps selection and allows submit
      (ui-design: invite picker scenarios) — extracted pure
      `components/shared/inviteeIdentity.ts`
      (`mergeInviteeOptions`/`resolveSelectedInvitees`), tested via
      `inviteeIdentity.test.ts` (persist-across-search + retain-on-empty/error).
      No RTL in repo, so the antd render is not asserted directly.
- [x] 8.4 Instant-create request builder: invitees carry
      `email`/`accountId`/`displayName`; no-invitee case sends empty list
      (create-instant-meeting: invitee contract scenarios) — extracted pure
      `buildInstantMeetingPayload` in `api/meetings.ts`, tested via
      `api/meetings.instant.test.ts`.
- [x] 8.5 `useCreateInstantMeeting`: success returns snapshot+livekit; backend
      error surfaced without mock fallback (ui-backend-interaction: creation +
      failure scenarios) — `api/createInstantMeeting.test.ts` mocks
      `@forge/bridge` `invoke`: success maps `{meeting,livekit}`→Meeting;
      rejection propagates (no mock fallback). Tests the API layer the hook's
      `mutationFn` calls directly.
- [x] 8.6 `cn` utility: conflicting Tailwind classes resolve to last; falsy
      inputs ignored (ui-design: cn scenarios) — `components/ui/cn.test.ts`.
- [x] 8.7 Issue Panel entry: host-conflict warning precedes the form; form opens
      prefilled for the issue (create-instant-meeting: Issue Panel scenarios) —
      extracted pure `features/issue-panel/startInstantGate.ts`
      (`shouldWarnBeforeInstant`, `instantModalPayloadFor`), tested via
      `startInstantGate.test.ts`. The end-to-end button click→Forge-Modal wiring
      is not automated (no RTL/jsdom or Forge bridge in vitest); the decision
      logic it drives is covered by the pure helpers.

## 9. Verification

- [x] 9.1 `pnpm sdk:build` and `pnpm sdk:typecheck` (smiski-ts) — both PASS
      (exit 0).
- [x] 9.2 In `app/static/smiski-ui`: `pnpm typecheck`, `pnpm lint`, `pnpm test`,
      `pnpm build` — ALL PASS (typecheck 0; biome lint 130 files clean; vitest
      13 files/46 tests pass; vite build ok — only the expected >500kB antd
      chunk-size advisory, not an error).
- [x] 9.3 In `app`: `pnpm typecheck` and `forge lint` ← (verify: full
      build/lint/test/typecheck green across sdk, smiski-ui, and app root) —
      `pnpm typecheck` PASS (resolver + smiski-ui); app-root `pnpm lint` (biome,
      135 files) PASS. `forge lint` could NOT run — "Not logged in" (Atlassian
      auth/network unavailable here), same as 1.4; manifest YAML +
      `content.styles` shape validated independently.
