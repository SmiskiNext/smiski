# Tasks

## 1. Update meeting-room leave flow inputs and localized copy

- [x] 1.1 Pass `isHost` and the active `meetingId` from
      `frontends/web/src/components/meeting/index.tsx` into the leave dialog.
- [x] 1.2 Add host-specific leave dialog translation keys to
      `frontends/web/src/messages/en.json` and
      `frontends/web/src/messages/vi.json`. ← (verify: all new host dialog
      labels and inline error text are present and mapped in both locales)

## 2. Implement host end-meeting behavior in the leave dialog

- [x] 2.1 Extend `frontends/web/src/components/meeting/leave-dialog.tsx` props
      and rendering so hosts see both "Leave Meeting" and "End for All" while
      non-hosts keep the existing single-action confirmation flow.
- [x] 2.2 Implement the host "End for All" action using the generated
      `endMeeting({ path: { id: meetingId } })` SDK call, with submitting state
      that disables dialog actions and shows a spinner on the destructive
      button.
- [x] 2.3 Handle end-meeting failures inline in the dialog and preserve the
      existing disconnect-plus-navigate behavior for local leave and successful
      end-for-all completion. ← (verify: host success calls API then disconnects
      and navigates to `/${locale}/workspace`, while failure keeps the dialog
      open with retryable localized feedback)

## 3. Add regression-focused tests for role-specific leave behavior

- [x] 3.1 Add unit tests covering host rendering with both exit options and
      non-host rendering with the existing leave-only flow.
- [x] 3.2 Add unit tests covering host submitting state and disabled actions
      during an in-flight end-meeting request. ← (verify: tests fail if
      duplicate submission remains possible or if non-host behavior regresses)
