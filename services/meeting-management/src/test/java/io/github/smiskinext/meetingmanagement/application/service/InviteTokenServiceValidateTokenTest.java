package io.github.smiskinext.meetingmanagement.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.github.smiskinext.meetingmanagement.application.service.InviteTokenService.ValidationResult;
import io.github.smiskinext.meetingmanagement.domain.model.InviteToken;
import io.github.smiskinext.meetingmanagement.domain.model.InviteTokenStatus;
import io.github.smiskinext.meetingmanagement.domain.model.Meeting;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.InviteTokenId;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.InviteeId;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.MeetingSettings;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.MeetingTimeRange;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.MeetingTitle;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.ShortCode;
import io.github.smiskinext.meetingmanagement.domain.port.InviteTokenRepository;
import io.github.smiskinext.meetingmanagement.domain.port.MeetingRepository;
import io.github.phunguy65.zms.shared.domain.valueobject.MeetingId;
import io.github.phunguy65.zms.shared.domain.valueobject.UserId;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link InviteTokenService#validateToken(String)}.
 *
 * <p>Covers the DB-level rejection cases: token not found, USED status, and EXPIRED status,
 * in addition to the pre-existing REVOKED rejection.
 */
@ExtendWith(MockitoExtension.class)
class InviteTokenServiceValidateTokenTest {

    private static final String TEST_SECRET = "test-secret-at-least-32-bytes-long-ok";

    @Mock
    private InviteTokenRepository inviteTokenRepository;

    @Mock
    private MeetingRepository meetingRepository;

    private InviteTokenService service;

    @BeforeEach
    void setUp() {
        service = new InviteTokenService(TEST_SECRET, 7, inviteTokenRepository, meetingRepository);
    }

    @Test
    void returnsNotFoundWhenTokenHashIsAbsentFromDatabase() {
        MeetingId meetingId = MeetingId.of(UUID.randomUUID());
        InviteeId inviteeId = InviteeId.of(UUID.randomUUID());

        String rawToken = service.generateToken(meetingId, inviteeId);
        String tokenHash = service.hashToken(rawToken);

        when(inviteTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.empty());

        ValidationResult result = service.validateToken(rawToken);

        assertThat(result.isValid()).isFalse();
        assertThat(result)
                .isInstanceOfSatisfying(
                        ValidationResult.Invalid.class, invalid -> assertThat(invalid.errorCode())
                                .isEqualTo("INVITE_TOKEN_NOT_FOUND"));
    }

    @Test
    void returnsUsedWhenTokenStatusIsUsed() {
        MeetingId meetingId = MeetingId.of(UUID.randomUUID());
        InviteeId inviteeId = InviteeId.of(UUID.randomUUID());

        String rawToken = service.generateToken(meetingId, inviteeId);
        String tokenHash = service.hashToken(rawToken);
        Instant expiresAt = service.extractExpiresAt(rawToken);

        InviteToken usedToken = InviteToken.reconstitute(
                InviteTokenId.of(UUID.randomUUID()),
                meetingId,
                inviteeId,
                tokenHash,
                InviteTokenStatus.USED,
                expiresAt,
                Instant.now(),
                Instant.now());

        when(inviteTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(usedToken));

        ValidationResult result = service.validateToken(rawToken);

        assertThat(result.isValid()).isFalse();
        assertThat(result)
                .isInstanceOfSatisfying(
                        ValidationResult.Invalid.class,
                        invalid -> assertThat(invalid.errorCode()).isEqualTo("INVITE_TOKEN_USED"));
    }

    @Test
    void returnsExpiredWhenTokenStatusIsExpired() {
        MeetingId meetingId = MeetingId.of(UUID.randomUUID());
        InviteeId inviteeId = InviteeId.of(UUID.randomUUID());

        String rawToken = service.generateToken(meetingId, inviteeId);
        String tokenHash = service.hashToken(rawToken);
        Instant expiresAt = service.extractExpiresAt(rawToken);

        InviteToken expiredToken = InviteToken.reconstitute(
                InviteTokenId.of(UUID.randomUUID()),
                meetingId,
                inviteeId,
                tokenHash,
                InviteTokenStatus.EXPIRED,
                expiresAt,
                Instant.now(),
                Instant.now());

        when(inviteTokenRepository.findByTokenHash(tokenHash))
                .thenReturn(Optional.of(expiredToken));

        ValidationResult result = service.validateToken(rawToken);

        assertThat(result.isValid()).isFalse();
        assertThat(result)
                .isInstanceOfSatisfying(
                        ValidationResult.Invalid.class, invalid -> assertThat(invalid.errorCode())
                                .isEqualTo("INVITE_TOKEN_EXPIRED"));
    }

    @Test
    void returnsRevokedWhenTokenStatusIsRevoked() {
        MeetingId meetingId = MeetingId.of(UUID.randomUUID());
        InviteeId inviteeId = InviteeId.of(UUID.randomUUID());

        String rawToken = service.generateToken(meetingId, inviteeId);
        String tokenHash = service.hashToken(rawToken);
        Instant expiresAt = service.extractExpiresAt(rawToken);

        InviteToken revokedToken = InviteToken.reconstitute(
                InviteTokenId.of(UUID.randomUUID()),
                meetingId,
                inviteeId,
                tokenHash,
                InviteTokenStatus.REVOKED,
                expiresAt,
                Instant.now(),
                Instant.now());

        when(inviteTokenRepository.findByTokenHash(tokenHash))
                .thenReturn(Optional.of(revokedToken));

        ValidationResult result = service.validateToken(rawToken);

        assertThat(result.isValid()).isFalse();
        assertThat(result)
                .isInstanceOfSatisfying(
                        ValidationResult.Invalid.class, invalid -> assertThat(invalid.errorCode())
                                .isEqualTo("INVITE_TOKEN_REVOKED"));
    }

    @Test
    void returnsValidWhenTokenIsPendingAndMeetingIsScheduled() {
        MeetingId meetingId = MeetingId.of(UUID.randomUUID());
        InviteeId inviteeId = InviteeId.of(UUID.randomUUID());

        String rawToken = service.generateToken(meetingId, inviteeId);
        String tokenHash = service.hashToken(rawToken);
        Instant expiresAt = service.extractExpiresAt(rawToken);

        InviteToken pendingToken = InviteToken.reconstitute(
                InviteTokenId.of(UUID.randomUUID()),
                meetingId,
                inviteeId,
                tokenHash,
                InviteTokenStatus.PENDING,
                expiresAt,
                Instant.now(),
                Instant.now());

        Meeting scheduledMeeting = Meeting.schedule(
                UserId.of(UUID.randomUUID()),
                MeetingTitle.of("Scheduled Meeting"),
                "Agenda",
                MeetingTimeRange.of(
                        Instant.parse("2026-06-01T10:00:00Z"),
                        Instant.parse("2026-06-01T11:00:00Z")),
                MeetingSettings.defaults(),
                ShortCode.of("VALID12345"));

        when(inviteTokenRepository.findByTokenHash(tokenHash))
                .thenReturn(Optional.of(pendingToken));
        when(meetingRepository.findById(meetingId.value()))
                .thenReturn(Optional.of(scheduledMeeting));

        ValidationResult result = service.validateToken(rawToken);

        assertThat(result.isValid()).isTrue();
        assertThat(result).isInstanceOf(ValidationResult.Valid.class);
    }
}
