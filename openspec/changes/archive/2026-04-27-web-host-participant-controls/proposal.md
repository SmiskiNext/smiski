# Why

The web meeting room currently exposes participant presence and media status but
does not let hosts moderate participants the way Android already does. Adding
host participant management controls now closes a cross-platform moderation gap
using backend endpoints that already exist and supports live meetings without
requiring backend changes.

## What Changes

- Regenerate the web OpenAPI SDK so the existing meeting moderation endpoints
  for muting all participants and muting an individual participant track are
  available in `frontends/web/src/generated/`.
- Extend the web meeting participant view model and sidebar wiring so host state
  and host participant identity are available when rendering the People tab.
- Add host-only moderation UI in the meeting sidebar People tab, including a
  sticky “Mute All” action and per-participant microphone and camera mute
  buttons with loading, success, and error handling.
- Preserve the current read-only participant list experience for non-host users
  while adding the new moderation copy to English and Vietnamese translations.
- Add focused frontend tests for participant-controls rendering and any
  extracted state-management helpers or hooks.

## Capabilities

### New Capabilities

- `web-host-participant-controls`: Host-only participant moderation controls in
  the web meeting room People tab, including mute-all and per-participant track
  muting.

### Modified Capabilities

- `web-live-meeting-room`: Expand the web meeting room requirements to support
  host moderation controls, host-aware participant rows, and moderation feedback
  states in the sidebar People tab.

## Impact

- Affected frontend code in `frontends/web/src/components/meeting/`, generated
  SDK files in `frontends/web/src/generated/`, and meeting-room translations in
  `frontends/web/src/messages/`.
- Uses existing backend moderation endpoints `participants:muteAll` and
  `participants/{identity}:muteTrack`; no backend API contract changes are
  required.
- Requires `openapi-ts` regeneration from `openapi/unified-openapi.yaml` using
  `frontends/web/openapi-ts.config.ts`.
- Adds or updates frontend tests for sidebar behavior and any new moderation
  state helpers.
