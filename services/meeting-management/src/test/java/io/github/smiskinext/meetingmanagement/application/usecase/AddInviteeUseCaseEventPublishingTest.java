package io.github.smiskinext.meetingmanagement.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.smiskinext.meetingmanagement.application.command.AddInviteeCommand;
import io.github.smiskinext.meetingmanagement.application.service.InviteTokenService;
import io.github.smiskinext.meetingmanagement.domain.event.MeetingInvitationsSentEvent;
import io.github.smiskinext.meetingmanagement.domain.model.Meeting;
import io.github.smiskinext.meetingmanagement.domain.model.MeetingInvitee;
import io.github.smiskinext.meetingmanagement.domain.model.InviteeStatus;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.InviteeId;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.MeetingSettings;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.MeetingTimeRange;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.MeetingTitle;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.ShortCode;
import io.github.smiskinext.meetingmanagement.domain.port.InviteTokenRepository;
import io.github.smiskinext.meetingmanagement.domain.port.MeetingInviteeRepository;
import io.github.smiskinext.meetingmanagement.domain.port.MeetingRepository;
import io.github.smiskinext.meetingmanagement.domain.port.UserGrpcServicePort;
import io.github.smiskinext.meetingmanagement.domain.port.UserGrpcServicePort.ResolvedUser;
import io.github.smiskinext.shared.domain.Result;
import io.github.smiskinext.shared.domain.valueobject.Email;
import io.github.smiskinext.shared.domain.valueobject.MeetingId;
import io.github.smiskinext.shared.domain.valueobject.UserId;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import io.github.smiskinext.meetingmanagement.domain.model.valueobject.InviterId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Verifies that {@link AddInviteeUseCase} publishes a {@link MeetingInvitationsSentEvent}
 * whose {@code inviteeTokens} map is keyed by userId, not the internal inviteeId (DB record UUID).
 */
@ExtendWith(MockitoExtension.class)
class AddInviteeUseCaseEventPublishingTest {

    @Mock
    private MeetingRepository meetingRepository;

    @Mock
    private MeetingInviteeRepository meetingInviteeRepository;

    @Mock
    private InviteTokenRepository inviteTokenRepository;

    @Mock
    private InviteTokenService inviteTokenService;

    @Mock
    private UserGrpcServicePort userGrpcServicePort;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private AddInviteeUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new AddInviteeUseCase(
                meetingRepository,
                meetingInviteeRepository,
                inviteTokenRepository,
                inviteTokenService,
                userGrpcServicePort,
                eventPublisher);
    }

    @Test
    void publishesInvitationEventKeyedByUserId_notInviteeId() {
        UUID hostId = UUID.randomUUID();
        UUID inviteeUserId = UUID.randomUUID();

        Meeting meeting = Meeting.schedule(
                UserId.of(hostId),
                MeetingTitle.of("Token Key Test Meeting"),
                "Verify token map key is userId",
                MeetingTimeRange.of(
                        Instant.parse("2026-05-01T10:00:00Z"),
                        Instant.parse("2026-05-01T11:00:00Z")),
                MeetingSettings.defaults(),
                ShortCode.of("KEYTEST123"));

        when(meetingRepository.findByIdWithLock(meeting.getId().value()))
                .thenReturn(Optional.of(meeting));

        when(userGrpcServicePort.resolveUsers(any()))
                .thenReturn(Map.of(
                        "target@example.com",
                        new ResolvedUser(
                                inviteeUserId,
                                "target@example.com",
                                "Target User",
                                null,
                                null,
                                "EMAIL")));

        MeetingInvitee savedInvitee = MeetingInvitee.reconstitute(
                InviteeId.of(UUID.randomUUID()),
                MeetingId.of(meeting.getId().value()),
                InviterId.of(
                        hostId),
                UserId.of(inviteeUserId),
                Email.of("target@example.com"),
                null,
                InviteeStatus.PENDING,
                Instant.now(),
                null,
                null);

        when(meetingInviteeRepository.save(any())).thenReturn(savedInvitee);

        String rawToken = "sig|" + meeting.getId().value() + "|"
                + savedInvitee.getId().value() + "|9999999999";
        when(inviteTokenService.generateToken(any(), any())).thenReturn(rawToken);
        when(inviteTokenService.hashToken(rawToken)).thenReturn("hashed-token");
        when(inviteTokenService.extractExpiresAt(rawToken))
                .thenReturn(Instant.now().plusSeconds(604800));
        when(inviteTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Result<?, ?> result = useCase.execute(new AddInviteeCommand(
                meeting.getId().value(), "target@example.com", "Target User", hostId));

        assertThat(result).isInstanceOf(Result.Success.class);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(captor.capture());
        MeetingInvitationsSentEvent event = captor.getAllValues().stream()
                .filter(MeetingInvitationsSentEvent.class::isInstance)
                .map(MeetingInvitationsSentEvent.class::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected invitation event to be published"));

        assertThat(event.inviteeTokens()).isNotNull().isNotEmpty();
        assertThat(event.inviteeTokens()).containsKey(inviteeUserId);
        assertThat(event.inviteeTokens().get(inviteeUserId)).isEqualTo(rawToken);

        UUID inviteeRecordId = savedInvitee.getId().value();
        assertThat(inviteeRecordId).isNotEqualTo(inviteeUserId);
        assertThat(event.inviteeTokens()).doesNotContainKey(inviteeRecordId);
    }
}
