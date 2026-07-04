## 1. Backend Domain & Repository Layer

- [x] 1.1 Add `findByMeetingIdAndUserId(UUID meetingId, UUID userId)` method to
      `MeetingInviteeRepository` port interface
- [x] 1.2 Implement `findByMeetingIdAndUserId` in
      `MeetingInviteeRepositoryAdapter` (JPA query)
- [x] 1.3 Add `findPendingByUserId(UUID userId)` method to
      `MeetingInviteeRepository` port interface
- [x] 1.4 Implement `findPendingByUserId` in `MeetingInviteeRepositoryAdapter`
      (JPA query returning invitees with PENDING status) ← (verify: query
      filters by PENDING status only and joins meeting table for metadata)

## 2. Backend Application Layer — Respond Invite Use Case

- [x] 2.1 Create `RespondInviteCommand` record in `application/command/`
      (meetingId, userId, response enum)
- [x] 2.2 Create `InviteeResponseType` enum (`ACCEPTED`, `DECLINED`) in
      `application/command/`
- [x] 2.3 Create `RespondInviteUseCase` service: find invitee by meetingId +
      userId, call accept()/decline(), save, publish events
- [x] 2.4 Create response DTO `InviteeRespondResponse` record in
      `application/response/` ← (verify: use case correctly handles all error
      cases: invitee not found, invalid transition)

## 3. Backend Application Layer — Get Pending Invitations Use Case

- [x] 3.1 Create `GetPendingInvitationsQuery` record in `application/query/`
      (userId, requesterId)
- [x] 3.2 Create `PendingInvitationResponse` record in `application/response/`
      (inviteeId, meetingId, meetingTitle, meetingShortCode, startTime,
      hostDisplayName, invitedAt)
- [x] 3.3 Create `GetPendingInvitationsUseCase` service: validate ownership,
      query pending invitees, join with meeting data for
      title/shortCode/startTime/host ← (verify: returns correct meeting metadata
      alongside invitee data, ownership check works)

## 4. Backend Presentation Layer — Controllers

- [x] 4.1 Create `RespondInviteRequest` record in `presentation/request/` with
      Jakarta validation (`@NotNull response` field)
- [x] 4.2 Create `InviteeResponseController` with PATCH
      `/api/v1/meetings/{meetingId}/invitees/me` endpoint
- [x] 4.3 Add GET `/api/v1/users/{userId}/invitations:pending` endpoint to
      `UserMeetingController` (or new controller)
- [x] 4.4 Add error mapping for `InviteeNotFound` and `InvalidInviteeTransition`
      in `BaseController` (HTTP 404/409) ← (verify: all spec scenarios return
      correct HTTP status codes and error formats)

## 5. Backend Tests

- [x] 5.1 Unit test `RespondInviteUseCase` — accept success, decline success,
      invitee not found, invalid transition
- [x] 5.2 Unit test `GetPendingInvitationsUseCase` — has pending, empty list,
      ownership mismatch
- [x] 5.3 Integration test for PATCH endpoint — success + error cases
- [x] 5.4 Integration test for GET pending invitations endpoint ← (verify: all
      spec scenarios covered, events are published correctly)

## 6. Notification Service — Host Notification on Invitee Response

- [x] 6.1 Create `InviteeRespondedMessage` record for Kafka deserialization
      (eventId, inviteeId, meetingId, inviterId, respondedAt, responseType)
- [x] 6.2 Create `InviteeRespondedConsumer` Kafka listener consuming both
      `meeting-management.invitee.accepted` and
      `meeting-management.invitee.declined` topics
- [x] 6.3 Create `SendInviteeRespondedEmailUseCase` — resolves host email (via
      inviterId/meeting lookup), renders and sends email
- [x] 6.4 Create email renderer for invitee response notification (subject:
      "[name] [accepted/declined] your meeting invitation")
- [x] 6.5 Add Kafka consumer group config in `NotificationProperties` and
      `application.yaml`
- [x] 6.6 Unit test for consumer and email use case ← (verify: consumer handles
      both event types, failure is isolated and logged)

## 7. OpenAPI & SDK Regeneration

- [x] 7.1 Add PATCH `/api/v1/meetings/{meetingId}/invitees/me` endpoint to
      OpenAPI spec via controller test (`generateOpenApiDocsFromTests`)
- [x] 7.2 Add GET `/api/v1/users/{userId}/invitations:pending` endpoint to
      OpenAPI spec
- [x] 7.3 Run `pnpm run openapi:unified` to regenerate unified spec
- [x] 7.4 Rebuild Android SDK (generated API interfaces will include new
      endpoints) ← (verify: unified OpenAPI spec is valid, new endpoints appear
      in generated SDK)

## 8. Android App — Domain & Data Layer

- [x] 8.1 Create `PendingInvitation` domain model (inviteeId, meetingId,
      meetingTitle, meetingShortCode, startTime, hostDisplayName, invitedAt)
- [x] 8.2 Add `getPendingInvitations()` and
      `respondToInvitation(meetingId, response)` methods to `MeetingRepository`
      interface
- [x] 8.3 Implement both methods in `MeetingRepositoryImpl` using generated API
      interfaces
- [x] 8.4 Create `GetPendingInvitationsUseCase` and `RespondInvitationUseCase`
      in domain/usecase/meeting/ ← (verify: repository calls correct generated
      API methods, error handling follows existing patterns)

## 9. Android App — Presentation Layer (Dashboard)

- [x] 9.1 Create `item_pending_invitation.xml` layout (card with meeting title,
      host, time, Accept/Decline buttons)
- [x] 9.2 Create `PendingInvitationAdapter` RecyclerView adapter
- [x] 9.3 Add pending invitations state and actions to `DashboardViewModel`
      (load, accept, decline)
- [x] 9.4 Update `fragment_dashboard.xml` to add pending invitations
      RecyclerView section above upcoming meetings
- [x] 9.5 Update `DashboardFragment` to observe pending invitations state and
      bind adapter
- [x] 9.6 Add string resources for pending invitations UI (en + vi) ← (verify:
      accept/decline buttons work end-to-end, empty state handled, list
      refreshes after response)
