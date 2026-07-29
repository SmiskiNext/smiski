## Context

The Custom UI (`app/static/smiski-ui`) ships two create-meeting flows. The
instant flow (`StartInstantMeetingModal`) is already Ant Design-based and calls
the `meet` backend through Forge Remote (`requestRemote('meet-backend', ...)`),
with FIT-only auth and problem+json error handling. The schedule flow
(`ScheduleMeetingModal`) still uses the legacy `components/ui` kit and native
`<input type=date|time>` controls, and its create path is wired to the in-memory
mock (`useScheduleMeeting` → `mockDb.scheduleMeeting`). The dead
`scheduleMeeting()`/`scheduleMeetingRequest` path in `api/meetings.ts` +
`api/mappers.ts` goes through `apiRequest` and is not invoked by any hook.

The backend contract already exists as the `create-schedule-meeting` spec:
`POST /api/1/meetings:schedule` with `title`, `description`, `issueLink`,
`settings`, `timeRange {startTime,endTime}`, top-level IANA `zoneId`, optional
`invitees[{email,accountId,displayName}]`, and NO `host` object (host is
resolved from the `X-Account-Id` header at the gateway). Per api-convention, the
schedule action is a `POST` to the `:schedule`-suffixed collection path and
returns `201 Created` with the meeting snapshot as the body (no envelope).

Constraints:

- Forge UI-module remotes take the request path from `requestRemote`, not from
  `manifest.yml` `endpoint.route.path`; the schedule path reuses the already
  declared `meet-backend` remote, so no manifest change is needed.
- Only UI Kit-free Ant Design + `@forge/bridge` are available; instant flow is
  the reference implementation pattern.
- The `read:jira-user` scope is already granted, so `/myself` (with `timeZone`)
  is readable.

## Goals / Non-Goals

**Goals:**

- Rebuild the schedule create form with Ant Design, matching the instant modal.
- Wire scheduled creation to the real `meet` backend via Forge Remote, with a
  pure, unit-testable payload builder and no mock fallback.
- Capture start time, end time, and time zone with client-side validation (start
  < end, start not in past for the zone).
- Source invitees from the workspace-user picker so each carries real identity.
- Default the meeting time zone from the invoking user's Jira profile for BOTH
  the instant and scheduled flows.

**Non-Goals:**

- The edit/update-meeting branch of `ScheduleMeetingModal` (stays on the mock).
- Invitation-email delivery (backend/out of scope).
- Any `manifest.yml`, backend, or dependency change.
- Reworking the mock db or standalone `vite dev` create capability (BREAKING:
  scheduled create requires a reachable backend, same as instant).

## Decisions

### D1: Reuse the instant Forge Remote pattern for schedule

Add `SCHEDULE_MEETING_PATH = '/api/1/meetings:schedule'` and rewrite
`scheduleMeeting()` to call
`requestRemote(MEET_REMOTE_KEY, { path, method:'POST', headers, body: JSON.stringify(buildScheduleMeetingPayload(input)) })`,
reusing `readProblem`/problem-detail error shaping from the instant path and
mapping the response with `meetingFromBackend`. `useScheduleMeeting` calls this
directly.

- Rationale: proven, consistent with instant; FIT-only auth is already correct.
- Alternative (rejected): keep `apiRequest`/resolver transport — diverges from
  instant, requires resolver wiring, and the existing path is dead code.

### D2: Pure `buildScheduleMeetingPayload(input)` builder

Mirror `buildInstantMeetingPayload`: a pure function producing the exact backend
body — `title`, `description` (defaults to title when blank), `issueLink`,
default `settings`, `timeRange {startTime,endTime}`, `zoneId`, `invitees[]` —
and NO `host`. Keeps the payload contract unit-testable without `@forge/bridge`.

- Rationale: matches the instant test strategy (`meetings.instant.test.ts`).
- Alternative (rejected): reuse `scheduleMeetingRequest` mapper — it synthesizes
  fake invitee emails from accountIds and embeds organizer fields; incompatible
  with real identities and the header-based host contract. Retire it if unused.

### D3: Extend `ScheduleMeetingInput`

Add `endTime: string` (ISO) and `invitees: MeetingInviteeInput[]` (full
identity), retiring reliance on `participantAccountIds` for the create path.
`startTime` stays ISO. `zoneId` stays the resolved profile/zone value.

### D4: Start/end/zone controls and validation

Use Ant Design date/time selection for start and end, plus a `Select` time-zone
control seeded from the profile zone. Convert wall-clock date/time in the chosen
zone to a UTC ISO instant via the existing `zonedWallTimeToIso`. Validate
client-side: all three present, start < end, start ≥ now (in the chosen zone via
`nowWallTimeInZone`). Server remains authoritative (`MEETING_START_IN_PAST`,
`VALIDATION_ERROR`) and its problem detail is shown inline.

### D5: Time zone from Jira profile (instant + schedule)

Add `timeZone?: string` to `ProjectMember`; `getCurrentJiraUser` maps `/myself`
`timeZone`. Add `resolveUserTimeZone(profileZone?)` in `utils/datetime.ts`
returning the profile zone when it is a resolvable IANA id, else
`getLocalTimeZone()`. Schedule seeds its zone control from
`resolveUserTimeZone(currentUser.timeZone)`; instant passes
`zoneId: resolveUserTimeZone(currentUser.timeZone)` instead of `undefined`. Mock
`CURRENT_USER` gets a default `timeZone` for `vite dev`.

- Rationale: the profile zone is the host's intended zone; browser zone is only
  a fallback. Validating IANA resolvability avoids sending a bad `zoneId` the
  backend would reject.

### D6: Scope create only; edit stays on mock

`ScheduleMeetingModal` keeps its edit branch (`useUpdateMeeting` → mock)
unchanged; only the create branch is rewired. Avoids entangling this change with
the separate `update-meeting` capability.

## Flow

```mermaid
sequenceDiagram
    participant UI as ScheduleMeetingModal (Custom UI)
    participant Bridge as Forge Remote (requestRemote)
    participant BE as meet backend

    UI->>UI: validate start<end, start not past (zone)
    UI->>UI: buildScheduleMeetingPayload(input) (no host)
    UI->>Bridge: requestRemote('meet-backend', POST :schedule, body)
    Bridge->>BE: POST /api/1/meetings:schedule + Bearer FIT
    alt success
        BE-->>Bridge: 201 Created (meeting snapshot, SCHEDULED)
        Bridge-->>UI: response.ok, json
        UI->>UI: meetingFromBackend(json); close + onSubmitted
    else problem+json
        BE-->>Bridge: 4xx problem (VALIDATION_ERROR / MEETING_START_IN_PAST)
        Bridge-->>UI: response.ok=false, problem
        UI->>UI: show inline error; keep modal open
    end
```

## Risks / Trade-offs

- [Standalone `vite dev` can no longer create scheduled meetings] → Accepted;
  identical to the instant flow. Edit still works on the mock for local UI work.
- [`zonedWallTimeToIso` correctness across DST/offset edge cases] → Reuse the
  existing tested helper; add zone-resolution tests for `resolveUserTimeZone`.
- [Profile `timeZone` missing or non-IANA] → `resolveUserTimeZone` falls back to
  the browser zone; covered by tests.
- [Retiring `scheduleMeetingRequest` breaks `mappers.test.ts`] → Only remove if
  no longer referenced; update/remove the corresponding test in the same change.

## Open Questions

- None. Backend contract, transport, auth, and scope are all resolved.
