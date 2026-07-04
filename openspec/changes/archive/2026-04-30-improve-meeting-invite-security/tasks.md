# Tasks

## 1. Database Schema and Migration

- [x] 1.1 Add Flyway migration V{n}\_\_create_invite_tokens_table.sql with
      columns: id (uuid, pk), meeting_id (uuid, fk → meetings.id), invitee_id
      (uuid, fk → meeting_invitees.id), token_hash (varchar, unique, not null),
      status (varchar: PENDING/USED/REVOKED/EXPIRED), expires_at (timestamp with
      time zone, not null), created_at, updated_at. Add index on (meeting_id,
      status). Add index on token_hash.
- [x] 1.2 Add column invite_token_id (uuid, nullable, fk → invite_tokens.id) to
      the meeting_invitees table via a separate Flyway migration.
- [x] 1.3 Write rollback scripts for both migrations. ← (verify: migrations run
      without errors against a local PostgreSQL instance, all foreign key
      constraints are satisfied)

## 2. Domain Model: InviteToken

- [x] 2.1 Create `InviteTokenStatus` enum with values PENDING, USED, REVOKED,
      EXPIRED.
- [x] 2.2 Create `InviteToken` aggregate root in `domain/model/` with fields:
      id, meetingId, inviteeId, tokenHash, status, expiresAt, createdAt,
      updatedAt. Include factory methods `create()`, `markUsed()`, `revoke()`,
      and domain behaviors.
- [x] 2.3 Create `InviteTokenId` value object extending `Identifier<UUID>`.
- [x] 2.4 Add domain validation: `expiresAt` must be in the future when
      creating, status transitions follow PENDING→{USED, REVOKED} only.
- [x] 2.5 Write unit tests for `InviteToken` aggregate covering creation,
      markUsed, revoke, and invalid status transitions. ← (verify: all domain
      model tests pass, status transition coverage is complete)

## 3. Domain Port: InviteTokenRepository

- [x] 3.1 Define `InviteTokenRepository` port interface in `domain/port/` with
      methods: save(InviteToken), findById(InviteTokenId),
      findByMeetingId(UUID), findByMeetingIdAndStatus(UUID, InviteTokenStatus),
      revokeAllPendingByMeetingId(UUID), revokeAllPendingByInviteeId(UUID),
      existsByTokenHash(String).
- [x] 3.2 Create `InviteTokenJpaEntity` in `infrastructure/persistence/`
      matching the Flyway schema.
- [x] 3.3 Create `InviteTokenJpaRepository` extending
      `JpaRepository<InviteTokenJpaEntity, UUID>` with custom query methods.
- [x] 3.4 Create `InviteTokenRepositoryAdapter` implementing
      `InviteTokenRepository`, containing private `toDomain()` and `toEntity()`
      mappers.
- [x] 3.5 Write integration tests for `InviteTokenRepositoryAdapter` using the
      testcontainers configuration. ← (verify: adapter tests pass, repository
      queries return correct domain objects)

## 4. InviteTokenService (Token Generation and Validation)

- [x] 4.1 Create `InviteTokenService` in `application/service/` with methods:
      `generateToken(MeetingId, InviteeId): String` (returns the raw token
      string), `hashToken(String): String` (returns SHA-256 hash),
      `validateToken(String): ValidationResult` (returns case class with
      valid/invalid reason and meeting metadata).
- [x] 4.2 Inject `serverSecret` from `@Value("${zms.invite.token-secret}")` and
      configure via application properties.
- [x] 4.3 Implement HMAC-SHA256 signing: compute
      `HMAC-SHA256(meetingId | inviteeId | expiryEpoch, secret)` as bytes,
      base64url-encode. Construct token as
      `base64(signature) | meetingId | inviteeId | expiryEpoch`. Use `|` as
      delimiter — ensure pipe does not appear in base64 output by using
      base64url (no +/=).
- [x] 4.4 Implement `validateToken`: parse token components, recompute HMAC,
      compare signatures (constant-time comparison), check expiry (allow 60s
      grace), look up token hash in DB to check status.
- [x] 5.3 Add `inviteTokenId` field to `MeetingInvitee` aggregate root and
      update factory methods. ← (verify: this task depends on 2 and 5 — ensure
      domain model is complete first)

## 5. Update MeetingInvitee to Reference InviteToken

- [x] 5.1 Add `inviteTokenId` field to `MeetingInvitee` aggregate root with
      optional `@Nullable InviteTokenId`.
- [x] 5.2 Add `inviteToken()` accessor returning `Optional<InviteTokenId>`.
- [x] 5.3 Update `reconstitute()` to accept `inviteTokenId` parameter.
- [x] 5.4 Update JPA entity mapper to handle the new foreign key column.
- [x] 5.5 Update `MeetingInviteeJpaEntity` and `MeetingInviteeRepositoryAdapter`
      to map the new column.
- [x] 5.6 Write migration test to verify schema is correct. ← (verify: existing
      MeetingInvitee tests still pass, new FK is correctly mapped)

## 6. Update ScheduleMeetingUseCase to Generate and Store Invite Tokens

- [x] 6.1 Inject `InviteTokenService` and `InviteTokenRepository` into
      `ScheduleMeetingUseCase`.
- [x] 6.2 After `inviteeRepository.saveAll(invitees)`, for each invitee generate
      a token via `inviteTokenService.generateToken(meetingId, inviteeId)` and
      save the `InviteToken` aggregate.
- [x] 6.3 Update `MeetingInvitee` reference: set the `inviteTokenId` on each
      `MeetingInvitee` after token creation.
- [x] 6.4 Update `MeetingInvitationsSentEvent` to include per-invitee token
      string (add new field `Map<UUID, String>` inviteeTokens mapping inviteeId
      → token). Remove `rawPassword` field (replace with nullable `null` for
      now; full removal in phase 3).
- [x] 6.5 Write unit tests for the updated `ScheduleMeetingUseCase` verifying
      token creation and event payload. ← (verify: all ScheduleMeetingUseCase
      tests pass, token is persisted, event contains token string)

## 7. Update MeetingInvitationsSentEvent and Kafka Message

- [x] 7.1 Update `MeetingInvitationsSentEvent` record: remove `rawPassword`
      field entirely. Add `Map<UUID, String>` field `inviteeTokens` (inviteeId →
      token string).
- [x] 7.2 Update `MeetingInvitationsSentMessage` in the notification service
      accordingly (remove rawPassword, add inviteeTokens map).
- [x] 7.3 Update `ScheduleMeetingUseCase` event publishing: populate
      inviteeTokens map from generated tokens.
- [x] 7.4 Add deprecation notice comment to `rawPassword` removal in event
      classes referencing the migration window.
- [x] 7.5 Run `EventTopicContractTest` to verify event schema compatibility. ←
      (verify: contract test passes, all consumers can deserialize the new event
      format)

## 8. Update PutMeetingSettingsUseCase: Detect Password Change, Invalidate Tokens

- [x] 8.1 Inject `InviteTokenRepository` and `ApplicationEventPublisher` into
      `PutMeetingSettingsUseCase`.
- [x] 8.2 After determining that the new password differs from the old password
      AND meeting status is SCHEDULED: call
      `inviteTokenRepository.revokeAllPendingByMeetingId(meetingId)`.
- [x] 8.3 Publish `MeetingInviteTokensInvalidatedEvent` to Kafka with meetingId
      and list of affected invitee IDs and emails.
- [x] 8.4 Extend the `MeetingSettingsResponse` DTO with `invalidatedInviteCount`
      and `resendInvitesRecommended` fields. Populate them from the invalidation
      result.
- [x] 8.5 Write unit tests for `PutMeetingSettingsUseCase` covering: password
      change triggers invalidation, non-password changes do not, LIVE meeting
      password change does not invalidate. ← (verify: use case tests pass, Kafka
      event is published with correct payload)

## 9. New REST Endpoints: InviteToken Validation and Invite Management

- [x] 9.1 Create `ValidateInviteTokenRequest` and `ValidateInviteTokenResponse`
      DTOs.
- [x] 9.2 Create `ValidateInviteTokenUseCase` that calls
      `InviteTokenService.validateToken()` and maps the result to the response
      DTO. Marks token as USED on success.
- [x] 9.3 Create `InviteTokenController` with
      `POST /{version}/meetings/invite-tokens:validate` endpoint (public, no
      auth required).
- [x] 9.4 Create `InviteeListResponse`, `ResendInviteResponse` DTOs.
- [x] 9.5 Create `GetInviteesUseCase`, `ResendInviteUseCase`,
      `RevokeInviteUseCase`, `AddInviteeUseCase`.
- [x] 9.6 Create `InviteManagementController` with:
      `GET /{version}/meetings/{id}/invitees`,
      `POST /{version}/meetings/{id}/invitees/{inviteeId}/resend`,
      `DELETE /{version}/meetings/{id}/invitees/{inviteeId}`,
      `POST /{version}/meetings/{id}/invitees`.
- [x] 9.7 Write integration tests for all new endpoints. ← (verify: all new
      endpoints return correct HTTP status codes, auth guards work, data is
      correct)

## 10. New Kafka Event: MeetingInviteTokensInvalidatedEvent

- [x] 10.1 Create `MeetingInviteTokensInvalidatedEvent` in `domain/event/`
      implementing `PublishableEvent`. Fields: eventId, meetingId, hostId,
      meetingShortCode, meetingTitle, list of affected InviteeInfo (email,
      userId, displayName), updatedAt.
- [x] 10.2 Register the event in the transactional outbox via
      `OutboxEventPublisher`.
- [x] 10.3 Write unit test for the event structure and JSON serialization.

## 11. Notification Service: Update MeetingInvitationLinkFactory

- [x] 11.1 Update `MeetingInvitationLinkFactory.buildJoinLink()` to accept an
      `inviteToken` parameter and build `{baseUrl}/join?token={inviteToken}`
      instead of the short-code + password URL.
- [x] 11.2 Add backward-compatible overload
      `buildJoinLink(String shortCode, String rawPassword)` that logs a warning
      and falls back to the legacy URL when inviteToken is null. Mark as
      deprecated.
- [x] 11.3 Update `SendMeetingInvitationEmailUseCase.send()` to accept the
      invite token string from the Kafka message and pass it to
      `MeetingInvitationLinkFactory`.
- [x] 11.4 Write tests for `MeetingInvitationLinkFactory` covering both new
      token-based URL and legacy fallback.

## 12. Notification Service: MeetingInviteTokensInvalidatedConsumer

- [x] 12.1 Create `MeetingInviteTokensInvalidatedMessage` record in
      `infrastructure/messaging/`.
- [x] 12.2 Create `MeetingInviteTokensInvalidatedConsumer` Kafka listener on
      topic `meeting-management.meeting.invite-tokens.invalidated`.
- [x] 12.3 In `onMeetingInviteTokensInvalidated()`: call
      `GET /meetings/{meetingId}/invitees` to fetch the current invitee list
      with tokens. For each invitee that had their token invalidated (check by
      comparing with the event payload), send a "your invite link has been
      updated" email.
- [x] 12.4 Create `InviteUpdatedEmailRenderer` for the new email template with
      subject "Update: Your meeting invite for [title] has been updated".
- [x] 12.5 Add idempotency: check if a new invite email was already sent after
      the invalidation time to avoid duplicate emails.
- [x] 12.6 Write tests for the consumer covering happy path, empty affected
      list, and partial send failures. ← (verify: consumer tests pass, email is
      sent for each affected invitee, idempotency prevents duplicates)

## 13. Update MeetingInvitationEmailRenderer: Remove Password from Template

- [x] 13.1 Remove any password field reference from
      `MeetingInvitationEmailRenderer.render()`. The rendered email shall only
      include the join button/link and meeting details.
- [x] 13.2 Update HTML template to use the token-based join link passed from
      `SendMeetingInvitationEmailUseCase`.
- [x] 13.3 Verify existing `MeetingInvitationEmailRendererTest` test fixtures
      are updated.

## 14. Application Configuration

- [x] 14.1 Add `zms.invite.token-secret` property to `application.properties` in
      meeting-management service with a placeholder value and instructions for
      production configuration.
- [x] 14.2 Add `zms.invite.token-expiry-days=7` property with a default.
- [x] 14.3 Add `zms.invite.use-tokens=false` feature flag (default false for
      phased rollout).
- [x] 14.4 Document the properties in the service's
      `application-sample.properties`.

## 15. OpenAPI Specification

- [x] 15.1 Add OpenAPI annotations to all new endpoint DTOs and controller
      methods.
- [x] 15.2 Run the `OpenApiGenerationTest` to ensure new endpoints are included
      in the generated spec.
- [x] 15.3 Verify the `ScheduleMeetingRequest` and
      `MeetingInvitationsSentMessage` schema changes are reflected.

## 16. Android App: Join Flow with Invite Token

- [x] 16.1 Update `MeetingsApi` (OpenAPI-generated) with new endpoints:
      `validateInviteToken`, `getInvitees`, `resendInvite`, `revokeInvite`,
      `addInvitee`.
- [x] 16.2 Update `MeetingRepository` interface to include new methods.
- [x] 16.3 Implement `joinByInviteToken()` in `MeetingRepositoryImpl`: calls
      `POST /meetings/invite-tokens/validate`, then proceeds with the existing
      join flow using the returned `shortCode` and `meetingId`.
- [x] 16.4 Update `VideoCallActivity` or the join screen to handle invite token
      URLs: extract `token` query param on launch, call `joinByInviteToken()`,
      skip password prompt if `preApproved` is true.
- [x] 16.5 Add UI for error states: expired token, revoked token, meeting not
      found.
- [x] 16.6 Update `ScheduleFragment` edit mode: after password change settings
      update returns `resendInvitesRecommended: true`, show "Password changed. X
      invites invalidated. Resend?" prompt with action button.
- [x] 16.7 Update host meeting detail screen: add list of invitees with token
      status (PENDING/USED/REVOKED/EXPIRED), add resend and revoke actions per
      invitee. ← (verify: Android build succeeds, token-based join flow works
      end-to-end, invite management UI renders correctly)

## 17. End-to-End Integration Tests

- [x] 17.1 Write a Spring Boot integration test that schedules a meeting with
      invitees, validates the token via the new endpoint, joins the meeting, and
      verifies the `InviteToken` status is `USED`.
- [x] 17.2 Write a test for password change on scheduled meeting: change
      password, verify tokens are `REVOKED`, verify
      `MeetingInviteTokensInvalidatedEvent` is published.
- [x] 17.3 Write a test for resend: call resend endpoint, verify old token is
      `REVOKED` and new token is `PENDING`.
- [x] 17.4 Write a test for revoke: call revoke endpoint, verify validation
      returns `INVITE_TOKEN_REVOKED`.

## 18. Phase 3: Remove rawPassword from Events (Feature Flag Gating)

- [x] 18.1 Set `zms.invite.use-tokens=true` in non-production environments.
- [x] 18.2 Remove `rawPassword` from `MeetingInvitationsSentEvent` and
      `MeetingInvitationsSentMessage` (remove the nullable field entirely).
- [x] 18.3 Remove the backward-compatible fallback in
      `MeetingInvitationLinkFactory`.
- [x] 18.4 Update `ScheduleMeetingUseCase` to no longer pass `rawPassword` to
      the event (already null; just remove from event record).
- [x] 18.5 Run full test suite and verify no tests break due to the field
      removal. ← (verify: all tests pass, rawPassword is absent from all event
      serialization)
