## [2026-04-27] Round 1 (from apply auto-verify)

### Verifier

- Fixed: Removed unused `case 'auto':` before `default:` in
  `use-meeting-layout.ts` switch statement
- Fixed: Replaced non-null assertions `request.id!` with `request.id ?? ''` in
  `waiting-room-sheet.tsx`
- Fixed: Removed unused `useRef` import from `index.tsx`
- Fixed: Removed unused `locale` and `t` variables from `MeetingBootstrap`
  function in `index.tsx`
- Fixed: Removed unused `useIsSpeaking as useParticipantSpeaking` and
  `useParticipantTracks` imports from `participant-grid.tsx`
- Fixed: Changed `<div role="region">` to `<section>` in `self-view.tsx` for
  valid ARIA landmark usage
- Fixed: Biome auto-sorted imports across `index.tsx`, `sidebar.tsx`,
  `self-view.tsx`, `participant-tile.tsx`, `participant-grid.tsx`
- Fixed: Biome updated `leave-dialog.tsx` to use `useRoomContext()` instead of
  `useLocalParticipant()` for room disconnect
- Fixed: Removed unused `LayoutOption.labelKey` field and simplified
  `LAYOUT_OPTIONS` type in `layout-picker.tsx`

## [2026-04-27] Round 2 (from verification fix pass)

### Verifier

- Fixed: Added host-only recording toggle button to more-actions dropdown in
  `toolbar.tsx`, gated behind `isHost` prop. Added `isRecording` and
  `onToggleRecording` props to `MeetingToolbarProps`. Added `Square` icon and
  `DropdownMenuSeparator` imports.
- Fixed: Added `startRecording` and `stopRecording` SDK calls in `index.tsx`.
  Added `isRecording` state with `handleToggleRecording` async function using
  `useCallback`. Wired props to `MeetingToolbar`.
- Fixed: Added `controlStartRecording` and `controlStopRecording` i18n keys to
  `en.json` and `vi.json`.
- Fixed: Added `.catch()` handler to `Promise.all()` in `MeetingBootstrap` so
  API failures degrade gracefully (host features disabled, not crash).
- Fixed: After successful approve/deny/approveAll mutations in
  `use-waiting-room.ts`, `loadRequests()` is now called on both success and
  error paths to keep the list in sync with the API.
- Fixed: Updated `selectPromoted` in `participant-grid.tsx` to prefer pinned
  participant, then active speaker (first remote speaking participant), then
  first remote participant as fallback. Added `useSpeakingParticipants` hook
  from `@livekit/components-react`.
- Fixed: Changed `aria-live='assertive'` to `aria-live='polite'` on reconnecting
  banner in `index.tsx`.
- Fixed: Added `use-call-timer.test.ts` with 6 tests covering MM:SS formatting,
  H:MM:SS formatting, digit padding, interval increments, and cleanup on
  unmount.
- Fixed: Added `use-meeting-layout.test.ts` with 14 tests covering mode
  switching, pinned identity tracking, and `deriveColumnCount` for all layout
  modes and viewport breakpoints.
