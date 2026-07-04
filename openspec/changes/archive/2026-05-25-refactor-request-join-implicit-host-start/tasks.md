## 1. Backend — implicit elevation in RequestJoinUseCase

- [x] 1.1 Modify
      `../../../../services/meeting-management/src/main/java/io/github/smiskinext/meetingmanagement/application/usecase/RequestJoinUseCase.java`
      to insert host elevation logic immediately after the meeting is loaded
      under lock and before the existing LIVE-status check. When the requester
      equals the meeting host and status is `SCHEDULED`, call `meeting.start()`
      and short-circuit on its failure result. When status is `ENDED` or
      `CANCELLED`, fail with `InvalidStatusTransition(status, LIVE)`. When
      status is `LIVE`, skip elevation. For non-host callers, keep the current
      `status != LIVE` guard. Add Javadoc on `execute` documenting the elevation
      rule.
- [x] 1.2 Confirm `RequestJoinUseCase` still publishes `PublishableEvent`s
      collected from the meeting aggregate after `meetingRepository.save(...)`,
      mirroring how `StartMeetingUseCase` did, so `MeetingStartedEvent` reaches
      the outbox path. ← (verify: MeetingStartedEvent is published exactly once
      when host joins SCHEDULED meeting; no event is published when host joins
      LIVE)

## 2. Backend — delete the start-meeting endpoint

- [x] 2.1 Remove the `POST /v1/meetings/{id}:start` mapping, the
      `startMeetingUseCase` field, its constructor parameter, and the
      `StartMeetingCommand` import from
      `../../../../services/meeting-management/src/main/java/io/github/smiskinext/meetingmanagement/presentation/MeetingController.java`.
- [x] 2.2 Delete
      `../../../../services/meeting-management/src/main/java/io/github/smiskinext/meetingmanagement/application/usecase/StartMeetingUseCase.java`.
- [x] 2.3 Delete
      `../../../../services/meeting-management/src/main/java/io/github/smiskinext/meetingmanagement/application/command/StartMeetingCommand.java`.
- [x] 2.4 Search for any remaining references to `StartMeetingUseCase` or
      `StartMeetingCommand` in `services/meeting-management/src/main` and remove
      them. ← (verify:
      `rg -n 'StartMeetingUseCase|StartMeetingCommand' services/meeting-management/src/main`
      returns no matches)

## 3. Backend — tests for new behaviour

- [x] 3.1 In
      `../../../../services/meeting-management/src/test/java/io/github/smiskinext/meetingmanagement/application/usecase/RequestJoinUseCaseTest.java`,
      add tests covering the full transition matrix: host requesting `SCHEDULED`
      (status becomes LIVE, MeetingStartedEvent registered, response APPROVED);
      host requesting `LIVE` (no extra MeetingStartedEvent); host requesting
      `ENDED` (fails with `InvalidStatusTransition`); host requesting
      `CANCELLED` (fails); non-host requesting `SCHEDULED` (fails as before);
      non-host requesting `LIVE` (existing happy path retained).
- [x] 3.2 Update or remove any existing test in `RequestJoinUseCaseTest` that
      asserts a host requesting `SCHEDULED` is rejected with
      `InvalidStatusTransition`. The new behaviour SHALL be approval. ← (verify:
      every assertion in this test file matches the elevation rule)
- [x] 3.3 Remove from
      `../../../../services/meeting-management/src/test/java/io/github/smiskinext/meetingmanagement/presentation/MeetingControllerTest.java`
      every test that targets the `:start` endpoint or its mocked use case
      (delete the `@MockitoBean StartMeetingUseCase` field if present and any
      related setup).
- [x] 3.4 Delete
      `../../../../services/meeting-management/src/test/java/io/github/smiskinext/meetingmanagement/application/usecase/StartMeetingUseCaseTest.java`
      if the file exists.
- [x] 3.5 Run
      `./services/gradlew -p services/meeting-management spotlessApply test` and
      confirm the suite passes. ← (verify: full meeting-management test suite
      green)

## 4. Backend — regenerate OpenAPI docs

- [x] 4.1 Run
      `./services/gradlew -p services/meeting-management generateOpenApiDocsFromTests`
      and confirm the per-service OpenAPI document no longer references
      `meetings/{id}:start` or `startMeeting`. ← (verify:
      `rg -n ':start|startMeeting' services/meeting-management/build/openapi*.yaml`
      returns no matches)
- [x] 4.2 Run `pnpm run openapi:unified` from the workspace root and confirm the
      merged unified spec at `openapi/unified-openapi.yaml` no longer exposes
      the start endpoint.

## 5. Web — regenerate SDK and adapt instant flow

- [x] 5.1 Run `pnpm --dir frontends/web run sdk:generate`. Confirm
      `frontends/web/src/generated/sdk.gen.ts` no longer exports `startMeeting`
      and `frontends/web/src/generated/types.gen.ts` no longer references
      start-meeting types.
- [x] 5.2 In
      `frontends/web/src/components/create-meeting/use-create-meeting.ts`,
      simplify the state machine: remove the `STARTING` phase, drop the
      `startMeeting` call and its imports, and change `READY` payload to
      `{ meetingId, shortCode }` only. Adjust action types and reducer
      transitions accordingly. After `createInstantMeeting` succeeds, dispatch
      directly to `READY` with the returned identifier and short code.
- [x] 5.3 Update
      `frontends/web/src/components/create-meeting/use-create-meeting.test.ts`
      to match the new state machine: remove or replace the test "transitions to
      ERROR when startMeeting fails", add a test that creating succeeds drives
      `IDLE → CREATING → READY`, and ensure the `READY` payload contains only
      `meetingId` and `shortCode`.
- [x] 5.4 In
      `frontends/web/src/components/create-meeting/instant-meeting-dialog.tsx`,
      replace `handleNavigate` so it stops writing `MEETING_TOKEN_KEY`,
      `MEETING_ROOM_KEY`, and `MEETING_ID_KEY` to `sessionStorage` for the
      instant flow, removes those imports if they become unused in this file,
      and routes to `/${locale}/workspace/green-room?code=${state.shortCode}`.
      Keep the `SuccessDialog` step intact. Update `isCreating` so it no longer
      references the removed `STARTING` phase. ← (verify: instant-meeting-dialog
      renders SuccessDialog after CREATING completes and navigates to green-room
      with the short code)

## 6. Web — quality gates

- [x] 6.1 Run `pnpm --dir frontends/web lint`. Resolve any newly surfaced lint
      issues only inside files modified by this change.
- [x] 6.2 Run `pnpm --dir frontends/web build`. Resolve any type errors only
      inside files modified by this change. ← (verify: build succeeds and
      instant-meeting flow files type-check against the regenerated SDK)

## 7. Android — confirm SDK regeneration causes no breakage

- [x] 7.1 Run
      `./frontends/android-app/gradlew -p frontends/android-app :app:testDebugUnitTest`.
      Resolve only failures introduced by removed SDK symbols, if any. Do not
      modify Android source for any other reason.
- [x] 7.2 Run
      `./frontends/android-app/gradlew -p frontends/android-app :app:assembleDebug`
      and confirm the build succeeds. ← (verify: Android module compiles cleanly
      with the regenerated client; no references to a removed start-meeting
      symbol remain)

## 8. Final cross-stack sanity

- [x] 8.1 Re-run `rg -n 'startMeeting' frontends/web/src` and confirm only
      intentional textual references remain (e.g. localization keys named
      `startMeeting` for UI labels). The generated SDK and create flow MUST NOT
      contain any.
- [x] 8.2 Re-run
      `rg -n 'StartMeetingUseCase|StartMeetingCommand|meetings/\{id\}:start|:start' services/meeting-management`
      and confirm no references remain in main or test sources.
- [x] 8.3 Update
      `openspec/changes/refactor-request-join-implicit-host-start/tasks.md`
      checklist to mark every completed item. ← (verify: all task groups
      complete; no outstanding checkboxes)
