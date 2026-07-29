# Tasks

## 1. Time zone from profile

- [x] 1.1 Add optional `timeZone?: string` (IANA id) to `ProjectMember` in
      `src/domain/projectMember.ts`
- [x] 1.2 Map `/myself` `timeZone` into `ProjectMember.timeZone` in
      `src/api/currentUser.ts`
- [x] 1.3 Give `CURRENT_USER` (and `MockUser` type) a default `timeZone` in
      `src/mocks/users.ts` for `vite dev`
- [x] 1.4 Add `resolveUserTimeZone(profileZone?)` to `src/utils/datetime.ts` —
      return profile zone when a resolvable IANA id, else `getLocalTimeZone()` ←
      (verify: valid IANA passthrough, invalid/missing falls back to browser
      zone)

## 2. Schedule API layer (Forge Remote)

- [x] 2.1 Extend `ScheduleMeetingInput` in `src/api/meetings.ts` with
      `endTime: string` and `invitees: MeetingInviteeInput[]` (full identity)
- [x] 2.2 Add `SCHEDULE_MEETING_PATH = '/api/1/meetings:schedule'` and a
      `ScheduleMeetingInvokePayload` interface (no `host`)
- [x] 2.3 Add pure `buildScheduleMeetingPayload(input)` — `title`, `description`
      (defaults to title), `issueLink`, default `settings`,
      `timeRange {startTime,endTime}`, `zoneId`, `invitees[]`; NO `host`
- [x] 2.4 Rewrite `scheduleMeeting()` to call
      `requestRemote(MEET_REMOTE_KEY, POST :schedule)`, reuse
      `readProblem`/problem-detail error shaping, map success with
      `meetingFromBackend` ← (verify: calls Forge Remote with correct
      remote/path/body, no identity headers, no host, surfaces problem+json
      without mock fallback)
- [x] 2.5 Point `useScheduleMeeting` in `src/hooks/useMeetingMutations.ts` at
      the real `scheduleMeeting`; leave `useUpdateMeeting` on the mock
- [x] 2.6 Retire `scheduleMeetingRequest` from `src/api/mappers.ts` (and its
      `mappers.test.ts` case) only if no longer referenced after 2.5

## 3. Schedule form (Ant Design create branch)

- [x] 3.1 Rebuild the create branch of
      `src/components/shared/ScheduleMeetingModal.tsx` with Ant Design `Modal`,
      `Form`, `Input`, date/time controls, `Select`, `Alert`; remove legacy
      `components/ui`/native controls from the create path
- [x] 3.2 Add start-time, end-time, and time-zone controls; seed the zone from
      `resolveUserTimeZone(useCurrentUser().timeZone)`
- [x] 3.3 Replace the participant picker with `WorkspaceUserPicker` (full
      identity invitees)
- [x] 3.4 Convert chosen wall time in the selected zone to UTC ISO via
      `zonedWallTimeToIso`; validate all-present, start < end, start not past
      (via `nowWallTimeInZone`) ← (verify: rejects missing fields, start≥end,
      and past start with inline errors; issues no request on failure)
- [x] 3.5 Submit via `useScheduleMeeting`; show backend problem detail inline
      and keep modal open on error; keep edit branch behavior unchanged ←
      (verify: success creates SCHEDULED meeting and closes; backend problem
      stays inline)
- [x] 3.6 Set instant flow `zoneId: resolveUserTimeZone(currentUser.timeZone)`
      in `src/components/shared/StartInstantMeetingModal.tsx` (replace
      `undefined`)

## 4. Tests

- [x] 4.1 Unit test `resolveUserTimeZone` — valid IANA passthrough, invalid
      value fallback, missing value fallback (spec: Meeting time zone resolved
      from the user profile)
- [x] 4.2 Unit test `getCurrentJiraUser` maps `/myself` `timeZone` into
      `ProjectMember.timeZone`
- [x] 4.3 Unit test `buildScheduleMeetingPayload` — invitees carry
      `email`/`accountId`/`displayName`, empty list when none, `timeRange`
      start/end present, `zoneId` from profile, and NO `host` (spec: Scheduled
      invitees carry frontend-resolved identity; Meeting time zone from profile)
- [x] 4.4 Unit test `scheduleMeeting` via mocked `requestRemote` — correct
      remote/path/method/body, no `X-Tenant-ID`/`X-Account-Id` headers, maps
      snapshot on success, surfaces problem+json without mock fallback (spec:
      Scheduled meeting creation through the backend)
- [x] 4.5 Verify instant payload now carries the profile-resolved `zoneId`
      (extend/adjust existing instant payload test) (spec: Meeting time zone
      resolved from the user profile)

## 5. Verification

- [x] 5.1 From `app/`: run `pnpm --filter smiski-ui test`
- [x] 5.2 From `app/`: run `pnpm --filter smiski-ui typecheck`
- [x] 5.3 From `app/`: run `pnpm lint`
- [x] 5.4 From `app/`: run `pnpm --filter smiski-ui build` ← (verify:
      type-check + Vite build pass with no schedule-flow regressions)
