# Tasks

## 1. useRecordingState Hook

- [x] 1.1 Create `frontends/web/src/hooks/use-recording-state.ts` with the
      four-state type: `'idle' | 'starting' | 'recording' | 'stopping'`
- [x] 1.2 Subscribe to `RoomEvent.RoomMetadataChanged` using `useRoomContext()`
      to parse `{"recording": true/false}` from room metadata
- [x] 1.3 Implement IDLE → STARTING transition on `startRecording()` API call
      and STARTING → RECORDING on metadata-true confirmation
- [x] 1.4 Implement RECORDING → STOPPING transition on `stopRecording()` API
      call and STOPPING → IDLE on metadata-false confirmation
- [x] 1.5 Implement error fallback: API failure reverts state and populates
      `error: string | null`
- [x] 1.6 Implement 10-second metadata confirmation timeout using a ref-based
      timer: fall back to previous stable state and set a localized timeout
      error message
- [x] 1.7 Guard all state setters with `isMountedRef` to prevent updates after
      unmount ← (verify: state machine transitions match all scenarios in
      web-recording-controls spec; timeout resets state to idle correctly)

## 2. RecordingIndicator Component

- [x] 2.1 Create `frontends/web/src/components/meeting/recording-indicator.tsx`
      with a pulsing red dot using `motion-safe:animate-ping`, label "REC",
      `role="status"`, and `aria-live="polite"`
- [x] 2.2 Use `h-2.5 w-2.5 bg-error rounded-full` for dot and `bg-error/30` for
      the ping ring; label uses
      `text-xs font-semibold tracking-wider text-error` ← (verify: component
      renders correctly when `isVisible=true`; ping animation only when
      recording)

## 3. RecordingConfirmDialog Component

- [x] 3.1 Create
      `frontends/web/src/components/meeting/recording-confirm-dialog.tsx`
      following `LeaveDialog` structure: `Dialog`, `DialogContent`,
      `DialogHeader`, `DialogFooter`
- [x] 3.2 Render title using `recordingConfirmTitle` i18n key and description
      using `recordingConfirmMessage` i18n key
- [x] 3.3 Add Cancel and Start buttons; Start button uses `bg-primary` (not
      destructive variant)
- [x] 3.4 Show a loading spinner on the Start button and disable both buttons
      when the API call is in the `starting` state
- [x] 3.5 Render the inline error banner
      (`border border-error/40 bg-error-subtle` pattern) with
      `recordingStartError` key when `error` is non-null; change Start button
      label to match `recordingConfirmStart` / "Retry" appropriately ← (verify:
      dialog matches all confirmation scenarios in spec; error path shows retry
      label and clears on successful open)

## 4. RecordingBanner Component

- [x] 4.1 Create `frontends/web/src/components/meeting/recording-banner.tsx`
      accepting `type: 'started' | 'stopped' | null` and `onDismiss`
- [x] 4.2 Implement started variant: `bg-error/10` background, red dot,
      `recordingStartedBanner` text, `role="alert"`, `aria-live="assertive"`,
      8-second auto-dismiss
- [x] 4.3 Implement stopped variant: `bg-surface-input` background, neutral
      text, `recordingStoppedBanner` text, `role="status"`,
      `aria-live="polite"`, 5-second auto-dismiss
- [x] 4.4 Add dismiss button using `dismissRecordingBanner` i18n key; clicking
      it immediately hides the banner ← (verify: banner does not appear on
      initial mount; correct role/aria-live per variant; auto-dismiss fires at
      correct delays)

## 5. Toolbar Updates

- [x] 5.1 In `toolbar.tsx`, add
      `recordingState: 'idle' | 'starting' | 'recording' | 'stopping'` prop and
      replace `isRecording: boolean` + `onToggleRecording` with
      `onStartRecording` and `onStopRecording`
- [x] 5.2 Remove the recording `DropdownMenuItem` from the More dropdown menu
- [x] 5.3 Add a `ToolbarIconButton` for recording between the Layout Picker and
      the More dropdown, visible only when `isHost` is true
- [x] 5.4 Implement the four visual states on the recording button: IDLE
      (`bg-surface-input`, `Circle` stroke icon), STARTING (`bg-surface-input`,
      `Loader2 animate-spin`, disabled), RECORDING (`bg-error`,
      `shadow-[0_0_12px_rgba(220,38,38,0.5)]`, filled `Circle` white icon),
      STOPPING (`bg-error/70`, `Loader2 animate-spin` white, disabled)
- [x] 5.5 Wire `onClick`: when `recordingState === 'idle'` call
      `onStartRecording`; when `recordingState === 'recording'` call
      `onStopRecording`; disabled during `starting` and `stopping` ← (verify:
      toolbar type signature compiles without errors; all four recording button
      visual states render correctly; button disabled during transitions)

## 6. i18n Keys

- [x] 6.1 Add to `frontends/web/src/messages/en.json` inside the `meetingRoom`
      namespace: `recordingActive`, `recordingStartedBanner`,
      `recordingStoppedBanner`, `dismissRecordingBanner`, `recordingStarting`,
      `recordingStopping`, `recordingConfirmTitle`, `recordingConfirmMessage`,
      `recordingConfirmCancel`, `recordingConfirmStart`, `recordingStartError`,
      `recordingStopError`
- [x] 6.2 Add the same twelve keys with Vietnamese translations to
      `frontends/web/src/messages/vi.json` ← (verify: both locale files have all
      twelve keys; no missing translation warnings at build time)

## 7. Meeting Shell Wiring (index.tsx)

- [x] 7.1 Replace `const [isRecording, setIsRecording] = useState(false)` and
      `handleToggleRecording` with `useRecordingState(meetingId)` hook in
      `frontends/web/src/components/meeting/index.tsx`
- [x] 7.2 Add `RecordingIndicator` to the meeting header actions slot,
      positioned before `ConnectionIndicator`, visible only when
      `recordingState === 'recording'`
- [x] 7.3 Add banner state: track previous `recordingState` to detect
      transitions; show `RecordingBanner` with type `'started'` or `'stopped'`
      in the same slot as the reconnecting banner
- [x] 7.4 Add `RecordingConfirmDialog` wired to `open` state and
      `useRecordingState` callbacks; open it when host clicks the toolbar record
      button in idle/error state
- [x] 7.5 Update `MeetingToolbar` props: pass `recordingState`,
      `onStartRecording` (opens confirm dialog), `onStopRecording` (calls
      `stopRecording()` directly)
- [x] 7.6 Verify that no `isRecording` or `onToggleRecording` prop references
      remain in `index.tsx` ← (verify: end-to-end flow works as a host: start →
      confirm → indicator visible → stop → stopped banner; non-host sees only
      the indicator; banner not shown on initial load)
