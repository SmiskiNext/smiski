# Tasks

## 1. Data Layer: End Meeting Repository and API

- [x] 1.1 Add `CompletableFuture<Void> endMeeting(String meetingId)` method
      signature to `MeetingRepository` interface
- [x] 1.2 Implement `endMeeting(String meetingId)` in `MeetingRepositoryImpl`
      using `meetingsApi.endMeeting(UUID)`, following the same
      `CompletableFuture.supplyAsync` and `translateException` pattern as
      `cancelMeeting` ← (verify: method compiles, UUID parsing matches other
      methods, exception is wrapped via `translateException`)

## 2. Domain Layer: EndMeetingUseCase

- [x] 2.1 Create `EndMeetingUseCase` in `domain/usecase/meeting/` with `@Inject`
      constructor accepting `MeetingRepository` and a single
      `execute(String meetingId)` method returning `CompletableFuture<Void>`,
      mirroring the structure and Javadoc style of `CancelMeetingUseCase` ←
      (verify: class is in the correct package, mirrors CancelMeetingUseCase
      shape exactly, Hilt can inject it without additional module entries)

## 3. Presentation Layer: CallViewModel End-Meeting State

- [x] 3.1 Add `_isEndingMeeting` (`MutableLiveData<Boolean>`),
      `_endMeetingError` (`MutableLiveData<String>`), and corresponding public
      `LiveData` accessors and a `clearEndMeetingError()` method to
      `CallViewModel`, following the naming conventions of `_isRecordingLoading`
      and `_recordingError`
- [x] 3.2 Inject `EndMeetingUseCase` into `CallViewModel` via its `@Inject`
      constructor
- [x] 3.3 Implement `endMeetingForAll()` in `CallViewModel`: validate
      `_meetingId` and `_isHost` state first (emit error and return early if
      either is absent or host is false), set `_isEndingMeeting` to true, call
      `endMeetingUseCase.execute(meetingId)`, on success call `endCall()` and
      post a success signal, on error post the translated error message and
      clear loading state ← (verify: precondition guard prevents API calls when
      host is false or meetingId null, success path invokes endCall() before
      finishing, error path keeps user in call with error displayed)

## 4. Presentation Layer: ActiveCallFragment Host Leave Dialog

- [x] 4.1 Observe `_isEndingMeeting` in `ActiveCallFragment.setupObservers()`
      and disable the end-call button container while ending is in progress to
      prevent re-entry
- [x] 4.2 Observe `_endMeetingError` in `ActiveCallFragment.setupObservers()`
      and show the error as a Snackbar, then call
      `viewModel.clearEndMeetingError()`, following the `recordingError`
      observer pattern
- [x] 4.3 Rewrite `showLeaveDialog()` in `ActiveCallFragment` to branch based on
      `viewModel.isHost().getValue()`: when host, show a
      `MaterialAlertDialogBuilder` with title, message, a "Leave Meeting"
      positive button (calls `endCall()` + `requireActivity().finish()`), and an
      "End Meeting for All" neutral button (calls
      `viewModel.endMeetingForAll()`); when non-host, keep the existing
      `AlertDialog.Builder` leave flow unchanged ← (verify: non-host dialog is
      visually and behaviorally identical to the original, host dialog shows
      both options, "End Meeting for All" does not immediately finish the
      activity)

## 5. String Resources: End-Meeting Dialog Strings

- [x] 5.1 Add the following string entries to `res/values/strings.xml` following
      the existing `call_leave_*` grouping:
    - `call_host_leave_title` — dialog title for host leave choice
    - `call_host_leave_message` — subtitle explaining the host has two options
    - `call_host_leave_local` — label for "Leave Meeting" (local only)
    - `call_host_end_for_all` — label for "End Meeting for All"
    - `call_end_meeting_error` — generic error for failed end-meeting requests ←
      (verify: all string keys are referenced correctly from ActiveCallFragment
      without R symbol errors)

## 6. Domain Layer: CancelMeetingUseCase Wiring to ScheduleViewModel

- [x] 6.1 Inject `CancelMeetingUseCase` into `ScheduleViewModel` via its
      `@Inject` constructor alongside existing use cases
- [x] 6.2 Add `_isCancelling` (`MutableLiveData<Boolean>`), `_cancelSuccess`
      (`SingleLiveEvent<Void>`), and `_cancelError` (`SingleLiveEvent<String>`)
      fields plus their public `LiveData` accessors to `ScheduleViewModel`,
      following the `SingleLiveEvent` convention used by `_scheduleError` and
      `_updateSuccess`
- [x] 6.3 Implement `cancelMeeting()` in `ScheduleViewModel`: guard with
      `editMeetingId != null` and `isEditMode == true` and
      `meetingDetail status == SCHEDULED`, set `_isCancelling` to true, call
      `cancelMeetingUseCase.execute(editMeetingId)`, on success post
      `_cancelSuccess`, on error extract the message and post to `_cancelError`,
      clear `_isCancelling` in both paths ← (verify: method is a no-op when
      editMeetingId is null, cancellation does not proceed if meeting status is
      not SCHEDULED, SingleLiveEvent fires exactly once)

## 7. Presentation Layer: ScheduleFragment Cancel Button UI

- [x] 7.1 Add a `MaterialButton` with id `btnCancelMeeting` to
      `fragment_schedule.xml`, styled as an error/destructive outlined button,
      positioned below the save/submit button, initially set to
      `android:visibility="gone"`
- [x] 7.2 Bind `btnCancelMeeting` in `ScheduleFragment.initViews()` using
      `view.findViewById(R.id.btnCancelMeeting)`
- [x] 7.3 In the `meetingDetail` observer inside `ScheduleFragment`, show
      `btnCancelMeeting` only when `isEditMode == true`, the current session
      user ID equals `detail.hostId()`, and
      `detail.status() == MeetingStatus.SCHEDULED`; hide it in all other
      branches ← (verify: button is invisible in create mode, invisible when
      non-host, invisible when meeting is not SCHEDULED, visible only for host
      on a SCHEDULED meeting in edit mode)
- [x] 7.4 Set `btnCancelMeeting.setOnClickListener` in
      `ScheduleFragment.setupListeners()` to call `showCancelConfirmation()`
- [x] 7.5 Implement `showCancelConfirmation()` in `ScheduleFragment` using
      `MaterialAlertDialogBuilder` with a title, warning message, a destructive
      positive button that calls `viewModel.cancelMeeting()`, and a dismiss
      negative button, following the pattern in
      `DashboardFragment.showCancelConfirmation()`

## 8. Presentation Layer: ScheduleFragment Cancel Observers

- [x] 8.1 Observe `viewModel.isCancelling` in
      `ScheduleFragment.setupObservers()` to disable `btnCancelMeeting` and show
      the progress indicator while a cancel request is in flight
- [x] 8.2 Observe `viewModel.cancelSuccess` in
      `ScheduleFragment.setupObservers()`: on event, pop the back stack and show
      a Snackbar success message
- [x] 8.3 Observe `viewModel.cancelError` in
      `ScheduleFragment.setupObservers()`: on event, show a Snackbar with the
      error string and re-enable the cancel button ← (verify: success pops back
      to dashboard and the dashboard reloads upcoming meetings on resume, error
      leaves user on schedule screen with button re-enabled)

## 9. String Resources: Cancel Meeting Strings for ScheduleFragment

- [x] 9.1 Add the following string entries to `res/values/strings.xml`:
    - `schedule_cancel_meeting` — label for the cancel button
    - `schedule_cancel_title` — confirmation dialog title
    - `schedule_cancel_message` — confirmation dialog body text
    - `schedule_cancel_confirm` — destructive confirm label
    - `schedule_cancel_dismiss` — keep-meeting dismiss label
    - `schedule_cancel_success` — Snackbar message after successful cancel
    - `schedule_cancel_error` — Snackbar message after failed cancel ← (verify:
      all keys compile without R errors, text is distinct from
      `upcoming_cancel_*` strings to avoid confusion)

## 10. End-to-End Verification

- [ ] 10.1 Verify non-host active call leave dialog is unchanged: tap end-call
      as a non-host, confirm only one option is displayed and local leave works
- [ ] 10.2 Verify host end-meeting happy path: tap end-call as a host, select
      "End Meeting for All", confirm loading state appears, confirm activity
      finishes after backend success
- [ ] 10.3 Verify host end-meeting error path: with a simulated network failure,
      confirm the call screen remains open, error Snackbar appears, and the
      end-call button re-enables
- [ ] 10.4 Verify cancel button visibility rules: open a SCHEDULED meeting in
      edit mode as host (visible), as non-host (hidden), and open a LIVE meeting
      in edit mode as host (hidden)
- [ ] 10.5 Verify scheduled meeting cancel happy path: tap cancel, confirm in
      dialog, confirm navigation back to dashboard and upcoming meeting list no
      longer shows the cancelled meeting
- [ ] 10.6 Verify scheduled meeting cancel error path: with a simulated network
      failure, confirm user stays on schedule screen, error Snackbar appears,
      and the cancel button re-enables ← (verify: all six scenarios above pass
      on device or emulator, no regressions on non-host leave flow or dashboard
      cancel flow)
