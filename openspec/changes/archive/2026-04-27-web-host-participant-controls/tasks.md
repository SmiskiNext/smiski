# Tasks

## 1. Regenerate the web moderation SDK

- [x] 1.1 Run `openapi-ts` with `frontends/web/openapi-ts.config.ts` and confirm
      the generated client exports `muteAllParticipants` and
      `muteParticipantTrack`
- [x] 1.2 Review and keep the required generated SDK updates in
      `frontends/web/src/generated/` and any related lockfile or package
      metadata changes ← (verify: generated output includes the two moderation
      operations and no hand-written API shim is needed)

## 2. Wire host-aware moderation data into the meeting room

- [x] 2.1 Extend `ParticipantViewModel` with optional role metadata and update
      meeting-room participant mapping to mark the participant matching `hostId`
      as `HOST`
- [x] 2.2 Add host-only moderation callbacks in
      `frontends/web/src/components/meeting/index.tsx` that call the generated
      mute-all and mute-track SDK functions for the active meeting
- [x] 2.3 Pass `isHost`, `meetingId`, `onMuteMic`, `onMuteCamera`, and
      `onMuteAll` into `MeetingSidebar` while preserving the existing non-host
      read-only People tab flow ← (verify: host resolution, callback wiring, and
      sidebar props match the design and no mute controls appear for local or
      host rows)

## 3. Implement host participant controls in the People tab UI

- [x] 3.1 Add the sticky host-only mute-all banner in
      `frontends/web/src/components/meeting/sidebar.tsx` with idle, loading,
      transient success, and recoverable error states
- [x] 3.2 Add always-visible inline microphone and camera mute buttons for
      moderable participant rows, including tooltips and per-button loading
      indicators
- [x] 3.3 Preserve the existing read-only row presentation for non-host users
      and for protected rows while keeping participant media status reactive to
      LiveKit updates instead of optimistic toggles ← (verify: host-only UI
      conditions, loading states, and reactive muted-state rendering match the
      spec scenarios)

## 4. Localize and test the moderation experience

- [x] 4.1 Add the new `meetingRoom` moderation i18n keys to
      `frontends/web/src/messages/en.json` and
      `frontends/web/src/messages/vi.json`
- [x] 4.2 Add or update focused frontend tests for sidebar participant
      moderation rendering and any extracted moderation state helper or hook
      behavior ← (verify: tests cover host vs non-host rendering, host-row
      suppression, and mute action loading or failure feedback)
