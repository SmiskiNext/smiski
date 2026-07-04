# Tasks

## 1. Domain and request-model alignment

- [x] 1.1 Add `MeetingSettingsInput` to encapsulate schedule settings selected
      by the user
- [x] 1.2 Update `ScheduleMeetingRequest` to carry optional title, schedule
      timing, and the new settings input object
- [x] 1.3 Update any affected use-case or repository method signatures to
      consume the richer schedule request cleanly ← (verify: Android domain
      layer still follows codemap boundaries and scheduled meeting requests can
      represent every setting required by design.md)

## 2. Repository mapping and backend contract consistency

- [x] 2.1 Update `MeetingRepositoryImpl.buildScheduleMeetingRequest()` to map
      waiting room to `admissionPolicy`
- [x] 2.2 Map `muteOnEntry`, `maxParticipants`, `screenShareMode`,
      `chatEnabled`, `recordingEnabled`, and optional `password` from user
      selections instead of hardcoded defaults
- [x] 2.3 Preserve host-video as a locally stored preference only and keep it
      out of the serialized backend request
- [x] 2.4 Ensure default UI values still produce a backward-compatible schedule
      request payload when the user does not change advanced settings ← (verify:
      schedule API request contents match the updated spec and no unsupported
      host-video field is sent)

## 3. Schedule ViewModel validation and derived state

- [x] 3.1 Update `ScheduleViewModel` submit validation so title is optional but
      limited to 255 characters
- [x] 3.2 Preserve submit-time validation for required date, time, and duration
      fields, including the existing 15-480 minute duration range
- [x] 3.3 Build the richer `ScheduleMeetingRequest` from validated schedule form
      state and persist host-video preference locally before submission
- [x] 3.4 Add derived end-time calculation support so presentation can show
      helper feedback when date, time, and duration are valid
- [x] 3.5 Keep loading, success, validation, and error state handling coherent
      for the expanded form ← (verify: invalid inputs are rejected before API
      submission, end-time feedback is correct, and duplicate submits remain
      blocked)

## 4. Schedule fragment UI and interaction updates

- [x] 4.1 Restructure `fragment_schedule.xml` to add mute-on-entry, password,
      and advanced-settings controls while preserving the existing schedule flow
- [x] 4.2 Migrate schedule form text inputs from Material Components 2 styles to
      Material 3-compatible styling and spacing tokens
- [x] 4.3 Add icons for all meeting setting rows and a collapsible advanced
      settings section for max participants, screen share mode, chat enabled,
      and recording enabled
- [x] 4.4 Update `ScheduleFragment` to collect all new control values and send
      them through the ViewModel
- [x] 4.5 Add blur-based inline validation with `TextInputLayout.setError()` for
      title, date, time, duration, password, and max-participants where
      applicable
- [x] 4.6 Add accessibility content descriptions for the date and time picker
      triggers and prevent past-date selection in the date picker
- [x] 4.7 Show calculated end-time helper text under duration and keep it
      updated as scheduling inputs change
- [x] 4.8 Add a visible loading indicator to the submit button while submission
      is in progress ← (verify: the schedule screen exposes all required
      settings, inline errors behave correctly, helper/accessibility
      improvements work, and submit loading is visible)

## 5. Resources and text support

- [x] 5.1 Add or update English string resources for new setting labels, helper
      text, validation messages, password visibility text, advanced-section
      text, and accessibility descriptions
- [x] 5.2 Add any supporting icons, arrays, or resource values needed for
      screen-share options and advanced settings defaults
- [x] 5.3 Confirm resource naming and theming remain consistent with existing
      Android meeting-creation patterns ← (verify: all new UI text resolves from
      resources and screen-share/default settings are fully backed by Android
      resources)

## 6. Validation and regression checks

- [x] 6.1 Compile the Android app module and fix any schedule-flow build or
      resource issues
- [ ] 6.2 Manually validate title optionality, title max-length handling,
      required date/time/duration behavior, and duration range enforcement
- [ ] 6.3 Manually validate request-setting combinations for waiting room, mute
      on entry, password, max participants, screen share mode, chat enabled, and
      recording enabled
- [ ] 6.4 Manually verify accessibility and UX details including picker
      descriptions, past-date prevention, end-time helper text, advanced-section
      behavior, and submit loading state ← (verify: the full schedule-meeting
      flow matches proposal.md, design.md, and android-meeting-creation delta
      spec scenarios)
