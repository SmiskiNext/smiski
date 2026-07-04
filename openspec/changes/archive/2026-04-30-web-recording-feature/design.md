# Context

The web meeting room (`frontends/web/src/components/meeting/index.tsx`)
currently holds recording state as a plain `useState(false)`, making it
impossible for non-host participants to know whether recording is active and
unable to properly gate the recording start action. The backend surfaces
recording state through LiveKit room composite egress webhooks, which the server
handles by updating room metadata in the form `{"recording": true/false}`.
Android already consumes this; web needs to catch up.

## Goals / Non-Goals

**Goals:**

- Make recording state authoritative by deriving it from LiveKit room metadata
  (`RoomMetadataChanged` event)
- Provide a proper four-state machine (`idle | starting | recording | stopping`)
  to represent the full lifecycle
- Expose recording controls exclusively to the host with visual gating during
  transitions
- Show all participants a visible indicator when recording is active
- Surface recording transition notifications with auto-dismiss banners
- Require explicit host confirmation before starting a recording

**Non-Goals:**

- Implementing a playback UI (covered by `recording-playback` capability)
- Recording quality controls or egress configuration
- Persisting recording history on web (the `listMeetingRecordings` SDK call is
  for viewing recorded files, not controlling recording)
- Changes to the backend recording pipeline

## Decisions

**1. State machine anchored to room metadata, not local state**

LiveKit room metadata is the authoritative source of truth for recording state
because it reflects what the backend is actually doing (room composite egress).
Polling or local flags can drift from reality. The `useRecordingState` hook
derives state from `RoomEvent.RoomMetadataChanged`, making all participants see
the same authoritative value. Local machine state (`starting`, `stopping`)
tracks pending API calls that will eventually be confirmed or denied by
metadata.

**2. Confirmation dialog only on start, not stop**

Starting a recording is an intrusive action that notifies all participants and
may have legal/privacy implications. Stopping is a low-stakes undo. The
confirmation dialog gates the start action with an explicit prompt; stopping
executes immediately with an inline error path if the API call fails.

**3. Toolbar record button replaces dropdown item**

Moving recording to a direct toolbar button (between Layout Picker and More
dropdown) gives it prominent, one-click access for hosts, removes it from the
crowded More menu, and enables transition-locked visual states that are
impossible on a dropdown menu item. The loading and active recording states are
communicated clearly without entering a menu.

**4. Recording indicator in header (before ConnectionIndicator)**

All participants need to know when they are being recorded for compliance and
trust reasons. Placing it in the header next to connection status ensures
constant visibility without adding clutter to the main toolbar. The pulsing dot
pattern matches the `ConnectionIndicator` pattern for consistency.

**5. Auto-dismiss banners instead of persistent toasts**

Recording transition notifications are temporary by nature. Auto-dismissing them
after 8 seconds (started) or 5 seconds (stopped) prevents alert fatigue while
still giving participants a cue. The started-banner uses `aria-live="assertive"`
for accessibility; stopped uses `polite`.

**6. Separate hook for state, components for UI**

The state machine logic lives entirely in `useRecordingState` (reusable,
testable). Each UI piece is a separate component. This keeps the meeting shell
(`index.tsx`) from growing unwieldy and mirrors the pattern used by
`useMeetingChat`.

## Risks / Trade-offs

- **[Risk] Metadata delay after API call**: There is a window between
  `startRecording()` / `stopRecording()` API call and LiveKit reflecting the new
  metadata (up to several seconds depending on webhook latency). During
  `starting` / `stopping`, the button is disabled and shows a spinner to prevent
  double-clicks.
- **[Risk] Webhook delivery failure**: If the backend fails to update metadata,
  the web client stays in `starting` / `stopping` indefinitely. The
  `useRecordingState` hook should implement a timeout (10 seconds) that falls
  back to the previous stable state and sets an error.
- **[Risk] Race between metadata updates and component unmount**: The
  `useEffect` clean-up function detaches the room listener. If the metadata
  update fires during unmount, the setter will be called after the hook is torn
  down. `isMountedRef` guards against this (same pattern as `useMeetingChat`).
- **[Trade-off] No persistent error state for stop failure**: Stop failures
  surface as a banner above the toolbar rather than a modal dialog. This is less
  disruptive than a modal but may be missed if the user scrolls past it. It
  mirrors the `LeaveDialog` inline error pattern used for consistency.
- **[Trade-off] Recording indicator shown to hosts when not recording**: The
  indicator only renders when `recordingState === 'recording'`, so hosts see it
  only during active recording (same as participants). The host's control is the
  toolbar button itself.
