# Tasks

## 1. Domain and repository contract updates

- [x] 1.1 Extend `MeetingSettings` with `requirePassword`, expose the getter,
      and update builder/default copy behavior
- [x] 1.2 Update `MeetingMapper` to map `settings.requirePassword` from the
      generated DTO with a safe default of `false`
- [x] 1.3 Add `MeetingRepository.getMeetingByShortCode(String shortCode)` and
      implement it in `MeetingRepositoryImpl` using
      `meetingsApi.getMeetingByShortCode(...)`
- [x] 1.4 Extend `JoinRoomRepository.requestJoin(...)` and
      `JoinRoomRepositoryImpl.requestJoin(...)` with nullable password support ←
      (verify: short-code lookup returns meeting detail with `requirePassword`,
      cached meeting UUID is preserved, and join payloads include password only
      through the existing nullable backend field)

## 2. CallViewModel protected-join orchestration

- [x] 2.1 Add `requiresPassword`, `password`, `isFetchingMeetingInfo`, and
      `fetchError` state to `CallViewModel` with public observers and setters as
      needed
- [x] 2.2 Implement `fetchMeetingInfoAndJoin(String shortCode)` to resolve the
      meeting by short code, cache `meetingUuid`, and either reveal password
      state or continue to join submission
- [x] 2.3 Update `requestJoinRoom()` to pass the current password and preserve
      existing approved, pending, denied, and expired handling
- [x] 2.4 Update `setMeetingCode(...)` and `resetJoinState()` to clear stale
      password state, cached lookup errors, and password-required flags when the
      meeting code changes or the flow is reset ← (verify: state transitions
      cover unprotected join, protected reveal, retry after errors, and
      stale-code resets without leaking old meeting/password state)

## 3. Pre-join UI and interaction updates

- [x] 3.1 Update `fragment_prejoin.xml` to add the hidden password label,
      password input, helper text, and any button-loading support needed for the
      checking state
- [x] 3.2 Update `PreJoinFragment` view binding/init logic to reference password
      views and observe password/fetch state from `CallViewModel`
- [x] 3.3 Update `onJoinClicked()` so the first valid tap performs meeting
      lookup and the protected follow-up tap validates password and submits join
- [x] 3.4 Implement password reveal animation, delayed checking/loading button
      UI, auto-focus behavior, inline field errors, and retry snackbar handling
      ← (verify: password field reveal timing, button loading delay,
      meeting-not- found inline error, invalid-password inline error, and
      network retry behavior all match the spec)

## 4. Localization and automated coverage

- [x] 4.1 Add new protected-join strings to `values/strings.xml`
- [x] 4.2 Add matching Vietnamese translations to `values-vi/strings.xml`
- [x] 4.3 Add unit tests for `MeetingMapper` require-password mapping
- [x] 4.4 Add `CallViewModel` tests covering lookup success/failure, password-
      required state transitions, and password-aware join submission
- [x] 4.5 Add or update integration coverage for the Android join flow where the
      protected password path can be exercised ← (verify: no new pre-join text
      is hardcoded, Vietnamese parity is maintained, and automated coverage
      protects mapper + ViewModel behavior for protected joins)
