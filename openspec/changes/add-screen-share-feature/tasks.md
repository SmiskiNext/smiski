## 1. Backend — extend grants and propagate source-level permissions

- [x] 1.1 Add an optional `List<String> allowedSources` field to
      `../../../services/meeting-management/src/main/java/io/github/smiskinext/meetingmanagement/domain/model/valueobject/ParticipantGrants.java`
      with a defensive `List.copyOf` in the canonical constructor; default to an
      empty list in `speaker()`, `viewer()`, `observer()`.
- [x] 1.2 Add a static factory
      `ParticipantGrants.fromSettingsForRuntimeSync(MeetingSettings, ParticipantRole)`
      that fills `allowedSources` for PARTICIPANT using the same logic as
      `LiveKitAdapter.buildAllowedSources(MeetingSettings)` (microphone / camera
      / screen_share / screen_share_audio).
- [x] 1.3 Update
      `../../../services/meeting-management/src/main/java/io/github/smiskinext/meetingmanagement/infrastructure/livekit/LiveKitAdapter.java#toPermission`
      to call `setCanPublishSources(...)` on the
      `LivekitModels.ParticipantPermission` builder when
      `grants.allowedSources()` is non-empty, mapping strings to
      `LivekitModels.TrackSource` values (`microphone`→MICROPHONE,
      `camera`→CAMERA, `screen_share`→SCREEN_SHARE,
      `screen_share_audio`→SCREEN_SHARE_AUDIO).
- [x] 1.4 Reuse `LiveKitAdapter.buildAllowedSources` from
      `fromSettingsForRuntimeSync` (move helper to package-visible or
      duplicate-but-test) so token issuance and runtime sync produce identical
      lists.
- [x] 1.5 Update
      `../../../services/meeting-management/src/test/java/io/github/smiskinext/meetingmanagement/infrastructure/livekit/LiveKitAdapterTest.java`
      (or add it if missing) covering: (a) `toPermission` sets
      `canPublishSources` from `allowedSources`; (b) empty list omits the field
      for backward compatibility; (c) each source string maps to the correct
      `TrackSource` enum. ← (verify: ParticipantPermission proto contains
      canPublishSources entries for the screen_share cases)

## 2. Backend — runtime sync mutes outstanding screen-share track

- [x] 2.1 Update
      `../../../services/meeting-management/src/main/java/io/github/smiskinext/meetingmanagement/application/handler/MeetingSettingsChangedHandler.java`
      to switch from `ParticipantGrants.fromSettings(...)` to
      `ParticipantGrants.fromSettingsForRuntimeSync(...)` when computing grants
      for PARTICIPANT identities.
- [x] 2.2 In the same handler, when `oldSettings.allowScreenShare()` is true and
      `newSettings.allowScreenShare()` is false, after the permission update
      call
      `liveKitPort.muteParticipantTrack(roomName, identity,     "screen_share")`
      for each PARTICIPANT identity; treat `MeetingError.TrackNotFound` and
      `MeetingError.LiveKitParticipantNotFound` as success and log at debug
      level.
- [x] 2.3 Extend
      `../../../services/meeting-management/src/test/java/io/github/smiskinext/meetingmanagement/application/handler/MeetingSettingsChangedHandlerTest.java`
      (create if missing): (a) flipping `allowScreenShare` true→false on a LIVE
      meeting calls `muteParticipantTrack` exactly once per PARTICIPANT; (b)
      HOST/GUEST identities are skipped; (c) `TrackNotFound` does not abort the
      loop. ← (verify: handler still respects existing best-effort error
      handling pattern)
- [x] 2.4 Extend
      `../../../services/meeting-management/src/test/java/io/github/smiskinext/meetingmanagement/application/usecase/PutMeetingSettingsUseCaseTest.java`
      with a scenario that toggles `allowScreenShare` on a LIVE meeting and
      asserts the published `MeetingSettingsUpdatedEvent` includes
      `oldSettings`/`newSettings` with the screen-share boolean flipping
      (handler is exercised separately).
- [x] 2.5 Run `./services/gradlew -p services/meeting-management test` and
      `./services/gradlew -p services/meeting-management spotlessApply` until
      both pass. ← (verify: backend changes pass full meeting-management test
      suite end-to-end)

## 3. Web — `useScreenShare` hook

- [x] 3.1 Create `frontends/web/src/hooks/use-screen-share.ts` exporting
      `useScreenShare()` that returns
      `{ canShareScreen, isLocalSharing, activeSharer, toggle }`.
      Implementation: read `useLocalParticipant()`'s permissions and
      `permissions.canPublishSources`, scan `useRemoteParticipants()` for any
      participant with a publication of `Track.Source.ScreenShare`, derive
      `activeSharer` from the first such participant (preferring the local one
      when local is sharing).
- [x] 3.2 In the same hook, expose
      `canShareScreen = isHostRole || sourcesIncludeScreenShare` AND
      `(activeSharer === null || activeSharer.identity === localParticipant.identity)`.
- [x] 3.3 In `toggle()`, call
      `localParticipant.setScreenShareEnabled(!isLocalSharing)` and surface
      promise rejections via a returned error state for the caller to handle (do
      not throw).

## 4. Web — toolbar control

- [x] 4.1 Add a `MonitorUp`/`MonitorOff` icon import (lucide-react) to
      `frontends/web/src/components/meeting/toolbar.tsx`.
- [x] 4.2 Extend `MeetingToolbarProps` with `canShareScreen: boolean`,
      `isScreenSharing: boolean`, `activeSharerName: string | null`,
      `onToggleScreenShare: () => void`.
- [x] 4.3 Render a `ToolbarIconButton` for screen share between the
      camera/layout controls; hide it entirely when neither `canShareScreen` nor
      `isScreenSharing` is true; disable it (with tooltip naming
      `activeSharerName`) when another sharer is active.
- [x] 4.4 Add i18n keys `meetingRoom.controlScreenShare`,
      `meetingRoom.controlStopScreenShare`, `meetingRoom.screenShareInUse` to
      `frontends/web/src/messages/en.json` and
      `frontends/web/src/messages/vi.json`.

## 5. Web — wire toolbar + auto-spotlight + tile rendering

- [x] 5.1 In
      `frontends/web/src/components/meeting/index.tsx#MeetingRoomContent`,
      consume `useScreenShare()` and pass props through to `MeetingToolbar`.
- [x] 5.2 In the same component, derive an effective `pinnedIdentity` that
      prefers the active screen sharer's identity over the user's manual pin
      when a screen-share publication exists.
- [x] 5.3 Update `frontends/web/src/components/meeting/participant-tile.tsx` so
      that when the participant has a `Track.Source.ScreenShare` publication,
      the tile renders that track instead of the camera track (use `VideoTrack`
      component with the correct trackRef). Keep camera tile behavior unchanged
      for participants without screen share.
- [x] 5.4 Verify with `pnpm --dir frontends/web lint`,
      `pnpm --dir frontends/web build` that no type errors remain. ← (verify:
      end-to-end web build passes and toolbar/tile changes integrate without
      regressions)

## 6. Android — repository contract for screen share

- [x] 6.1 Extend
      `frontends/android-app/app/src/main/java/io/github/phunguy65/zms/domain/repository/LiveKitRepository.java`
      with
      `void setScreenShareEnabled(boolean enabled, @Nullable Intent     mediaProjectionConsent)`,
      `boolean isScreenShareEnabled()`,
      `@Nullable String getActiveScreenShareIdentity()`,
      `boolean canPublishScreenShare()`, plus listener methods on
      `RoomEventListener`:
      `onLocalPermissionsChanged(boolean canPublishScreenShare)` and
      `onRemoteScreenShareChanged(@Nullable String sharerIdentity,     @Nullable String displayName)`.
- [x] 6.2 Implement the new contract in
      `frontends/android-app/app/src/main/java/io/github/phunguy65/zms/data/repository/LiveKitRepositoryImpl.java`
      via
      `localParticipant.setScreenShareEnabled(enabled, mediaProjectionConsent, ...)`,
      and reflect state in the existing `startStatePolling` loop — detect (a)
      local screen-share publication change, (b) remote screen-share
      publications, (c) local permissions change — and notify the listener on
      the main thread.

## 7. Android — ScreenCaptureService and manifest entries

- [x] 7.1 Add to `frontends/android-app/app/src/main/AndroidManifest.xml`:
      `<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />`,
      `<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION" />`,
      and a `<service>` element for
      `.presentation.videocall.ScreenCaptureService` with
      `android:foregroundServiceType="mediaProjection"` and
      `android:exported="false"`.
- [x] 7.2 Create
      `frontends/android-app/app/src/main/java/io/github/phunguy65/zms/presentation/videocall/ScreenCaptureService.java`
      with: notification channel `screen_capture` (IMPORTANCE_LOW), an ongoing
      notification using neutral copy, and
      `startForeground(notificationId, notification,     ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)`
      invoked inside `onStartCommand` returning `START_NOT_STICKY`.
- [x] 7.3 Add static `start(Context)` and `stop(Context)` helpers on
      `ScreenCaptureService` for the fragment to call before/after the LiveKit
      publish call.
- [x] 7.4 Add strings to
      `frontends/android-app/app/src/main/res/values/strings.xml` and
      `frontends/android-app/app/src/main/res/values-vi/strings.xml`:
      `screen_capture_channel_name`, `screen_capture_notification_title`,
      `screen_capture_notification_text`, `cd_share_screen`,
      `cd_stop_share_screen`, `screen_share_in_use`. ← (verify: notification
      renders during share with neutral copy, no meeting metadata leaked)

## 8. Android — viewmodel state and consent flow

- [x] 8.1 Add to
      `frontends/android-app/app/src/main/java/io/github/phunguy65/zms/presentation/videocall/CallViewModel.java`:
      `LiveData<Boolean> canShareScreen`,
      `LiveData<Boolean> isLocalSharingScreen`,
      `LiveData<String> activeScreenSharerName`, plus method
      `toggleScreenShare(@Nullable Intent mediaProjectionConsent)` that
      delegates to the repository.
- [x] 8.2 Wire the new repository listener callbacks
      (`onLocalPermissionsChanged`, `onRemoteScreenShareChanged`) into
      `CallViewModel` to update those LiveData instances.
- [x] 8.3 In
      `frontends/android-app/app/src/main/java/io/github/phunguy65/zms/presentation/videocall/ActiveCallFragment.java`,
      register an `ActivityResultLauncher` for
      `MediaProjectionManager.createScreenCaptureIntent()`; replace the
      placeholder body of `onScreenShareClicked()` (line ~260) so that: if
      `viewModel.isLocalSharingScreen().getValue()` is true, call
      `viewModel.toggleScreenShare(null)` to stop; otherwise launch the intent.
      In the launcher callback, on RESULT_OK call
      `ScreenCaptureService.start(...)` then
      `viewModel.toggleScreenShare(data)`; on cancel show a brief snackbar.
- [x] 8.4 Update
      `frontends/android-app/app/src/main/java/io/github/phunguy65/zms/presentation/videocall/MeetingActionsBottomSheet.java`
      (and its layout) to reflect `canShareScreen`, `isLocalSharingScreen`, and
      `activeScreenSharerName`: hide row when
      `!canShareScreen && !isLocalSharingScreen`, disable when another sharer is
      active, change label/icon when local is sharing.
- [x] 8.5 Update
      `frontends/android-app/app/src/main/java/io/github/phunguy65/zms/presentation/videocall/VideoGridAdapter.java`
      (and supporting code) to detect a `screen_share` track on a participant
      and render it in spotlight mode (large tile + thumbnails below). Subscribe
      to the track using LiveKit Android's track helpers so the surface renders
      correctly. ← (verify: when a remote participant shares, that tile is
      auto-promoted; when sharing stops, grid reverts to the previous layout
      mode)

## 9. Android — remove placeholder, build, and lint

- [x] 9.1 Remove the `R.string.feature_coming_soon` snackbar path that was
      replaced.
- [x] 9.2 Run
      `./frontends/android-app/gradlew -p frontends/android-app spotlessApply`
      and
      `./frontends/android-app/gradlew -p frontends/android-app :app:testDebugUnitTest`
      until both pass.
- [x] 9.3 Run
      `./frontends/android-app/gradlew -p frontends/android-app :app:assembleDebug`
      and confirm the APK builds clean. ← (verify: full Android build is clean
      with manifest changes and new service)

## 10. Manual smoke and final verification

- [ ] 10.1 Smoke on web: open meeting in two browser windows (host +
      participant); host bats and tắts `allowScreenShare`; verify participant
      toolbar control appears/disappears live, that participant can share, that
      host sees auto-spotlight, that the second client's button disables with
      tooltip while sharing is in progress, and that a browser-side stop ends
      the publication cleanly.
- [ ] 10.2 Smoke on Android device with API 30+: same scenarios as above,
      additionally verify the foreground notification is posted while sharing
      and dismissed when sharing ends, and that `MediaProjection` denial returns
      to idle state.
- [ ] 10.3 Cross-platform smoke: web host + Android participant and vice versa;
      confirm tiles render screen-share track on the receiving side. ← (verify:
      full feature works end-to-end across web and Android with live host
      control toggling)
