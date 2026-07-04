package io.github.smiskinext.meetingmanagement.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.smiskinext.meetingmanagement.application.command.CancelMeetingCommand;
import io.github.smiskinext.meetingmanagement.domain.MeetingError;
import io.github.smiskinext.meetingmanagement.domain.event.MeetingCancelledEvent;
import io.github.smiskinext.meetingmanagement.domain.model.InviteeStatus;
import io.github.smiskinext.meetingmanagement.domain.model.Meeting;
import io.github.smiskinext.meetingmanagement.domain.model.MeetingInvitee;
import io.github.smiskinext.meetingmanagement.domain.model.MeetingStatus;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.InviteeDisplayName;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.InviteeId;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.InviterId;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.MeetingSettings;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.MeetingTimeRange;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.MeetingTitle;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.ShortCode;
import io.github.smiskinext.meetingmanagement.domain.port.MeetingInviteeRepository;
import io.github.smiskinext.meetingmanagement.domain.port.MeetingRepository;
import io.github.phunguy65.zms.shared.domain.Result;
import io.github.phunguy65.zms.shared.domain.valueobject.Email;
import io.github.phunguy65.zms.shared.domain.valueobject.UserId;
import java.time.Instant;
import java.util.List;
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
class CancelMeetingUseCaseTest {

    @Mock
    private MeetingRepository meetingRepository;

    @Mock
    private MeetingInviteeRepository meetingInviteeRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private CancelMeetingUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CancelMeetingUseCase(
                meetingRepository, meetingInviteeRepository, eventPublisher);
        lenient()
                .when(meetingRepository.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void returnsMeetingNotFoundWhenMissing() {
        UUID meetingId = UUID.randomUUID();
        when(meetingRepository.findById(meetingId)).thenReturn(Optional.empty());

        Result<Void, MeetingError> result =
                useCase.execute(new CancelMeetingCommand(meetingId, UUID.randomUUID()));

        assertThat(result)
                .isInstanceOfSatisfying(Result.Failure.class, failure -> assertThat(failure.error())
                        .isEqualTo(new MeetingError.MeetingNotFound(meetingId)));
        verifyNoInteractions(meetingInviteeRepository, eventPublisher);
    }

    @Test
    void returnsNotAuthorizedWhenRequesterIsNotHost() {
        Meeting meeting = scheduledMeeting();
        UUID requesterId = UUID.randomUUID();
        when(meetingRepository.findById(meeting.getId().value())).thenReturn(Optional.of(meeting));

        Result<Void, MeetingError> result =
                useCase.execute(new CancelMeetingCommand(meeting.getId().value(), requesterId));

        assertThat(result)
                .isInstanceOfSatisfying(Result.Failure.class, failure -> assertThat(failure.error())
                        .isEqualTo(new MeetingError.NotAuthorized(
                                requesterId, meeting.getHostId().value())));
        verifyNoInteractions(meetingInviteeRepository, eventPublisher);
    }

    @Test
    void returnsInvalidStatusWhenMeetingAlreadyLive() {
        Meeting meeting = scheduledMeeting();
        assertThat(meeting.start()).isInstanceOf(Result.Success.class);
        when(meetingRepository.findById(meeting.getId().value())).thenReturn(Optional.of(meeting));
        when(meetingInviteeRepository.findByMeetingId(meeting.getId().value()))
                .thenReturn(List.of());

        Result<Void, MeetingError> result = useCase.execute(new CancelMeetingCommand(
                meeting.getId().value(), meeting.getHostId().value()));

        assertThat(result).isInstanceOfSatisfying(Result.Failure.class, failure -> assertThat(
                        failure.error())
                .isEqualTo(new MeetingError.InvalidStatusTransition(
                        MeetingStatus.LIVE,
                        MeetingStatus
                                .CANCELLED)));
        verify(meetingInviteeRepository).findByMeetingId(meeting.getId().value());
        verify(eventPublisher, never()).publishEvent(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void publishesCancelledEventWithPendingAndAcceptedInviteesOnly() {
        Meeting meeting = scheduledMeeting();
        when(meetingRepository.findById(meeting.getId().value())).thenReturn(Optional.of(meeting));
        when(meetingInviteeRepository.findByMeetingId(meeting.getId().value()))
                .thenReturn(List.of(
                        invitee(meeting, InviteeStatus.PENDING, "alice@example.com", "Alice"),
                        invitee(meeting, InviteeStatus.ACCEPTED, "bob@example.com", "Bob"),
                        invitee(meeting, InviteeStatus.DECLINED, "carol@example.com", "Carol")));

        Result<Void, MeetingError> result = useCase.execute(new CancelMeetingCommand(
                meeting.getId().value(), meeting.getHostId().value()));

        assertThat(result).isInstanceOf(Result.Success.class);
        MeetingCancelledEvent event = capturedCancelledEvent();
        assertThat(event.aggregateId()).isEqualTo(meeting.getId().value());
        assertThat(event.meetingTitle()).isEqualTo("Planning Session");
        assertThat(event.meetingShortCode()).isEqualTo("ABC1234567");
        assertThat(event.startTime()).isEqualTo(Instant.parse("2026-04-03T10:00:00Z"));
        assertThat(event.invitees()).hasSize(2);
        assertThat(event.invitees())
                .extracting(MeetingCancelledEvent.InviteeInfo::email)
                .containsExactly("alice@example.com", "bob@example.com");
        assertThat(event.invitees())
                .extracting(MeetingCancelledEvent.InviteeInfo::status)
                .containsExactly("PENDING", "ACCEPTED");
    }

    @Test
    void publishesCancelledEventWithEmptyInviteesWhenNoneAreActive() {
        Meeting meeting = scheduledMeeting();
        when(meetingRepository.findById(meeting.getId().value())).thenReturn(Optional.of(meeting));
        when(meetingInviteeRepository.findByMeetingId(meeting.getId().value()))
                .thenReturn(List.of(
                        invitee(meeting, InviteeStatus.DECLINED, "carol@example.com", "Carol")));

        Result<Void, MeetingError> result = useCase.execute(new CancelMeetingCommand(
                meeting.getId().value(), meeting.getHostId().value()));

        assertThat(result).isInstanceOf(Result.Success.class);
        assertThat(capturedCancelledEvent().invitees()).isEmpty();
    }

    @Test
    void publishesCancelledEventWithNullTitleForInstantMeeting() {
        Meeting meeting = Meeting.instant(
                UserId.of(UUID.randomUUID()),
                null,
                null,
                MeetingSettings.defaults(),
                ShortCode.of("ABC1234567"));
        when(meetingRepository.findById(meeting.getId().value())).thenReturn(Optional.of(meeting));
        when(meetingInviteeRepository.findByMeetingId(meeting.getId().value()))
                .thenReturn(List.of());

        Result<Void, MeetingError> result = useCase.execute(new CancelMeetingCommand(
                meeting.getId().value(), meeting.getHostId().value()));

        assertThat(result).isInstanceOf(Result.Success.class);
        assertThat(capturedCancelledEvent().meetingTitle()).isNull();
    }

    private MeetingCancelledEvent capturedCancelledEvent() {
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(eventCaptor.capture());
        return eventCaptor.getAllValues().stream()
                .filter(MeetingCancelledEvent.class::isInstance)
                .map(MeetingCancelledEvent.class::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected cancelled event to be published"));
    }

    private static Meeting scheduledMeeting() {
        return Meeting.schedule(
                UserId.of(UUID.randomUUID()),
                MeetingTitle.of("Planning Session"),
                "Discuss roadmap",
                MeetingTimeRange.of(
                        Instant.parse("2026-04-03T10:00:00Z"),
                        Instant.parse("2026-04-03T11:00:00Z")),
                MeetingSettings.defaults(),
                ShortCode.of("ABC1234567"));
    }

    private static MeetingInvitee invitee(
            Meeting meeting, InviteeStatus status, String email, String displayName) {
        MeetingInvitee invitee = MeetingInvitee.reconstitute(
                InviteeId.of(
                        UUID.randomUUID()),
                meeting.getId(),
                InviterId.of(meeting.getHostId().value()),
                UserId.of(UUID.randomUUID()),
                Email.of(email),
                InviteeDisplayName.of(displayName),
                status,
                Instant.parse("2026-04-02T09:00:00Z"),
                null,
                null);
        return invitee;
    }
}
