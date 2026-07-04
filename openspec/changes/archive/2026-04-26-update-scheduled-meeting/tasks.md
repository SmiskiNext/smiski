# Tasks

## 1. Align meeting settings schema and room handoff

- [x] 1.1 Update `frontends/web/src/lib/schemas/meeting.ts` to use
      backend-supported admission policy constants and add reverse mapping from
      `MeetingManagementMeetingSettingsResponse` into `MeetingSettingsValues`
- [x] 1.2 Add a shared `meeting_id` session-storage handoff key and write the
      meeting identifier during instant-meeting success and approved join flows
- [x] 1.3 Extend `frontends/web/src/components/meeting/index.tsx` room
      credential consumption to read the stored meeting identifier into room
      state ← (verify: instant-meeting and approved-join redirects both preserve
      meetingId through sessionStorage and room state receives it)

## 2. Build reusable web meeting settings editing flow

- [x] 2.1 Create
      `frontends/web/src/components/meeting/meeting-settings-dialog.tsx` to
      fetch current meeting data on open, initialize the shared settings form,
      and render localized loading and error states
- [x] 2.2 Implement dialog submission with `putMeetingSettings`, backend-aligned
      request mapping, saving state, retryable inline errors, and success close
      behavior
- [x] 2.3 Add localized meeting settings dialog strings to
      `frontends/web/src/messages/en.json` and
      `frontends/web/src/messages/vi.json` ← (verify: dialog load, save, and
      failure states render localized copy and reuse `MeetingSettingsForm`
      correctly)

## 3. Expose meeting settings from the meeting room

- [x] 3.1 Add a settings action and `onOpenSettings` callback prop to
      `frontends/web/src/components/meeting/toolbar.tsx`
- [x] 3.2 Add dialog open state in
      `frontends/web/src/components/meeting/index.tsx` and render
      `MeetingSettingsDialog` for the active room meeting identifier
- [x] 3.3 Ensure the meeting room can open the settings dialog whenever a
      meeting identifier is available from session handoff ← (verify: toolbar
      button opens the dialog for the active meeting and save success closes
      without leaving the room)

## 4. Replace home-screen mock meetings with real host data

- [x] 4.1 Update `frontends/web/src/components/workspace-home-screen.tsx` to
      call `listHostMeetings` on mount and render real meeting title, start
      time, and status values
- [x] 4.2 Add localized loading, empty, and error states for the host meeting
      list and remove dependence on translation-backed mock meeting items
- [x] 4.3 Add a settings action for each home-screen meeting item that opens the
      shared `MeetingSettingsDialog` for the selected meeting ← (verify: host
      meeting list uses live API data, mock cards are gone, and each item can
      open settings with correct meeting context)
