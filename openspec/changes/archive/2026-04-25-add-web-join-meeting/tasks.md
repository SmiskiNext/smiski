# Tasks

## 1. Route and entry-point wiring

- [x] 1.1 Add the public guest join route at
      `src/app/[locale]/join/[code]/page.tsx` and render the shared join-meeting
      container in guest mode.
- [x] 1.2 Update `middleware.ts` so `/join` is treated as a public path and
      bypasses workspace auth enforcement.
- [x] 1.3 Update `src/components/home/join-form.tsx` and
      `src/components/workspace-home-screen.tsx` so guest joins navigate to
      `/{locale}/join/{code}` and authenticated joins pass the meeting code into
      `/workspace/green-room`. ← (verify: both entry points route to the correct
      page for guest vs authenticated users, and guest access is not blocked by
      middleware)

## 2. Shared join-meeting state and API orchestration

- [x] 2.1 Create `src/components/join-meeting/use-join-meeting.ts` with a
      reducer-backed state machine for lookup, password gating, request
      submission, waiting approval, denial, expiration, approval, and error
      recovery.
- [x] 2.2 Implement meeting lookup and request submission in the hook using
      `getMeetingByShortCode` and `requestJoin`, including meeting-id caching,
      mode-aware display name handling, tab-scoped `deviceId` storage in
      `sessionStorage`, and error normalization.
- [x] 2.3 Implement pending-approval SSE subscription with native `EventSource`,
      terminal event handling, and exponential-backoff retries at 1s, 2s, and
      4s. ← (verify: state transitions exactly match the design,
      password-required meetings do not call `requestJoin` early, and pending
      requests stop or retry SSE connections correctly)

## 3. Join UI components and green-room integration

- [x] 3.1 Create `src/components/join-meeting/join-form.tsx` for the shared
      pre-join form with meeting code, guest display name, password prompt, and
      existing mic/video controls.
- [x] 3.2 Create `src/components/join-meeting/waiting-dialog.tsx` and
      `src/components/join-meeting/index.tsx` to bind reducer state to the UI,
      surface inline errors and toast/dialog feedback, and trigger meeting-room
      handoff on approval.
- [x] 3.3 Replace the mock implementation in
      `src/components/green-room-screen.tsx` with the real shared join-meeting
      container in authenticated mode. ← (verify: authenticated users see
      prefilled display name behavior, guests are prompted for display name, and
      approved flows navigate with token handoff semantics)

## 4. Localization and end-to-end handoff validation

- [x] 4.1 Add the `joinMeeting` translation namespace to `src/messages/en.json`
      with all labels, waiting-room copy, and mapped error messages required by
      the new flow.
- [x] 4.2 Add the `joinMeeting` translation namespace to `src/messages/vi.json`
      with localized equivalents for the same strings.
- [x] 4.3 Validate the full browser flow for lookup, password-required join,
      denial messaging, waiting-room approval or expiry, retryable network
      failure, and `/workspace/meeting-room` credential handoff for both guest
      and authenticated entry points. ← (verify: English and Vietnamese render
      the correct copy and the meeting-room page can receive approved token data
      for both user types)
