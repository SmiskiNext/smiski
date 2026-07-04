# Why

The web meeting room currently stops at a static mockup after a user joins,
which blocks the core product promise of real-time video collaboration on web.
This change is needed now because the backend LiveKit infrastructure, generated
web SDK clients, and Android reference implementation are already in place, so
the web client can reach functional parity for the primary host and participant
meeting experience.

## What Changes

- Replace the static web meeting room participant mockups with a real
  LiveKit-powered conferencing experience driven by room credentials already
  stored in session storage.
- Render real participant media, camera-off fallbacks, active speaker states,
  and responsive grid layouts for 1:1 through larger meetings.
- Add a floating self-view preview, meeting connection status indicator, elapsed
  call timer, and reconnection messaging in the meeting shell.
- Redesign the meeting toolbar into a floating pill with icon actions, tooltips,
  leave confirmation, layout selection, and host-only recording access.
- Add host waiting room management on web, including pending-request counts,
  approve/deny actions, approve-all, and SSE-driven live updates.
- Support multiple meeting layouts on web including auto, tiled, spotlight, and
  sidebar modes, with responsive sidebar behavior on smaller screens.
- Preserve existing chat and settings flows while integrating them into the live
  meeting room shell.

## Capabilities

### New Capabilities

- `web-live-meeting-room`: A real-time web meeting room that connects to
  LiveKit, renders participant media, exposes meeting controls and layouts, and
  supports host waiting-room operations.

### Modified Capabilities

- None.

## Impact

- Affected frontend area: `frontends/web/src/components/meeting/`,
  `frontends/web/src/components/shared/app-header.tsx`, and new meeting-specific
  hooks/components.
- New frontend dependencies: `livekit-client` and `@livekit/components-react`.
- Existing generated web SDK APIs will be used for waiting room, recording,
  meeting detail, and settings flows.
- Integrates with existing backend LiveKit rooms, host-only SSE event streams,
  and meeting authorization/session-storage join state.
