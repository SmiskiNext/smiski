# Tasks

## 1. LiveKit foundation and meeting bootstrap

- [x] 1.1 Add `livekit-client` and `@livekit/components-react` to
      `frontends/web/package.json`
- [x] 1.2 Update `frontends/web/src/components/meeting/index.tsx` to read
      persisted room credentials, guard missing session state, and mount the
      `LiveKitRoom` provider
- [x] 1.3 Define meeting-room state composition for connection status, host
      eligibility, and normalized participant view models used by the shell ←
      (verify: meeting route connects only with valid credentials, exposes real
      LiveKit room state, and fails safely when credentials are missing)

## 2. Participant rendering and responsive layouts

- [x] 2.1 Create `frontends/web/src/components/meeting/participant-tile.tsx` for
      live video, camera-off fallback, name overlay, and speaking indicator
      states
- [x] 2.2 Create `frontends/web/src/components/meeting/self-view.tsx` for the
      floating local preview with camera-on and camera-off variants
- [x] 2.3 Create `frontends/web/src/hooks/use-meeting-layout.ts` to manage
      layout mode, promoted participant selection, and responsive layout
      derivation
- [x] 2.4 Replace the static implementation in
      `frontends/web/src/components/meeting/participant-grid.tsx` with
      LiveKit-backed grid, spotlight, and sidebar layouts plus responsive column
      caps ← (verify: participant tiles, active speaker emphasis, self-view, and
      Auto/Tiled/Spotlight/Sidebar layouts all render correctly across
      participant counts and breakpoints)

## 3. Meeting shell status and navigation safety

- [x] 3.1 Create `frontends/web/src/hooks/use-call-timer.ts` with
      elapsed-seconds tracking and formatted duration output
- [x] 3.2 Create `frontends/web/src/components/meeting/connection-indicator.tsx`
      for accessible connection-state display and tooltip text
- [x] 3.3 Create `frontends/web/src/components/meeting/leave-dialog.tsx` that
      confirms exit, disconnects the LiveKit room, and returns the user to
      `/workspace`
- [x] 3.4 Update `frontends/web/src/components/shared/app-header.tsx` and the
      meeting shell to show the connection indicator, call timer, and
      reconnecting inline message ← (verify: header status colors, `aria-live`
      announcements, timer formatting, and leave-confirmation disconnect flow
      work end-to-end)

## 4. Toolbar and shell interaction redesign

- [x] 4.1 Redesign `frontends/web/src/components/meeting/toolbar.tsx` into a
      floating pill with icon-only controls, tooltips, host-only recording
      action, more-actions menu, and distinct end-call button
- [x] 4.2 Create `frontends/web/src/components/meeting/layout-picker.tsx` and
      wire it into the toolbar layout trigger
- [x] 4.3 Update `frontends/web/src/components/meeting/sidebar.tsx` to support
      collapsible or drawer behavior below the desktop breakpoint while
      preserving chat and settings access
- [x] 4.4 Reintegrate existing `chat.tsx` and `meeting-settings-dialog.tsx`
      flows into the live meeting shell without regressing current functionality
      ← (verify: toolbar actions, layout picker, sidebar responsiveness, chat
      access, and settings access behave correctly on desktop and narrow
      screens)

## 5. Host waiting room and realtime updates

- [x] 5.1 Create `frontends/web/src/hooks/use-waiting-room.ts` to load pending
      join requests, perform approve/deny/approve-all mutations, and manage SSE
      subscription recovery
- [x] 5.2 Create `frontends/web/src/components/meeting/waiting-room-sheet.tsx`
      with loading, error, empty, and populated host states plus pending-count
      badge support
- [x] 5.3 Integrate waiting-room controls into the meeting toolbar and container
      so they appear only for eligible hosts when waiting-room support is
      enabled
- [x] 5.4 Connect generated SDK functions and SSE event handling so pending
      counts and join-request lists refresh correctly on `join_request_created`,
      approval, denial, expiry, and retry paths ← (verify: host-only
      waiting-room UI stays in sync with backend state, SSE interruptions
      recover cleanly, and approve/deny flows update the list authoritatively)

## 6. Localization, resilience, and final validation

- [x] 6.1 Add next-intl translation keys for all new meeting-room labels,
      statuses, tooltips, dialogs, and waiting-room text
- [x] 6.2 Validate media rendering semantics such as `playsInline`, cover-fit
      behavior, remote mute rules, and camera-off fallbacks for local and remote
      participants
- [x] 6.3 Run project checks for the web frontend and fix any issues introduced
      by the meeting-room upgrade ← (verify: new strings are localized, media
      semantics match the spec, and the final implementation passes the
      project's lint/type/test checks)
