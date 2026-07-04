## 1. Backend host fast-path

- [x] 1.1 Update `RequestJoinUseCase.execute` in
      `../../../services/meeting-management/src/main/java/io/github/smiskinext/meetingmanagement/application/usecase/RequestJoinUseCase.java`
      so the LiveKit fast-path branch is entered when
      `isHost || meeting.getSettings().admissionPolicy() == AdmissionPolicy.ALLOW_ALL`.
- [x] 1.2 Confirm that the existing host-related logic (SCHEDULED → LIVE
      elevation in `prepareMeetingForJoin`, password skip on line 81,
      `ParticipantRole.HOST` resolution, max-participant skip for host,
      `MeetingStartedEvent` publication) continues to apply unchanged.
- [x] 1.3 Verify no other branch in the use case needs to be touched:
      pending-request creation (lines around the `JoinRequest.create` block)
      SHALL only run when the requester is a non-host on a `MANUAL_APPROVAL`
      meeting, which the new guard preserves. ← (verify: trace
      `RequestJoinUseCase.execute` end-to-end and confirm host on
      `MANUAL_APPROVAL` cannot reach `joinRequestRepository.save`)

## 2. Backend tests

- [x] 2.1 Add
      `hostRequestingScheduledMeetingWithManualApproval_startsAndApprovesWithToken`
      to
      `../../../services/meeting-management/src/test/java/io/github/smiskinext/meetingmanagement/application/usecase/RequestJoinUseCaseTest.java`.
      Use a `Meeting` reconstituted with `AdmissionPolicy.MANUAL_APPROVAL`,
      status `SCHEDULED`, host requester. Assert: status becomes `LIVE`,
      response is `APPROVED` with `token-value` and `meeting-<id>` room name,
      `MeetingStartedEvent` is published once, no `JoinRequest` is saved,
      `ParticipationLog` is saved.
- [x] 2.2 Add `hostRequestingLiveMeetingWithManualApproval_approvesWithToken`.
      Same as 2.1 but starting from `LIVE`; assert no `MeetingStartedEvent` is
      published.
- [x] 2.3 Add `nonHostRequestingLiveMeetingWithManualApproval_returnsPending`.
      Use a separate non-host user id; assert response status is `PENDING`,
      token and roomName are `null`, a `JoinRequest` is saved through
      `joinRequestRepository`, and `liveKitPort.generateToken` is never called.
- [x] 2.4 Re-run `./services/gradlew -p services/meeting-management test` and
      ensure the suite passes, including the existing scenarios
      (`hostRequestingScheduledMeeting_*`,
      `hostRequestingLiveMeetingReturnsApprovedWithoutStartedEvent`,
      `hostRequestingEndedMeetingFailsWithInvalidStatusTransition`,
      `hostRequestingCancelledMeetingFailsWithInvalidStatusTransition`,
      `nonHostRequestingScheduledMeetingFailsWithInvalidStatusTransition`,
      `nonHostRequestingLiveMeetingReturnsApproved`,
      `concurrentHostRequests_publishStartedEventExactlyOnceAndBothApprove`). ←
      (verify: backend tests green; new tests cover the
      host-on-`MANUAL_APPROVAL` regression)

## 3. Web API base URL helper

- [x] 3.1 In `frontends/web/src/lib/api/client.ts`, store the configured base
      URL in module scope inside `configureApiClient` and add an exported
      `getApiBaseUrl(): string` that returns the stored value (default empty
      string before configuration).
- [x] 3.2 Confirm `frontends/web/src/components/api-client-provider.tsx`
      continues to call `configureApiClient(apiBaseUrl, ...)` with
      `apiBaseUrl = process.env.NEXT_PUBLIC_API_BASE_URL` so the helper picks up
      the configured value at bootstrap. No behavior change required there.

## 4. Web SSE URL fix

- [x] 4.1 Update `frontends/web/src/components/join-meeting/use-join-meeting.ts`
      so the `EventSource` constructor at line 272 is called with
      `` `${getApiBaseUrl()}/api/v1/joinRequests/${requestId}/events` ``. Import
      `getApiBaseUrl` from `@/lib/api/client.ts`.
- [x] 4.2 Update `frontends/web/src/hooks/use-waiting-room.ts` so the
      `EventSource` constructor at line 92 is called with
      `` `${getApiBaseUrl()}/api/v1/meetings/${meetingId}/events` `` using the
      same helper.
- [x] 4.3 Run `pnpm --dir frontends/web lint` and
      `pnpm --dir frontends/web build` to confirm both files type-check and pass
      Biome lint.

## 5. Manual smoke test

- [ ] 5.1 With the local stack running (`pnpm --dir frontends/web dev`, Caddy on
      `localhost:30000`, meeting-management service on `localhost:8182`), log in
      as a host, create an instant meeting, click "Continue" on the success
      dialog, and confirm the green-room flow advances directly into the LiveKit
      meeting room without showing a waiting-approval screen. (manual)
- [ ] 5.2 As the same host, schedule a meeting for the next minute, navigate
      back to the workspace home, click "Start meeting" on the upcoming card,
      and confirm the host enters the room without waiting. (manual)
- [ ] 5.3 As a second non-host user, request to join the host's
      `MANUAL_APPROVAL` meeting and confirm: the request shows as `PENDING`, the
      SSE EventSource opens against
      `http://localhost:30000/api/v1/joinRequests/{id}/events` (verify in
      DevTools Network), the host can approve the request, and the SSE stream
      delivers `join_request_approved`. ← (verify: end-to-end host join works;
      non-host SSE waiting-room continues to work; SSE URL targets gateway
      origin) (manual)

## 6. OpenSpec finalization

- [x] 6.1 Run `openspec validate fix-host-join-bypass-admission --strict` and
      confirm the change validates cleanly.
- [ ] 6.2 Mark this tasks file complete and ready for archive once verification
      passes.
