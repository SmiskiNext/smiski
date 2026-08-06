## 1. Admission eligibility rule

- [x] 1.1 Add `JoinAdmissionSupport` to
      `services/meet/src/main/java/io/github/smiskinext/meet/application/helper/`
      exposing a pure predicate that decides immediate admission from an already
      loaded `Meeting`, the caller's account id, and an
      `Optional<MeetingInvitee>`
- [x] 1.2 Return eligible when the caller's account equals `Meeting#getHostId`,
      without requiring any invitee record
- [x] 1.3 Return eligible when the invitee is present and its `InviteeStatus` is
      `ACCEPTED` or `TENTATIVE`; return not eligible for `NEEDS_ACTION`,
      `DECLINED`, and for an absent invitee ← (verify: eligibility set matches
      the spec exactly — `ACCEPTED`/`TENTATIVE` only; removed invitees are
      excluded by the repository's `removed_at IS NULL` filter, not by this
      predicate)

## 2. Join service wiring

- [x] 2.1 Add `MeetingInviteeRepository` and `JoinRequestResultStore`
      constructor dependencies to `RequestJoinApplicationService`
- [x] 2.2 In the `MANUAL_APPROVAL` branch, resolve eligibility before creating a
      pending request: skip the invitee lookup when the caller is the host, and
      otherwise call `MeetingInviteeRepository#findByMeetingIdAndAccountId`
- [x] 2.3 Keep the `ALLOW_ALL` branch free of any invitee lookup
- [x] 2.4 Route an eligible caller through the existing immediate-admission
      logic so capacity enforcement, token minting, participant attributes, and
      room naming are shared with the `ALLOW_ALL` path rather than duplicated ←
      (verify: bypass and `ALLOW_ALL` produce identical token requests — same
      `PARTICIPANT` role, same attributes, same room config; capacity is checked
      before token issuance on both)

## 3. Pending-request reconciliation

- [x] 3.1 After a successful token issuance for an eligible caller, look up any
      pending request for the same meeting and device via
      `JoinRequestRepository#findByDeviceId`
- [x] 3.2 When one exists, transition it with `JoinRequest#approve()`, persist
      `JoinRequestResult.approved(...)` carrying the issued token and room name,
      register the approved event with the joining account as `approvedBy`,
      publish it, and remove the request from the pending queue
- [x] 3.3 Ensure the token is minted before the transition so a LiveKit failure
      leaves the pending request untouched and publishes nothing
- [x] 3.4 Publish no join-request event when an eligible caller has no pending
      request ← (verify: reconciliation order is token → approve → persist
      result → publish → dequeue, mirroring
      `AcceptJoinRequestsApplicationService`; the token in the persisted result
      is the same token returned in the response)

## 4. Unit tests

- [x] 4.1 Update `RequestJoinApplicationServiceTest` for the new constructor and
      confirm the existing `ALLOW_ALL`, capacity, unknown-meeting, and
      duplicate-device cases still pass unchanged
- [x] 4.2 Host joins a `MANUAL_APPROVAL` meeting → `APPROVED` with token, no
      pending request saved, no invitee lookup performed
- [x] 4.3 `ACCEPTED` invitee joins → `APPROVED` with token, no pending request
      saved
- [x] 4.4 `TENTATIVE` invitee joins → `APPROVED` with token, no pending request
      saved
- [x] 4.5 `NEEDS_ACTION` invitee joins → `PENDING`, join-created event
      registered
- [x] 4.6 `DECLINED` invitee joins → `PENDING`, join-created event registered
- [x] 4.7 Absent invitee record (non-invitee or removed) joins → `PENDING`,
      join-created event registered
- [x] 4.8 Eligible caller at `maxParticipants` → `MeetingFull`, no token
      generated, no pending request saved
- [x] 4.9 Eligible caller with an existing pending request → `APPROVED`, request
      approved, result store holds the issued token and room name, approved
      event registered, request removed from the queue
- [x] 4.10 Eligible caller with no pending request → no event published at all
- [x] 4.11 Eligible caller whose token issuance fails → failure returned, result
      store untouched, no approved event, request not dequeued ← (verify: every
      spec scenario in the ADDED requirements maps to a test here, and no test
      asserts on mocks in a way that would pass if the bypass branch were
      removed)

## 5. Integration tests

- [x] 5.1 Add `meeting_invitees` seeding to
      `JoinMeetingControllerIntegrationTest` covering a given account id and
      invitation status
- [x] 5.2 `ACCEPTED` invitee joins a `MANUAL_APPROVAL` meeting → `200`
      `APPROVED` with a non-empty token and room name, and no
      `meet.join.created` row in the outbox
- [x] 5.3 Host joins a `MANUAL_APPROVAL` meeting → `200` `APPROVED` with a
      non-empty token
- [x] 5.4 `NEEDS_ACTION` invitee joins → `200` `PENDING` with a
      `meet.join.created` outbox row, proving the default path is intact
- [x] 5.5 Soft-removed `ACCEPTED` invitee (row with `removed_at` set) joins →
      `200` `PENDING`, confirming the removal filter denies the bypass against
      the real schema ← (verify: assertions read real database rows and real
      outbox contents rather than mocks, and the removed-invitee case genuinely
      exercises the `removed_at IS NULL` filter)

## 6. Verification

- [x] 6.1 `./services/gradlew spotlessApply`
- [x] 6.2 `./services/gradlew -p services/meet test`
- [x] 6.3 `./services/gradlew -p services/meet integrationTest`
- [x] 6.4 Confirm `services/meet/openapi.yaml` is unchanged by the change, since
      no HTTP contract is modified ← (verify: `git status` shows no diff in
      `openapi.yaml`, no Flyway migration was added, and no file under `app/`
      was touched)
