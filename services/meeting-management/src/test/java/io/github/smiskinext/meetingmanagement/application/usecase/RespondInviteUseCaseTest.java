package io.github.smiskinext.meetingmanagement.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.smiskinext.meetingmanagement.application.command.InviteeResponseType;
import io.github.smiskinext.meetingmanagement.application.command.RespondInviteCommand;
import io.github.smiskinext.meetingmanagement.application.response.InviteeRespondResponse;
import io.github.smiskinext.meetingmanagement.domain.MeetingError;
import io.github.smiskinext.meetingmanagement.domain.event.InviteeAcceptedEvent;
import io.github.smiskinext.meetingmanagement.domain.event.InviteeDeclinedEvent;
import io.github.smiskinext.meetingmanagement.domain.model.InviteeStatus;
import io.github.smiskinext.meetingmanagement.domain.model.Meeting;
import io.github.smiskinext.meetingmanagement.domain.model.MeetingInvitee;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.InviterId;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.MeetingSettings;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.MeetingTimeRange;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.MeetingTitle;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.ShortCode;
import io.github.smiskinext.meetingmanagement.domain.port.MeetingInviteeRepository;
import io.github.smiskinext.meetingmanagement.domain.port.MeetingRepository;
import io.github.phunguy65.zms.shared.domain.Result;
import io.github.phunguy65.zms.shared.domain.valueobject.Email;
import io.github.phunguy65.zms.shared.domain.valueobject.MeetingId;
import io.github.phunguy65.zms.shared.domain.valueobject.UserId;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class RespondInviteUseCaseTest {

    @Mock
    MeetingRepository meetingRepository;

    @Mock
    MeetingInviteeRepository meetingInviteeRepository;

    @Mock
    ApplicationEventPublisher eventPublisher;

    RespondInviteUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new RespondInviteUseCase(
                meetingRepository, meetingInviteeRepository, eventPublisher);
    }

    @Test
    void execute_acceptsPendingInviteAndPublishesEvent() {
        UUID hostId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Meeting meeting = meeting(hostId);
        MeetingInvitee invitee = invitee(meeting, hostId, userId);
        when(meetingRepository.findById(meeting.getId().value())).thenReturn(Optional.of(meeting));
        when(meetingInviteeRepository.findByMeetingIdAndUserId(meeting.getId().value(), userId))
                .thenReturn(Optional.of(invitee));
        when(meetingInviteeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Result<InviteeRespondResponse, MeetingError> result =
                useCase.execute(new RespondInviteCommand(
                        meeting.getId().value(), userId, userId, InviteeResponseType.ACCEPTED));

        assertThat(result).isInstanceOf(Result.Success.class);
        InviteeRespondResponse response =
                ((Result.Success<InviteeRespondResponse, MeetingError>) result).value();
        assertThat(response.status()).isEqualTo(InviteeStatus.ACCEPTED);
        assertThat(response.respondedAt()).isNotNull();
        verify(meetingInviteeRepository).save(invitee);
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(InviteeAcceptedEvent.class);
    }

    @Test
    void execute_declinesPendingInviteAndPublishesEvent() {
        UUID hostId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Meeting meeting = meeting(hostId);
        MeetingInvitee invitee = invitee(meeting, hostId, userId);
        when(meetingRepository.findById(meeting.getId().value())).thenReturn(Optional.of(meeting));
        when(meetingInviteeRepository.findByMeetingIdAndUserId(meeting.getId().value(), userId))
                .thenReturn(Optional.of(invitee));
        when(meetingInviteeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Result<InviteeRespondResponse, MeetingError> result =
                useCase.execute(new RespondInviteCommand(
                        meeting.getId().value(), userId, userId, InviteeResponseType.DECLINED));

        assertThat(result).isInstanceOf(Result.Success.class);
        InviteeRespondResponse response =
                ((Result.Success<InviteeRespondResponse, MeetingError>) result).value();
        assertThat(response.status()).isEqualTo(InviteeStatus.DECLINED);
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(InviteeDeclinedEvent.class);
    }

    @Test
    void execute_declinesAcceptedInviteAndPublishesEvent() {
        UUID hostId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Meeting meeting = meeting(hostId);
        MeetingInvitee invitee = invitee(meeting, hostId, userId);
        invitee.accept();
        invitee.clearDomainEvents();
        when(meetingRepository.findById(meeting.getId().value())).thenReturn(Optional.of(meeting));
        when(meetingInviteeRepository.findByMeetingIdAndUserId(meeting.getId().value(), userId))
                .thenReturn(Optional.of(invitee));
        when(meetingInviteeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Result<InviteeRespondResponse, MeetingError> result =
                useCase.execute(new RespondInviteCommand(
                        meeting.getId().value(), userId, userId, InviteeResponseType.DECLINED));

        assertThat(result).isInstanceOf(Result.Success.class);
        InviteeRespondResponse response =
                ((Result.Success<InviteeRespondResponse, MeetingError>) result).value();
        assertThat(response.status()).isEqualTo(InviteeStatus.DECLINED);
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(InviteeDeclinedEvent.class);
    }

    @Test
    void execute_returnsNotOwnerForWrongScope() {
        UUID userId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();

        Result<InviteeRespondResponse, MeetingError> result =
                useCase.execute(new RespondInviteCommand(
                        UUID.randomUUID(), userId, requesterId, InviteeResponseType.ACCEPTED));

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<InviteeRespondResponse, MeetingError>) result).error())
                .isInstanceOf(MeetingError.NotOwner.class);
        verifyNoInteractions(meetingRepository, meetingInviteeRepository, eventPublisher);
    }

    @Test
    void execute_returnsConflictForInvalidTransition() {
        UUID hostId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Meeting meeting = meeting(hostId);
        MeetingInvitee invitee = invitee(meeting, hostId, userId);
        invitee.accept();
        when(meetingRepository.findById(meeting.getId().value())).thenReturn(Optional.of(meeting));
        when(meetingInviteeRepository.findByMeetingIdAndUserId(meeting.getId().value(), userId))
                .thenReturn(Optional.of(invitee));

        Result<InviteeRespondResponse, MeetingError> result =
                useCase.execute(new RespondInviteCommand(
                        meeting.getId().value(), userId, userId, InviteeResponseType.ACCEPTED));

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<InviteeRespondResponse, MeetingError>) result).error())
                .isInstanceOf(MeetingError.InvalidInviteeTransition.class);
        verifyNoInteractions(eventPublisher);
    }

    private Meeting meeting(UUID hostId) {
        return Meeting.schedule(
                UserId.of(hostId),
                MeetingTitle.of("Planning"),
                null,
                MeetingTimeRange.of(
                        Instant.parse("2026-05-01T10:00:00Z"),
                        Instant.parse("2026-05-01T11:00:00Z")),
                MeetingSettings.defaults(),
                ShortCode.of("ABC123"));
    }

    private MeetingInvitee invitee(Meeting meeting, UUID hostId, UUID userId) {
        return MeetingInvitee.create(
                MeetingId.of(meeting.getId().value()),
                InviterId.of(hostId),
                UserId.of(userId),
                Email.of("alice@example.com"),
                null);
    }
}
