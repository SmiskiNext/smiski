## [2026-04-29] Round 1 (from apply auto-verify)

### Verifier

- Fixed: Duplicate static factory methods in `MeetingSettingsResponse.java` —
  lines 65-98 contained orphaned `from()` methods outside the record's closing
  brace, which would cause a compilation error. Removed the duplicate block,
  keeping a single `from(MeetingSettings)` and `from(MeetingSettings, int)`
  inside the record.
- Fixed: Initial `InviteTokenFlowIntegrationTest` used a non-deterministic
  `findRawTokenForHash` helper that attempted to regenerate HMAC tokens (which
  embed a time-based expiry epoch and cannot be reproduced). Replaced with
  `TestInvitationEventCapture` — a `@TestConfiguration @Component` that uses
  `@EventListener` to capture in-process `MeetingInvitationsSentEvent` events
  and retrieve raw tokens from the event payload. This approach is reliable and
  aligned with the existing test infrastructure.
- Fixed: `InviteTokenFlowIntegrationTest` initially referenced an unused
  `OutboxEventJpaRepository` import and field. Removed after redesigning the
  test to use the event capture approach.
- Fixed: `MeetingError.InvalidInviteToken.reason()` field reference in
  integration test — the actual record field is named `errorCode()`, not
  `reason()`. Corrected the assertion.

## [2026-04-29] Round 2 (from apply auto-verify)

### Verifier

- Fixed: inviteeTokens map key mismatch in `ScheduleMeetingUseCase.java` —
  changed key from `invitee.getId().value()` (InviteeId, the DB record UUID) to
  `invitee.getUserId().map(UserId::value).ifPresent(...)` (UserId). The
  notification consumer (`SendMeetingInvitationEmailUseCase`) looks up tokens by
  `invitee.userId()` from `InviteeInfo`, so the map key must be the userId, not
  the inviteeId.
- Fixed: same inviteeTokens map key mismatch in `ResendInviteUseCase.java` —
  replaced `Map.of(invitee.getId().value(), rawToken)` with a mutable HashMap
  keyed by userId, populated only when the invitee has a userId.
- Fixed: Added `import java.util.HashMap` to `ResendInviteUseCase.java`.
- Fixed: Flyway CHECK constraint
  `invite_tokens_expires_at_check CHECK (expires_at > NOW())` breaks UPDATE
  operations on expired tokens during revocation. Created new migration
  `V9__drop_invite_tokens_expires_at_check.sql` to drop the constraint. Did not
  modify V7.
- Fixed: Flyway rollback scripts `R7__rollback_invite_tokens_table.sql` and
  `R8__rollback_invite_token_id.sql` used the `R` prefix which Flyway treats as
  repeatable migrations (auto-applied when checksum changes). Moved both files
  from `db/migration/` to `db/rollback/` so they serve as manual rollback
  references without being auto-executed by Flyway.
- Fixed:
  `SendMeetingInvitationEmailUseCaseTest.usesTokenBasedLinkWhenInviteTokenIsAvailable`
  — added distinct UUIDs (`inviteeRecordId` and `userId`) to expose the
  key-mismatch bug. The map key and `InviteeInfo.userId()` both use `userId`;
  `inviteeRecordId` is asserted to be different to document the distinction.
  Added `assertThat` import.
- Fixed:
  `ScheduleMeetingUseCaseEventPublishingTest.withTokensEnabled_generatesTokenPerInviteeAndIncludesInEvent`
  — renamed `inviteeId` to `inviteeUserId` (the resolved user UUID) to
  distinguish it from the DB record ID. Added assertion that
  `event.inviteeTokens()` is keyed by `inviteeUserId` (not the record ID).
- Fixed:
  `MeetingInvitationsSentConsumerTest.logsEventIdWithoutExposingInviteTokens` —
  used distinct UUIDs (`inviteeRecordId` vs `userId`). Replaced
  `OutputCaptureExtension` (which cannot capture SLF4J/Log4j2 output in unit
  tests) with a Log4j2 `AbstractAppender` attached directly to the consumer's
  logger. The test now reliably asserts the event ID is logged and the raw token
  is not.
- Fixed: Added two new Android unit tests to `ScheduleViewModelTest`:
  `updateMeetingSettings_whenResendRecommended_emitsInvalidatedCountViaResendInvitesPrompt`
  verifies that when `UpdateMeetingSettingsUseCase` returns
  `resendInvitesRecommended=true`, the ViewModel emits the invalidated count via
  `resendInvitesPrompt`;
  `updateMeetingSettings_whenResendNotRecommended_doesNotEmitResendInvitesPrompt`
  verifies no prompt is emitted when the flag is false.

## [2026-04-29] Round 3 (from apply auto-verify)

### Verifier

- Fixed: `AddInviteeUseCase.java` line 118 — replaced
  `Map.of(invitee.getId().value(), rawToken)` with a mutable `HashMap` keyed by
  `userId` (via `invitee.getUserId().map(UserId::value).ifPresent(...)`),
  matching the pattern already applied to `ResendInviteUseCase` and
  `ScheduleMeetingUseCase`. Added `import java.util.HashMap`.
- Fixed: Stale Javadoc in `MeetingInvitationsSentMessage.java` — updated
  `(inviteeId → raw token)` to `(userId → raw token)`.
- Fixed: `InviteTokenService.validateToken` — added rejection for
  `storedToken.isEmpty()` with error code `INVITE_TOKEN_NOT_FOUND`, and for
  `USED` and `EXPIRED` statuses in addition to the pre-existing `REVOKED` check.
- Fixed: Stale Javadoc in `ScheduleViewModelTest.java` — updated from
  `Unit tests for ScheduleViewModel#cancelMeeting(). Covers guard conditions and success/failure paths.`
  to reflect broader scope including `updateMeetingSettings` resend-invite
  prompt flows.
- Added: `AddInviteeUseCaseEventPublishingTest.java` — new unit test class
  verifying the `inviteeTokens` map is keyed by userId (not inviteeId/DB record
  UUID), using distinct UUIDs to prevent masking. Asserts that the map contains
  the inviteeUserId key and does not contain the inviteeRecordId key.
- Added: `InviteTokenServiceValidateTokenTest.java` — new unit test class
  covering all DB-level rejection cases: `INVITE_TOKEN_NOT_FOUND` (empty
  Optional), `INVITE_TOKEN_USED`, `INVITE_TOKEN_EXPIRED`,
  `INVITE_TOKEN_REVOKED`, plus a passing case for a `PENDING` token against a
  scheduled meeting.
