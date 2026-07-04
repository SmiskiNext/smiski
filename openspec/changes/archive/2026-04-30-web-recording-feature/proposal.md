# Why

The web meeting room lacks recording controls and state visibility, leaving web
participants and hosts without feature parity against the Android client. The
backend LiveKit composite egress recording pipeline and the generated SDK
endpoints are already in place; the web app needs to expose them.

## What Changes

- Introduce `useRecordingState` hook that subscribes to LiveKit
  `RoomMetadataChanged` events and drives a four-state machine
  (`idle | starting | recording | stopping`) backed by the existing
  `startRecording` / `stopRecording` SDK calls.
- Add a `RecordingIndicator` component (pulsing red dot + "REC" label) visible
  to all participants in the meeting header when recording is active.
- Promote the recording toggle from the More dropdown to a dedicated
  `ToolbarIconButton` on the toolbar (host-only), with four distinct visual
  states and a disabled state during transitions.
- Add a `RecordingConfirmDialog` that gates the start action behind a
  confirmation step for hosts; stop executes immediately without a dialog.
- Add a `RecordingBanner` that appears below the header on recording start and
  stop transitions, with appropriate auto-dismiss timings and accessibility
  roles.
- Add twelve new i18n keys to `en.json` and `vi.json` covering all new UI
  strings.
- Wire all pieces together in the meeting shell (`index.tsx`), replacing the
  current `useState(false)` approach.

## Capabilities

### New Capabilities

- `web-recording-controls`: Host controls to start and stop meeting recording
  from the web meeting room, including state machine, confirmation dialog,
  toolbar button with transition states, and error handling.
- `web-recording-visibility`: Recording presence indicator and notification
  banner visible to all meeting participants on the web client, driven by
  LiveKit room metadata.

### Modified Capabilities

- `web-live-meeting-room`: The meeting shell gains recording state management, a
  new header slot for the recording indicator, and a banner notification slot;
  toolbar props change to carry `recordingState` instead of `isRecording`.

## Impact

- **Modified files**: `frontends/web/src/components/meeting/index.tsx`,
  `frontends/web/src/components/meeting/toolbar.tsx`,
  `frontends/web/src/messages/en.json`, `frontends/web/src/messages/vi.json`
- **New files**: `frontends/web/src/hooks/use-recording-state.ts`,
  `frontends/web/src/components/meeting/recording-indicator.tsx`,
  `frontends/web/src/components/meeting/recording-confirm-dialog.tsx`,
  `frontends/web/src/components/meeting/recording-banner.tsx`
- **Dependencies**: `livekit-client` (already used), generated SDK (no changes),
  existing Tailwind semantic tokens
- **No API changes**: backend and SDK are unchanged; this is a pure frontend
  addition
