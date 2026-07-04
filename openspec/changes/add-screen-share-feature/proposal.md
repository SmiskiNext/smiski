## Why

The meeting system already accepts `allowScreenShare` in its settings contract
and already filters LiveKit token sources at join time. However the feature is
unreachable end-to-end: the web meeting toolbar has no screen-share button, the
Android in-call screen shows a "feature coming soon" snackbar, and the existing
runtime permission-sync handler ignores per-source filtering — so a host who
flips `allowScreenShare` mid-meeting cannot actually grant or revoke participant
screen-share publishing without forcing rejoins. This change delivers a working
host-gated screen-share flow on Web and Android with real-time enforcement,
completing the contract that `livekit-participant-permissions` and
`meeting-settings-replacement-api` already promise.

## What Changes

- Add a screen-share control to the web meeting toolbar wired to LiveKit's local
  participant `setScreenShareEnabled` API, gated by participant permissions
  (host always allowed; participant allowed when `allowScreenShare = true`;
  guest never allowed).
- Replace the placeholder Android `onScreenShareClicked` snackbar with a working
  flow that requests `MediaProjection` consent, runs a foreground service of
  type `mediaProjection`, and publishes the screen track via LiveKit Android
  SDK.
- Render remote screen-share tracks on both clients, automatically
  spotlight-pinning the active sharer (one-sharer-at-a-time policy enforced
  client-side).
- **BREAKING (internal port)**: Extend `ParticipantGrants` with an
  `allowedSources` list and update `LiveKitPort.updateParticipantPermissions`
    - `LiveKitAdapter.toPermission` to set
      `ParticipantPermission.canPublishSources` from those grants, so
      source-level filtering survives runtime permission updates. Existing
      callers in the codebase are updated; no external API surface is affected.
- Extend `MeetingSettingsChangedHandler` (or its replacement) so that when
  `allowScreenShare` flips from true to false on a LIVE meeting, the system also
  mutes any active `screen_share` track published by PARTICIPANT identities.
- Add Android manifest entries for `FOREGROUND_SERVICE` and
  `FOREGROUND_SERVICE_MEDIA_PROJECTION` and declare the new
  `ScreenCaptureService`.

## Capabilities

### New Capabilities

- `web-screen-share-control`: web meeting-room toolbar control + remote tile
  rendering + auto-spotlight + single-sharer guard for screen sharing, gated by
  LiveKit-derived permissions.
- `android-screen-share-control`: Android in-call screen-share initiation
  (MediaProjection consent + foreground service), local-publish flow, remote
  tile rendering, single-sharer guard, gated by LiveKit-derived permissions.

### Modified Capabilities

- `livekit-participant-permissions`: extend the runtime permission sync contract
  so source-level publish filtering (`canPublishSources`) is applied alongside
  the existing boolean grants, and so a runtime drop of `allowScreenShare`
  revokes any in-flight `screen_share` publication for PARTICIPANT identities.

## Impact

- **Backend** (`services/meeting-management`): `ParticipantGrants`,
  `LiveKitPort`, `LiveKitAdapter`, `MeetingSettingsChangedHandler`, plus their
  unit/integration tests. No HTTP API contract changes; OpenAPI regeneration is
  not required.
- **Web** (`frontends/web`): `components/meeting/toolbar.tsx`,
  `components/meeting/index.tsx`, `components/meeting/participant-tile.tsx`, new
  `hooks/use-screen-share.ts`, message bundles `messages/en.json` and
  `messages/vi.json`.
- **Android** (`frontends/android-app`):
  `domain/repository/LiveKitRepository.java`,
  `data/repository/LiveKitRepositoryImpl.java`, new
  `presentation/videocall/ScreenCaptureService.java`,
  `presentation/videocall/ActiveCallFragment.java`,
  `presentation/videocall/MeetingActionsBottomSheet.java`,
  `presentation/videocall/CallViewModel.java`,
  `presentation/videocall/VideoGridAdapter.java`,
  `app/src/main/AndroidManifest.xml`, `res/values/strings.xml`,
  `res/values-vi/strings.xml`.
- **Dependencies**: no new third-party libraries — uses the LiveKit SDKs already
  pinned for Web (`livekit-client`, `@livekit/components-react`) and Android
  (`io.livekit:livekit-android`).
- **Out of scope**: shared screen audio UI controls (grant only), annotation
  tooling, picture-in-picture for screen tiles, recording-side handling of
  screen layouts, granular per-participant share permission.
