package io.github.smiskinext.meet.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.github.smiskinext.meet.application.command.EndMeetingCommand;
import io.github.smiskinext.meet.application.result.EndMeetingResult;
import io.github.smiskinext.meet.application.service.EndMeetingApplicationService;
import io.github.smiskinext.meet.domain.MeetingError;
import io.github.smiskinext.meet.domain.event.MeetingCompletedEvent;
import io.github.smiskinext.meet.domain.model.Meeting;
import io.github.smiskinext.meet.domain.model.MeetingStatus;
import io.github.smiskinext.meet.domain.model.MeetingType;
import io.github.smiskinext.meet.domain.model.ParticipationLog;
import io.github.smiskinext.meet.domain.model.valueobject.*;
import io.github.smiskinext.meet.domain.port.LiveKitPort;
import io.github.smiskinext.meet.domain.port.MeetingRepository;
import io.github.smiskinext.meet.domain.port.ParticipationLogRepository;
import io.github.smiskinext.shared.domain.EventPublisher;
import io.github.smiskinext.shared.domain.Result;
import io.github.smiskinext.shared.domain.valueobject.TenantId;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EndMeetingApplicationServiceTest {

    @Test
    void hostEndsRunningMeetingTransitionsToCompletedClosesLogsPublishesEvent() {
        MeetingRepository repository = mock(MeetingRepository.class);
        ParticipationLogRepository logRepository = mock(ParticipationLogRepository.class);
        EventPublisher publisher = mock(EventPublisher.class);
        LiveKitPort liveKitPort = mock(LiveKitPort.class);
        Meeting meeting = runningMeeting();
        UUID meetingId = meeting.getId().value();
        ParticipationLog activeLog = mock(ParticipationLog.class);

        when(repository.findActiveByIdWithLock(meetingId)).thenReturn(Optional.of(meeting));
        when(logRepository.findActiveByMeetingId(meetingId)).thenReturn(List.of(activeLog));
        when(repository.save(meeting)).thenReturn(meeting);
        when(liveKitPort.deleteRoom(any())).thenReturn(Result.success());

        EndMeetingApplicationService service =
                new EndMeetingApplicationService(repository, logRepository, publisher, liveKitPort);

        Result<EndMeetingResult, MeetingError> result =
                service.execute(new EndMeetingCommand(meetingId, "tenant", "host"));

        assertThat(result.isSuccess()).isTrue();
        EndMeetingResult snapshot =
                ((Result.Success<EndMeetingResult, MeetingError>) result).value();
        assertThat(snapshot.meetingId()).isEqualTo(meetingId);
        assertThat(snapshot.status()).isEqualTo(MeetingStatus.COMPLETED.name());
        assertThat(snapshot.endTime()).isNotNull();
        verify(activeLog).leave(any(Instant.class));
        verify(logRepository).save(activeLog);
        verify(repository).save(meeting);
        verify(publisher).publishEventsOf(meeting);
        assertThat(meeting.getDomainEvents())
                .hasAtLeastOneElementOfType(MeetingCompletedEvent.class);
    }

    @Test
    void nonHostReturnsNotAuthorizedWithoutSaveOrPublish() {
        MeetingRepository repository = mock(MeetingRepository.class);
        ParticipationLogRepository logRepository = mock(ParticipationLogRepository.class);
        EventPublisher publisher = mock(EventPublisher.class);
        LiveKitPort liveKitPort = mock(LiveKitPort.class);
        Meeting meeting = runningMeeting();
        when(repository.findActiveByIdWithLock(meeting.getId().value()))
                .thenReturn(Optional.of(meeting));

        EndMeetingApplicationService service =
                new EndMeetingApplicationService(repository, logRepository, publisher, liveKitPort);

        Result<EndMeetingResult, MeetingError> result = service.execute(
                new EndMeetingCommand(meeting.getId().value(), "tenant", "other-account"));

        assertThat(((Result.Failure<EndMeetingResult, MeetingError>) result).error())
                .isInstanceOf(MeetingError.NotAuthorized.class);
        verify(repository, never()).save(any());
        verifyNoInteractions(publisher);
        verifyNoInteractions(liveKitPort);
    }

    @Test
    void unknownMeetingReturnsMeetingNotFoundWithoutPublish() {
        MeetingRepository repository = mock(MeetingRepository.class);
        ParticipationLogRepository logRepository = mock(ParticipationLogRepository.class);
        EventPublisher publisher = mock(EventPublisher.class);
        LiveKitPort liveKitPort = mock(LiveKitPort.class);
        UUID meetingId = UUID.randomUUID();
        when(repository.findActiveByIdWithLock(meetingId)).thenReturn(Optional.empty());

        EndMeetingApplicationService service =
                new EndMeetingApplicationService(repository, logRepository, publisher, liveKitPort);

        Result<EndMeetingResult, MeetingError> result =
                service.execute(new EndMeetingCommand(meetingId, "tenant", "host"));

        assertThat(((Result.Failure<EndMeetingResult, MeetingError>) result).error())
                .isInstanceOf(MeetingError.MeetingNotFound.class);
        verify(repository, never()).save(any());
        verifyNoInteractions(publisher);
        verifyNoInteractions(liveKitPort);
    }

    @Test
    void endingScheduledMeetingReturnsInvalidStatusTransition() {
        MeetingRepository repository = mock(MeetingRepository.class);
        ParticipationLogRepository logRepository = mock(ParticipationLogRepository.class);
        EventPublisher publisher = mock(EventPublisher.class);
        LiveKitPort liveKitPort = mock(LiveKitPort.class);
        Meeting meeting = meetingWithStatus(MeetingStatus.SCHEDULED);
        when(repository.findActiveByIdWithLock(meeting.getId().value()))
                .thenReturn(Optional.of(meeting));

        EndMeetingApplicationService service =
                new EndMeetingApplicationService(repository, logRepository, publisher, liveKitPort);

        Result<EndMeetingResult, MeetingError> result =
                service.execute(new EndMeetingCommand(meeting.getId().value(), "tenant", "host"));

        assertThat(((Result.Failure<EndMeetingResult, MeetingError>) result).error())
                .isInstanceOf(MeetingError.InvalidStatusTransition.class);
        verify(repository, never()).save(any());
        verifyNoInteractions(publisher);
        verifyNoInteractions(liveKitPort);
    }

    @Test
    void endingAlreadyCompletedMeetingReturnsInvalidStatusTransition() {
        MeetingRepository repository = mock(MeetingRepository.class);
        ParticipationLogRepository logRepository = mock(ParticipationLogRepository.class);
        EventPublisher publisher = mock(EventPublisher.class);
        LiveKitPort liveKitPort = mock(LiveKitPort.class);
        Meeting meeting = meetingWithStatus(MeetingStatus.COMPLETED);
        when(repository.findActiveByIdWithLock(meeting.getId().value()))
                .thenReturn(Optional.of(meeting));

        EndMeetingApplicationService service =
                new EndMeetingApplicationService(repository, logRepository, publisher, liveKitPort);

        Result<EndMeetingResult, MeetingError> result =
                service.execute(new EndMeetingCommand(meeting.getId().value(), "tenant", "host"));

        assertThat(((Result.Failure<EndMeetingResult, MeetingError>) result).error())
                .isInstanceOf(MeetingError.InvalidStatusTransition.class);
        verify(repository, never()).save(any());
        verifyNoInteractions(publisher);
        verifyNoInteractions(liveKitPort);
    }

    @Test
    void endingCanceledMeetingReturnsInvalidStatusTransition() {
        MeetingRepository repository = mock(MeetingRepository.class);
        ParticipationLogRepository logRepository = mock(ParticipationLogRepository.class);
        EventPublisher publisher = mock(EventPublisher.class);
        LiveKitPort liveKitPort = mock(LiveKitPort.class);
        Meeting meeting = meetingWithStatus(MeetingStatus.CANCELED);
        when(repository.findActiveByIdWithLock(meeting.getId().value()))
                .thenReturn(Optional.of(meeting));

        EndMeetingApplicationService service =
                new EndMeetingApplicationService(repository, logRepository, publisher, liveKitPort);

        Result<EndMeetingResult, MeetingError> result =
                service.execute(new EndMeetingCommand(meeting.getId().value(), "tenant", "host"));

        assertThat(((Result.Failure<EndMeetingResult, MeetingError>) result).error())
                .isInstanceOf(MeetingError.InvalidStatusTransition.class);
        verify(repository, never()).save(any());
        verifyNoInteractions(publisher);
        verifyNoInteractions(liveKitPort);
    }

    @Test
    void completionWithNoActiveParticipationLogsStillSucceeds() {
        MeetingRepository repository = mock(MeetingRepository.class);
        ParticipationLogRepository logRepository = mock(ParticipationLogRepository.class);
        EventPublisher publisher = mock(EventPublisher.class);
        LiveKitPort liveKitPort = mock(LiveKitPort.class);
        Meeting meeting = runningMeeting();
        UUID meetingId = meeting.getId().value();

        when(repository.findActiveByIdWithLock(meetingId)).thenReturn(Optional.of(meeting));
        when(logRepository.findActiveByMeetingId(meetingId)).thenReturn(List.of());
        when(repository.save(meeting)).thenReturn(meeting);
        when(liveKitPort.deleteRoom(any())).thenReturn(Result.success());

        EndMeetingApplicationService service =
                new EndMeetingApplicationService(repository, logRepository, publisher, liveKitPort);

        Result<EndMeetingResult, MeetingError> result =
                service.execute(new EndMeetingCommand(meetingId, "tenant", "host"));

        assertThat(result.isSuccess()).isTrue();
        EndMeetingResult snapshot =
                ((Result.Success<EndMeetingResult, MeetingError>) result).value();
        assertThat(snapshot.status()).isEqualTo(MeetingStatus.COMPLETED.name());
        verify(repository).save(meeting);
        verify(publisher).publishEventsOf(meeting);
    }

    @Test
    void deleteRoomFailureDoesNotRollBackCompletion() {
        MeetingRepository repository = mock(MeetingRepository.class);
        ParticipationLogRepository logRepository = mock(ParticipationLogRepository.class);
        EventPublisher publisher = mock(EventPublisher.class);
        LiveKitPort liveKitPort = mock(LiveKitPort.class);
        Meeting meeting = runningMeeting();
        UUID meetingId = meeting.getId().value();

        when(repository.findActiveByIdWithLock(meetingId)).thenReturn(Optional.of(meeting));
        when(logRepository.findActiveByMeetingId(meetingId)).thenReturn(List.of());
        when(repository.save(meeting)).thenReturn(meeting);
        when(liveKitPort.deleteRoom(any()))
                .thenReturn(
                        Result.failure(new MeetingError.LiveKitUnavailable("connection refused")));

        EndMeetingApplicationService service =
                new EndMeetingApplicationService(repository, logRepository, publisher, liveKitPort);

        Result<EndMeetingResult, MeetingError> result =
                service.execute(new EndMeetingCommand(meetingId, "tenant", "host"));

        assertThat(result.isSuccess()).isTrue();
        EndMeetingResult snapshot =
                ((Result.Success<EndMeetingResult, MeetingError>) result).value();
        assertThat(snapshot.status()).isEqualTo(MeetingStatus.COMPLETED.name());
        verify(repository).save(meeting);
        verify(publisher).publishEventsOf(meeting);
        assertThat(meeting.getDomainEvents())
                .hasAtLeastOneElementOfType(MeetingCompletedEvent.class);
    }

    @Test
    void deleteRoomRuntimeExceptionDoesNotRollBackCompletion() {
        MeetingRepository repository = mock(MeetingRepository.class);
        ParticipationLogRepository logRepository = mock(ParticipationLogRepository.class);
        EventPublisher publisher = mock(EventPublisher.class);
        LiveKitPort liveKitPort = mock(LiveKitPort.class);
        Meeting meeting = runningMeeting();
        UUID meetingId = meeting.getId().value();

        when(repository.findActiveByIdWithLock(meetingId)).thenReturn(Optional.of(meeting));
        when(logRepository.findActiveByMeetingId(meetingId)).thenReturn(List.of());
        when(repository.save(meeting)).thenReturn(meeting);
        when(liveKitPort.deleteRoom(any()))
                .thenThrow(new RuntimeException("network serialization bug"));

        EndMeetingApplicationService service =
                new EndMeetingApplicationService(repository, logRepository, publisher, liveKitPort);

        Result<EndMeetingResult, MeetingError> result =
                service.execute(new EndMeetingCommand(meetingId, "tenant", "host"));

        assertThat(result.isSuccess()).isTrue();
        EndMeetingResult snapshot =
                ((Result.Success<EndMeetingResult, MeetingError>) result).value();
        assertThat(snapshot.status()).isEqualTo(MeetingStatus.COMPLETED.name());
        verify(repository).save(meeting);
        verify(publisher).publishEventsOf(meeting);
        assertThat(meeting.getDomainEvents())
                .hasAtLeastOneElementOfType(MeetingCompletedEvent.class);
    }

    private Meeting runningMeeting() {
        return meetingWithStatus(MeetingStatus.RUNNING);
    }

    private Meeting meetingWithStatus(MeetingStatus status) {
        Instant start = Instant.now().minus(30, ChronoUnit.MINUTES);
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
}
