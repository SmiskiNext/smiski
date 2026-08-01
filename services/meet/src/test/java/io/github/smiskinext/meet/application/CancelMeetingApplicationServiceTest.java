package io.github.smiskinext.meet.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.github.smiskinext.meet.application.command.CancelMeetingCommand;
import io.github.smiskinext.meet.application.result.CancelMeetingResult;
import io.github.smiskinext.meet.application.service.CancelMeetingApplicationService;
import io.github.smiskinext.meet.domain.MeetingError;
import io.github.smiskinext.meet.domain.model.CancelReason;
import io.github.smiskinext.meet.domain.model.Meeting;
import io.github.smiskinext.meet.domain.model.MeetingInvitee;
import io.github.smiskinext.meet.domain.model.MeetingStatus;
import io.github.smiskinext.meet.domain.model.MeetingType;
import io.github.smiskinext.meet.domain.model.valueobject.*;
import io.github.smiskinext.meet.domain.port.MeetingInviteeRepository;
import io.github.smiskinext.meet.domain.port.MeetingRepository;
import io.github.smiskinext.shared.domain.EventPublisher;
import io.github.smiskinext.shared.domain.Result;
import io.github.smiskinext.shared.domain.valueobject.TenantId;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CancelMeetingApplicationServiceTest {

    @Test
    void meetingNotFoundReturnsErrorWithoutSaveOrPublish() {
        MeetingRepository repository = mock(MeetingRepository.class);
        MeetingInviteeRepository inviteeRepository = mock(MeetingInviteeRepository.class);
        EventPublisher publisher = mock(EventPublisher.class);
        UUID meetingId = UUID.randomUUID();
        when(repository.findActiveByIdWithLock(meetingId)).thenReturn(Optional.empty());

        CancelMeetingApplicationService service =
                new CancelMeetingApplicationService(repository, inviteeRepository, publisher);

        Result<CancelMeetingResult, MeetingError> result =
                service.execute(new CancelMeetingCommand(meetingId, "tenant", "host"));

        assertThat(((Result.Failure<CancelMeetingResult, MeetingError>) result).error())
                .isInstanceOf(MeetingError.MeetingNotFound.class);
        verify(repository, never()).save(any());
        verifyNoInteractions(publisher);
    }

    @Test
    void nonHostReturnsNotAuthorizedWithoutSaveOrPublish() {
        MeetingRepository repository = mock(MeetingRepository.class);
        MeetingInviteeRepository inviteeRepository = mock(MeetingInviteeRepository.class);
        EventPublisher publisher = mock(EventPublisher.class);
        Meeting meeting = scheduledMeeting();
        when(repository.findActiveByIdWithLock(meeting.getId().value()))
                .thenReturn(Optional.of(meeting));

        CancelMeetingApplicationService service =
                new CancelMeetingApplicationService(repository, inviteeRepository, publisher);

        Result<CancelMeetingResult, MeetingError> result = service.execute(
                new CancelMeetingCommand(meeting.getId().value(), "tenant", "other-account"));

        assertThat(((Result.Failure<CancelMeetingResult, MeetingError>) result).error())
                .isInstanceOf(MeetingError.NotAuthorized.class);
        verify(repository, never()).save(any());
        verifyNoInteractions(publisher);
    }

    @Test
    void runningMeetingReturnsInvalidStatusTransitionWithoutSaveOrPublish() {
        MeetingRepository repository = mock(MeetingRepository.class);
        MeetingInviteeRepository inviteeRepository = mock(MeetingInviteeRepository.class);
        EventPublisher publisher = mock(EventPublisher.class);
        Meeting meeting = meetingWithStatus(MeetingStatus.RUNNING);
        when(repository.findActiveByIdWithLock(meeting.getId().value()))
                .thenReturn(Optional.of(meeting));
        when(inviteeRepository.findByMeetingId(meeting.getId().value())).thenReturn(List.of());

        CancelMeetingApplicationService service =
                new CancelMeetingApplicationService(repository, inviteeRepository, publisher);

        Result<CancelMeetingResult, MeetingError> result = service.execute(
                new CancelMeetingCommand(meeting.getId().value(), "tenant", "host"));

        assertThat(((Result.Failure<CancelMeetingResult, MeetingError>) result).error())
                .isInstanceOf(MeetingError.InvalidStatusTransition.class);
        verify(repository, never()).save(any());
        verifyNoInteractions(publisher);
    }

    @Test
    void successfulHostCancelSavesMeetingPublishesOneEventReturnsCorrectSnapshot() {
        MeetingRepository repository = mock(MeetingRepository.class);
        MeetingInviteeRepository inviteeRepository = mock(MeetingInviteeRepository.class);
        EventPublisher publisher = mock(EventPublisher.class);
        Meeting meeting = scheduledMeeting();
        MeetingInvitee invitee = invitee(meeting.getId().value());
        when(repository.findActiveByIdWithLock(meeting.getId().value()))
                .thenReturn(Optional.of(meeting));
        when(inviteeRepository.findByMeetingId(meeting.getId().value()))
                .thenReturn(List.of(invitee));
        when(repository.save(meeting)).thenReturn(meeting);

        CancelMeetingApplicationService service =
                new CancelMeetingApplicationService(repository, inviteeRepository, publisher);

        Result<CancelMeetingResult, MeetingError> result = service.execute(
                new CancelMeetingCommand(meeting.getId().value(), "tenant", "host"));

        assertThat(result.isSuccess()).isTrue();
        CancelMeetingResult snapshot =
                ((Result.Success<CancelMeetingResult, MeetingError>) result).value();
        assertThat(snapshot.meetingId()).isEqualTo(meeting.getId().value());
        assertThat(snapshot.status()).isEqualTo(MeetingStatus.CANCELED.name());
        assertThat(snapshot.cancelReason()).isEqualTo(CancelReason.HOST_CANCELED.name());
        assertThat(snapshot.hostId()).isEqualTo("host");
        verify(repository).save(meeting);
        verify(publisher).publishEventsOf(meeting);
    }

    private Meeting scheduledMeeting() {
        return meetingWithStatus(MeetingStatus.SCHEDULED);
    }

    private Meeting meetingWithStatus(MeetingStatus status) {
        Instant start = Instant.now().plus(1, ChronoUnit.HOURS);
        Meeting meeting = Meeting.reconstitute(
                TenantId.of("tenant"),
                MeetingId.of(UUID.randomUUID()),
                AccountId.of("host"),
                ShortCode.of("ABC123DEF0"),
                MeetingTitle.of("Title"),
                "Description",
                JiraIssueLink.of("ISS-1", "PROJ-1", "PROJ"),
                MeetingTimeRange.of(start, start.plus(1, ChronoUnit.HOURS)),
                null,
                MeetingType.SCHEDULED,
                status,
                MeetingSettings.defaults(),
                MeetingTimeZone.of("UTC"),
                Email.of("host@example.com"),
                InviteeDisplayName.of("Host User"),
                "cal-uid",
                0,
                Instant.now().minus(1, ChronoUnit.DAYS),
                Instant.now().minus(1, ChronoUnit.DAYS),
                null,
                null,
                null,
                null);
        meeting.clearDomainEvents();
        return meeting;
    }

    private MeetingInvitee invitee(UUID meetingId) {
        return MeetingInvitee.reconstitute(
                TenantId.of("tenant"),
                InviteeId.of(UUID.randomUUID()),
                MeetingId.of(meetingId),
                InviterId.of("host"),
                AccountId.of("account-1"),
                Email.of("alice@example.com"),
                InviteeDisplayName.of("Alice"),
                io.github.smiskinext.meet.domain.model.InviteeRole.REQ_PARTICIPANT,
                true,
                io.github.smiskinext.meet.domain.model.InviteeStatus.ACCEPTED,
                Instant.now().minus(1, ChronoUnit.HOURS),
                Instant.now().minus(30, ChronoUnit.MINUTES),
                null);
    }
}
