package io.github.smiskinext.meetingmanagement.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.github.smiskinext.meetingmanagement.application.command.PutMeetingSettingsCommand;
import io.github.smiskinext.meetingmanagement.application.command.ResendInviteCommand;
import io.github.smiskinext.meetingmanagement.application.command.ScheduleMeetingCommand;
import io.github.smiskinext.meetingmanagement.application.response.InviteeListResponse;
import io.github.smiskinext.meetingmanagement.application.response.MeetingResponse;
import io.github.smiskinext.meetingmanagement.application.response.MeetingSettingsResponse;
import io.github.smiskinext.meetingmanagement.config.TestcontainersConfiguration;
import io.github.smiskinext.meetingmanagement.domain.MeetingError;
import io.github.smiskinext.meetingmanagement.domain.event.MeetingInvitationsSentEvent;
import io.github.smiskinext.meetingmanagement.domain.event.MeetingInviteTokensInvalidatedEvent;
import io.github.smiskinext.meetingmanagement.domain.model.AdmissionPolicy;
import io.github.smiskinext.meetingmanagement.domain.model.InviteTokenStatus;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.MeetingSettings;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.MeetingTimeRange;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.MeetingTitle;
import io.github.smiskinext.meetingmanagement.domain.port.InviteTokenRepository;
import io.github.smiskinext.meetingmanagement.domain.port.MeetingInviteeRepository;
import io.github.smiskinext.meetingmanagement.domain.port.UserGrpcServicePort;
import io.github.smiskinext.meetingmanagement.domain.port.UserGrpcServicePort.ResolvedUser;
import io.github.smiskinext.shared.domain.Result;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * End-to-end integration tests for the invite token feature.
 *
 * <p>Each test verifies a complete flow through real Spring beans and JPA persistence
 * backed by the Flyway-migrated PostgreSQL schema from Testcontainers.
 * The {@link UserGrpcServicePort} is mocked to avoid requiring a live gRPC server.
 *
 * <p>Raw tokens are captured via a {@link TestInvitationEventCapture} listener that
 * receives the in-process {@link MeetingInvitationsSentEvent} before it reaches the
 * transactional outbox. This avoids needing to deserialize outbox JSON payloads.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({
    TestcontainersConfiguration.class,
    InviteTokenFlowIntegrationTest.TestInvitationEventCapture.class
})
@TestPropertySource(
        properties = {
            "zms.invite.use-tokens=true",
            "zms.invite.token-secret=test-integration-secret-at-least-32-bytes"
        })
class InviteTokenFlowIntegrationTest {

    @Autowired
    ScheduleMeetingUseCase scheduleMeetingUseCase;

    @Autowired
    PutMeetingSettingsUseCase putMeetingSettingsUseCase;

    @Autowired
    ValidateInviteTokenUseCase validateInviteTokenUseCase;

    @Autowired
    ResendInviteUseCase resendInviteUseCase;

    @Autowired
    RevokeInviteUseCase revokeInviteUseCase;

    @Autowired
    InviteTokenRepository inviteTokenRepository;

    @Autowired
    MeetingInviteeRepository meetingInviteeRepository;

    @Autowired
    TestInvitationEventCapture eventCapture;

    @MockitoBean
    UserGrpcServicePort userGrpcServicePort;

    @BeforeEach
    void clearCapturedEvents() {
        eventCapture.clear();
    }

    /**
     * Full happy-path: schedule a meeting with an invitee, validate the generated token via the
     * validate endpoint, and verify the InviteToken record transitions to USED.
     */
    @Test
    void scheduleMeetingWithInvitee_validateToken_tokenBecomesUsed() {
        UUID hostId = UUID.randomUUID();
        UUID inviteeUserId = UUID.randomUUID();
        String inviteeEmail = "alice-" + UUID.randomUUID() + "@example.com";

        when(userGrpcServicePort.resolveUsers(List.of(inviteeEmail)))
                .thenReturn(Map.of(
                        inviteeEmail,
                        new ResolvedUser(
                                inviteeUserId, inviteeEmail, "Alice", null, null, "EMAIL")));

        Result<MeetingResponse, MeetingError> scheduleResult =
                scheduleMeetingUseCase.execute(scheduleCommand(hostId, inviteeEmail));
        assertThat(scheduleResult).isInstanceOf(Result.Success.class);
        UUID meetingId = ((Result.Success<MeetingResponse, MeetingError>) scheduleResult)
                .value()
                .id();

        var invitees = meetingInviteeRepository.findByMeetingId(meetingId);
        assertThat(invitees).hasSize(1);

        var tokens = inviteTokenRepository.findByMeetingId(meetingId);
        assertThat(tokens).hasSize(1);
        assertThat(tokens.getFirst().getStatus()).isEqualTo(InviteTokenStatus.PENDING);

        String rawToken = eventCapture.rawTokenForUser(inviteeUserId);
        assertThat(rawToken).isNotNull();

        Result<?, MeetingError> validateResult = validateInviteTokenUseCase.execute(rawToken);
        assertThat(validateResult).isInstanceOf(Result.Success.class);

        String tokenHash = tokens.getFirst().getTokenHash();
        var updatedTokenOpt = inviteTokenRepository.findByTokenHash(tokenHash);
        assertThat(updatedTokenOpt).isPresent();
        assertThat(updatedTokenOpt.get().getStatus()).isEqualTo(InviteTokenStatus.USED);
    }

    /**
     * Password change on a SCHEDULED meeting: all PENDING invite tokens are REVOKED and a
     * {@link MeetingInviteTokensInvalidatedEvent} is captured in the event capture.
     */
    @Test
    void passwordChangeOnScheduledMeeting_revokesTokens_andPublishesInvalidationEvent() {
        UUID hostId = UUID.randomUUID();
        UUID inviteeUserId = UUID.randomUUID();
        String inviteeEmail = "bob-" + UUID.randomUUID() + "@example.com";

        when(userGrpcServicePort.resolveUsers(List.of(inviteeEmail)))
                .thenReturn(Map.of(
                        inviteeEmail,
                        new ResolvedUser(inviteeUserId, inviteeEmail, "Bob", null, null, "EMAIL")));

        Result<MeetingResponse, MeetingError> scheduleResult =
                scheduleMeetingUseCase.execute(scheduleCommand(hostId, inviteeEmail));
        assertThat(scheduleResult).isInstanceOf(Result.Success.class);
        UUID meetingId = ((Result.Success<MeetingResponse, MeetingError>) scheduleResult)
                .value()
                .id();

        var tokensBefore = inviteTokenRepository.findByMeetingId(meetingId);
        assertThat(tokensBefore)
                .hasSize(1)
                .allMatch(t -> t.getStatus() == InviteTokenStatus.PENDING);

        Result<MeetingSettingsResponse, MeetingError> settingsResult =
                putMeetingSettingsUseCase.execute(
                        changePasswordCommand(meetingId, hostId, "new-password"));
        assertThat(settingsResult).isInstanceOf(Result.Success.class);
        MeetingSettingsResponse settingsResponse =
                ((Result.Success<MeetingSettingsResponse, MeetingError>) settingsResult).value();
        assertThat(settingsResponse.resendInvitesRecommended()).isTrue();
        assertThat(settingsResponse.invalidatedInviteCount()).isEqualTo(1);

        var tokensAfter = inviteTokenRepository.findByMeetingId(meetingId);
        assertThat(tokensAfter).hasSize(1);
        assertThat(tokensAfter.getFirst().getStatus()).isEqualTo(InviteTokenStatus.REVOKED);

        List<MeetingInviteTokensInvalidatedEvent> invalidationEvents =
                eventCapture.capturedInvalidationEvents();
        assertThat(invalidationEvents).hasSize(1);
        MeetingInviteTokensInvalidatedEvent invalidationEvent = invalidationEvents.getFirst();
        assertThat(invalidationEvent.aggregateId()).isEqualTo(meetingId);
        assertThat(invalidationEvent.hostId()).isEqualTo(hostId);
        assertThat(invalidationEvent.affectedInvitees()).hasSize(1);
        assertThat(invalidationEvent.affectedInvitees().getFirst().email()).isEqualTo(inviteeEmail);
    }

    /**
     * Resend: after calling the resend endpoint the original PENDING token is REVOKED and a
     * new PENDING token is created for the same invitee.
     */
    @Test
    void resendInvite_revokesOldToken_createsNewPendingToken() {
        UUID hostId = UUID.randomUUID();
        UUID inviteeUserId = UUID.randomUUID();
        String inviteeEmail = "carol-" + UUID.randomUUID() + "@example.com";

        when(userGrpcServicePort.resolveUsers(List.of(inviteeEmail)))
                .thenReturn(Map.of(
                        inviteeEmail,
                        new ResolvedUser(
                                inviteeUserId, inviteeEmail, "Carol", null, null, "EMAIL")));

        Result<MeetingResponse, MeetingError> scheduleResult =
                scheduleMeetingUseCase.execute(scheduleCommand(hostId, inviteeEmail));
        assertThat(scheduleResult).isInstanceOf(Result.Success.class);
        UUID meetingId = ((Result.Success<MeetingResponse, MeetingError>) scheduleResult)
                .value()
                .id();

        var invitees = meetingInviteeRepository.findByMeetingId(meetingId);
        assertThat(invitees).hasSize(1);
        UUID inviteeId = invitees.getFirst().getId().value();

        String originalTokenHash =
                inviteTokenRepository.findByMeetingId(meetingId).getFirst().getTokenHash();

        Result<InviteeListResponse, MeetingError> resendResult =
                resendInviteUseCase.execute(new ResendInviteCommand(meetingId, inviteeId, hostId));
        assertThat(resendResult).isInstanceOf(Result.Success.class);
        InviteeListResponse resendResponse =
                ((Result.Success<InviteeListResponse, MeetingError>) resendResult).value();
        assertThat(resendResponse.tokenStatus()).isEqualTo("PENDING");

        var originalToken = inviteTokenRepository.findByTokenHash(originalTokenHash);
        assertThat(originalToken).isPresent();
        assertThat(originalToken.get().getStatus()).isEqualTo(InviteTokenStatus.REVOKED);

        var allTokens = inviteTokenRepository.findByMeetingId(meetingId);
        assertThat(allTokens).hasSize(2);
        long pendingCount = allTokens.stream()
                .filter(t -> t.getStatus() == InviteTokenStatus.PENDING)
                .count();
        assertThat(pendingCount).isEqualTo(1);
    }

    /**
     * Revoke: after calling the revoke endpoint, validating the original token returns an
     * {@code INVITE_TOKEN_REVOKED} error.
     */
    @Test
    void revokeInvite_tokenValidationReturnsRevokedError() {
        UUID hostId = UUID.randomUUID();
        UUID inviteeUserId = UUID.randomUUID();
        String inviteeEmail = "dave-" + UUID.randomUUID() + "@example.com";

        when(userGrpcServicePort.resolveUsers(List.of(inviteeEmail)))
                .thenReturn(Map.of(
                        inviteeEmail,
                        new ResolvedUser(
                                inviteeUserId, inviteeEmail, "Dave", null, null, "EMAIL")));

        Result<MeetingResponse, MeetingError> scheduleResult =
                scheduleMeetingUseCase.execute(scheduleCommand(hostId, inviteeEmail));
        assertThat(scheduleResult).isInstanceOf(Result.Success.class);
        UUID meetingId = ((Result.Success<MeetingResponse, MeetingError>) scheduleResult)
                .value()
                .id();

        var invitees = meetingInviteeRepository.findByMeetingId(meetingId);
        assertThat(invitees).hasSize(1);
        UUID inviteeId = invitees.getFirst().getId().value();

        String rawToken = eventCapture.rawTokenForUser(inviteeUserId);
        assertThat(rawToken).isNotNull();

        Result<InviteeListResponse, MeetingError> revokeResult =
                revokeInviteUseCase.execute(meetingId, inviteeId, hostId);
        assertThat(revokeResult).isInstanceOf(Result.Success.class);
        assertThat(((Result.Success<InviteeListResponse, MeetingError>) revokeResult)
                        .value()
                        .tokenStatus())
                .isEqualTo("REVOKED");

        Result<?, MeetingError> validateResult = validateInviteTokenUseCase.execute(rawToken);
        assertThat(validateResult).isInstanceOf(Result.Failure.class);
        MeetingError error = ((Result.Failure<?, MeetingError>) validateResult).error();
        assertThat(error).isInstanceOf(MeetingError.InvalidInviteToken.class);
        assertThat(((MeetingError.InvalidInviteToken) error).errorCode())
                .isEqualTo("INVITE_TOKEN_REVOKED");
    }

    private ScheduleMeetingCommand scheduleCommand(UUID hostId, String inviteeEmail) {
        return new ScheduleMeetingCommand(
                hostId,
                MeetingTitle.of("Integration Test Meeting"),
                "Testing the invite token flow",
                MeetingTimeRange.of(
                        Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200)),
                new MeetingSettings(
                        AdmissionPolicy.MANUAL_APPROVAL, false, 50, true, true, true, true, null),
                List.of(new ScheduleMeetingCommand.InviteeInput(inviteeEmail)),
                null);
    }

    private PutMeetingSettingsCommand changePasswordCommand(
            UUID meetingId, UUID hostId, String newPassword) {
        return new PutMeetingSettingsCommand(
                meetingId,
                hostId,
                new MeetingSettings(
                        AdmissionPolicy.MANUAL_APPROVAL, false, 50, true, true, true, true, null),
                newPassword);
    }

    /**
     * Test-only Spring bean that captures in-process application events so integration tests can
     * retrieve raw invite tokens and verify event payloads without reading the outbox table JSON.
     */
    @TestConfiguration
    @Component
    static class TestInvitationEventCapture {

        private final List<MeetingInvitationsSentEvent> sentEvents = new CopyOnWriteArrayList<>();
        private final List<MeetingInviteTokensInvalidatedEvent> invalidatedEvents =
                new CopyOnWriteArrayList<>();

        @EventListener
        public void onInvitationsSent(MeetingInvitationsSentEvent event) {
            sentEvents.add(event);
        }

        @EventListener
        public void onTokensInvalidated(MeetingInviteTokensInvalidatedEvent event) {
            invalidatedEvents.add(event);
        }

        String rawTokenForUser(UUID userId) {
            return sentEvents.stream()
                    .filter(e -> e.inviteeTokens() != null && e.inviteeTokens().containsKey(userId))
                    .map(e -> e.inviteeTokens().get(userId))
                    .findFirst()
                    .orElse(null);
        }

        List<MeetingInviteTokensInvalidatedEvent> capturedInvalidationEvents() {
            return new ArrayList<>(invalidatedEvents);
        }

        void clear() {
            sentEvents.clear();
            invalidatedEvents.clear();
        }
    }
}
