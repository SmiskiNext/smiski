## Context

LiveKit token issuance and runtime permission sync already exist in
`services/meeting-management`. Token issuance correctly maps the boolean
settings (`allowMicrophone`, `allowVideo`, `allowScreenShare`, `chatEnabled`) to
LiveKit `CanPublishSources`. However the runtime permission sync path
(`MeetingSettingsChangedHandler` → `LiveKitPort.updateParticipantPermissions` →
`LiveKitAdapter.toPermission`) only carries the three booleans (`canPublish`,
`canPublishData`, `canSubscribe`); the resulting `ParticipantPermission` proto
does not set `canPublishSources`, so a runtime flip of `allowScreenShare` cannot
remove the screen-share source from a participant who is already in the room.
The clients (Web, Android) currently have no UI for screen sharing — Web's
toolbar lacks the control, Android shows a "feature coming soon" snackbar in
`ActiveCallFragment.onScreenShareClicked`.

The LiveKit Web SDK (`livekit-client`, `@livekit/components-react`) and the
LiveKit Android SDK (`io.livekit:livekit-android`) both expose
`LocalParticipant.setScreenShareEnabled(...)`, automatic browser/system
permission prompts, and a server-driven `permissionsChanged` event on the local
participant. Android additionally requires a foreground service of type
`mediaProjection` (Android 10+ enforced; Android 14+ requires the explicit
`FOREGROUND_SERVICE_MEDIA_PROJECTION` permission).

Stakeholders: meeting hosts (control), participants (publish + view), guests
(view only). Existing capabilities affected: `livekit-participant-permissions`,
`meeting-settings-replacement-api`, `web-live-meeting-room`,
`android-videocall-shell`.

## Goals / Non-Goals

**Goals:**

- Hosts can toggle `allowScreenShare` and have the change take effect for
  participants who are already in the room (no rejoin).
- A participant whose token allows `screen_share` can start sharing on Web
  (browser `getDisplayMedia` picker) or Android (system MediaProjection
  consent), publishing a `screen_share` track via LiveKit.
- Remote screen-share publications are rendered on both clients and
  automatically promoted to the spotlight tile.
- Only one publisher at a time is exposed in the UI: while any participant
  publishes screen share, every other client disables its share button with a
  tooltip identifying the active sharer.
- When the host disables `allowScreenShare` mid-meeting, any
  PARTICIPANT-published `screen_share` track is muted server-side and the share
  button is hidden on participants' UI.

**Non-Goals:**

- Source-level screen-share **audio** UI (we keep the `screen_share_audio`
  source in the grant when allowed but expose no dedicated mute/start audio
  control).
- Annotation, drawing, whiteboard.
- Picture-in-picture or floating overlay for screen-share tiles.
- Recording-side composition tweaks for screen layouts (recording follows the
  default LiveKit layout as already configured).
- Granular per-participant share rights — `allowScreenShare` remains a single
  boolean on `MeetingSettings`.
- Server-enforced single-publisher policy — single-sharer is a UI/UX policy, not
  a security boundary.

## Decisions

### Decision 1: Use LiveKit-native `permissionsChanged` instead of adding a custom SSE event

The existing SSE manager streams join-request lifecycle events only. Rather than
introduce a new `meeting_settings_updated` SSE event for clients to re-evaluate
which controls to show, clients listen to LiveKit's local `permissionsChanged`
event (Web: `RoomEvent.ParticipantPermissionsChanged` on the local participant;
Android: `Room.Event.ParticipantPermissionsChanged` or the
`Participant.Event.PermissionsChanged` listener). This event is fired by LiveKit
when the server pushes a `ParticipantPermission` update. **Trade-off**: clients
are coupled to the LiveKit signalling channel for this UI gate, which is
acceptable because every screen-share UI control is already rendered inside the
`LiveKitRoom` provider.

**Alternatives considered:**

- Add `meeting_settings_updated` SSE event from `MeetingSseManager` — adds
  redundancy with what LiveKit already pushes and forces clients to reconcile
  two truth sources.
- Poll meeting settings — wasteful and laggy.

### Decision 2: Extend `ParticipantGrants` with `allowedSources`

Add an optional `List<String> allowedSources` to the `ParticipantGrants` record.
Only used when the grant is being applied via `updateParticipantPermissions`.
`LiveKitAdapter.toPermission(...)` reads that list and sets
`ParticipantPermission.canPublishSources` on the proto using LiveKit's
`TrackSource` enum (`MICROPHONE`, `CAMERA`, `SCREEN_SHARE`,
`SCREEN_SHARE_AUDIO`).

**Backward compatibility:** factories `speaker()`, `viewer()`, `observer()`
default to an empty list (which the proto interprets as "all sources allowed
when `canPublish=true`"). Production callers (`MeetingSettingsChangedHandler`)
switch to a new factory `fromSettingsForRuntimeSync(settings, role)` that fills
`allowedSources` from the same logic as `LiveKitAdapter.buildAllowedSources` so
token issuance and runtime sync stay in lockstep.

**Alternatives considered:**

- Pass settings down to the adapter — leaks domain into infrastructure.
- Separate method `updateParticipantSources` — splits one logical update into
  two LiveKit RPC calls.

### Decision 3: Mute outstanding screen-share tracks when `allowScreenShare` flips false

In `MeetingSettingsChangedHandler`, when the new settings disable
`allowScreenShare` for a LIVE meeting, after applying
`updateParticipantPermissions` to PARTICIPANT identities, call the existing
`LiveKitPort.muteParticipantTrack(roomName, identity, "screen_share")` for each
PARTICIPANT identity. Best-effort; ignore `TrackNotFound` and
`LiveKitParticipantNotFound` errors. HOST identities are not muted (host keeps
share rights). GUEST identities cannot publish screen share, so no mute attempt
is required.

**Alternatives considered:**

- Rely on LiveKit to auto-mute when permissions change — LiveKit revokes
  _future_ publish rights but does not mute already-published tracks; tested
  behavior in this codebase confirms this gap.
- Disconnect participants — too disruptive.

### Decision 4: Auto-spotlight via existing `pinnedIdentity` (Web) and adapter promotion (Android)

Web's `MeetingRoomContent` already supports a `pinnedIdentity` that the
`SpotlightLayout`/`SidebarLayout` honour. Add a derived value: when any remote
participant has a `Track.Source.ScreenShare` publication, set `pinnedIdentity`
to that participant's identity (preferring the local participant if local is
sharing). On Android, `VideoGridAdapter` accepts a "promoted" participant id;
when a `screen_share` track is detected on any participant in
`LiveKitRepository.getParticipants()`, the adapter promotes it into a single
large tile and the rest become thumbnails (existing layout modes already support
spotlight).

**Trade-off:** if the user has manually pinned someone else, screen share
overrides their pin. We accept this because screen share is the rarer,
higher-signal event; manual pin can be re-applied after share ends.

### Decision 5: One-sharer-at-a-time guard is client-side only

In each client, `useScreenShare`/`CallViewModel` exposes:

- `isLocalSharing: boolean` — local participant has an active `screen_share`
  publication.
- `activeSharer: { identity, displayName } | null` — first remote participant
  with `screen_share`.
- `canShareScreen: boolean` — derived from local LiveKit permissions (host
  always true, participant true iff `screen_share` in `canPublishSources`, guest
  false) AND `activeSharer == null` (unless `isLocalSharing`).

**Trade-off:** a brief race window (~<1s) where two clients begin publishing
simultaneously will produce two screen-share tiles; we accept this because the
host can resolve via `muteParticipantTrack` and a server-side mutex is out of
scope.

### Decision 6: Android ScreenCaptureService

Create `presentation/videocall/ScreenCaptureService.java` extending `Service`
with `onStartCommand` returning `START_NOT_STICKY`. The service:

1. Creates a notification channel `screen_capture` (NotificationManager,
   IMPORTANCE_LOW).
2. Calls
   `startForeground(NOTIFICATION_ID, notification, FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)`
   before the LiveKit SDK begins capture.
3. Stops itself when `LiveKitRepository` reports the local screen-share track
   ended.

`AndroidManifest.xml` declares:

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION" />
<service
    android:name=".presentation.videocall.ScreenCaptureService"
    android:foregroundServiceType="mediaProjection"
    android:exported="false" />
```

`ActiveCallFragment` registers `ActivityResultLauncher` for
`MediaProjectionManager.createScreenCaptureIntent()`, starts
`ScreenCaptureService` first (Android 14+ requirement: foreground service of
that type must be running before MediaProjection permission is consumed by
LiveKit), then passes the consent `Intent` through
`CallViewModel.toggleScreenShare(Intent)` →
`LiveKitRepository.setScreenShareEnabled(true, Intent)`.

**Alternatives considered:**

- Use the LiveKit demo app's helper service directly — not present in current
  dependencies; safer to write our own.
- Skip foreground service on older API levels — would crash on Android 10+.

## Risks / Trade-offs

- **Race condition for simultaneous sharers** → accept; visible to host who can
  mute via existing `muteParticipantTrack` API.
- **`canPublishSources` propagation gap during runtime sync** → fix in
  `LiveKitAdapter.toPermission`; covered by new tests.
- **Android foreground service notification visibility** → required by platform;
  cannot be hidden on Android 10+. Use neutral copy and do not expose meeting
  metadata in the notification text.
- **Browser tab switching during share** → relying on LiveKit/browser default;
  on browsers that stop the screen track when the source tab closes we propagate
  `track ended` → publication removed → UI returns to normal state.
- **Permission flip during in-flight publish** → handled by Decision 3 plus
  client `permissionsChanged` listener that re-evaluates `canShareScreen` and
  stops local publication if needed.
- **No backend API contract change** → no OpenAPI regeneration required.
- **`pinnedIdentity` override for screen share** → manual pin overridden;
  acceptable trade-off documented in Decision 4.

## Migration Plan

No data migration required — `MeetingSettings.allowScreenShare` and the LiveKit
adapter already exist. Deployment order:

1. Backend release — adds source-aware `ParticipantGrants`, propagation in
   `LiveKitAdapter`, screen-share mute on flip-down. Backward compatible because
   empty `allowedSources` reproduces today's behavior.
2. Web release — adds toolbar control + `useScreenShare` hook + tile rendering.
3. Android release — adds manifest entries, `ScreenCaptureService`, and in-call
   wiring.

Rollback: the change can be reverted to backend-only or per-platform without
breaking earlier-deployed components, because the new clients still work against
the old backend (they just can't react to runtime permission revocation as
accurately).

## Open Questions

None for this change — all five user decisions are locked in proposal and this
design.
