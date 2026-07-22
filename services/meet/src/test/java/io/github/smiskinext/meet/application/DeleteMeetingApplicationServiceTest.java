package io.github.smiskinext.meet.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.github.smiskinext.meet.application.command.BatchDeleteMeetingsCommand;
import io.github.smiskinext.meet.application.command.DeleteMeetingCommand;
import io.github.smiskinext.meet.application.result.BatchDeleteMeetingsResult;
import io.github.smiskinext.meet.application.result.DeleteMeetingResult;
import io.github.smiskinext.meet.application.service.BatchDeleteMeetingsApplicationService;
import io.github.smiskinext.meet.application.service.DeleteMeetingApplicationService;
import io.github.smiskinext.meet.domain.MeetingError;
import io.github.smiskinext.meet.domain.model.Meeting;
import io.github.smiskinext.meet.domain.model.MeetingStatus;
import io.github.smiskinext.meet.domain.model.MeetingType;
import io.github.smiskinext.meet.domain.model.valueobject.*;
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

class DeleteMeetingApplicationServiceTest {

    @Test
    void missingOrAlreadyDeletedMeetingReturnsNotFoundWithoutSaveOrPublish() {
        MeetingRepository repository = mock(MeetingRepository.class);
        EventPublisher publisher = mock(EventPublisher.class);
        UUID meetingId = UUID.randomUUID();
        when(repository.findActiveByIdWithLock(meetingId)).thenReturn(Optional.empty());
        DeleteMeetingApplicationService service =
                new DeleteMeetingApplicationService(repository, publisher);

        Result<DeleteMeetingResult, MeetingError> result =
                service.execute(new DeleteMeetingCommand(meetingId, "tenant", "host"));

        assertThat(((Result.Failure<DeleteMeetingResult, MeetingError>) result).error())
                .isInstanceOf(MeetingError.MeetingNotFound.class);
        verify(repository, never()).save(any());
        verifyNoInteractions(publisher);
    }

    @Test
    void successfulDeleteSavesMeetingAndPublishesExactlyOneEvent() {
        MeetingRepository repository = mock(MeetingRepository.class);
        EventPublisher publisher = mock(EventPublisher.class);
        Meeting meeting = meeting(MeetingStatus.SCHEDULED);
        when(repository.findActiveByIdWithLock(meeting.getId().value()))
                .thenReturn(Optional.of(meeting));
        DeleteMeetingApplicationService service =
                new DeleteMeetingApplicationService(repository, publisher);

        Result<DeleteMeetingResult, MeetingError> result = service.execute(
                new DeleteMeetingCommand(meeting.getId().value(), "tenant", "host"));

        assertThat(result.isSuccess()).isTrue();
        assertThat(meeting.getDeletedAt()).isPresent();
        DeleteMeetingResult snapshot =
                ((Result.Success<DeleteMeetingResult, MeetingError>) result).value();
        assertThat(snapshot.meetingId()).isEqualTo(meeting.getId().value());
        assertThat(snapshot.hostId()).isEqualTo("host");
        assertThat(snapshot.shortCode()).isEqualTo("abc123def0");
        assertThat(snapshot.deletedBy()).isEqualTo("host");
        assertThat(snapshot.deletedAt()).isEqualTo(meeting.getDeletedAt().orElseThrow());
        verify(repository).save(meeting);
        verify(publisher).publishEventsOf(meeting);
    }

    @Test
    void batchDeleteAllEligibleSoftDeletesEveryMeetingWithOneEventEach() {
        MeetingRepository repository = mock(MeetingRepository.class);
        EventPublisher publisher = mock(EventPublisher.class);
        Meeting first = meeting(MeetingStatus.SCHEDULED);
        Meeting second = meeting(MeetingStatus.COMPLETED);
        when(repository.findActiveByIdWithLock(first.getId().value()))
                .thenReturn(Optional.of(first));
        when(repository.findActiveByIdWithLock(second.getId().value()))
                .thenReturn(Optional.of(second));
        BatchDeleteMeetingsApplicationService service =
                new BatchDeleteMeetingsApplicationService(repository, publisher);

        Result<BatchDeleteMeetingsResult, MeetingError> result =
                service.execute(new BatchDeleteMeetingsCommand(
                        List.of(first.getId().value(), second.getId().value()), "tenant", "host"));

        assertThat(result.isSuccess()).isTrue();
        assertThat(first.getDeletedAt()).isPresent();
        assertThat(second.getDeletedAt()).isPresent();
        BatchDeleteMeetingsResult snapshots =
                ((Result.Success<BatchDeleteMeetingsResult, MeetingError>) result).value();
        assertThat(snapshots.meetings())
                .extracting(DeleteMeetingResult::meetingId)
                .containsExactly(first.getId().value(), second.getId().value());
        assertThat(snapshots.meetings())
                .allSatisfy(item -> assertThat(item.deletedBy()).isEqualTo("host"));
        verify(repository).save(first);
        verify(repository).save(second);
        verify(publisher).publishEventsOf(first);
        verify(publisher).publishEventsOf(second);
    }

    @Test
    void batchDeleteWithOneRunningMeetingDeletesNothingAndPublishesNothing() {
        MeetingRepository repository = mock(MeetingRepository.class);
        EventPublisher publisher = mock(EventPublisher.class);
        Meeting eligible = meeting(MeetingStatus.SCHEDULED);
        Meeting running = meeting(MeetingStatus.RUNNING);
        when(repository.findActiveByIdWithLock(eligible.getId().value()))
                .thenReturn(Optional.of(eligible));
        when(repository.findActiveByIdWithLock(running.getId().value()))
                .thenReturn(Optional.of(running));
        BatchDeleteMeetingsApplicationService service =
                new BatchDeleteMeetingsApplicationService(repository, publisher);

        Result<BatchDeleteMeetingsResult, MeetingError> result =
                service.execute(new BatchDeleteMeetingsCommand(
                        List.of(eligible.getId().value(), running.getId().value()),
                        "tenant",
                        "host"));

        assertThat(((Result.Failure<BatchDeleteMeetingsResult, MeetingError>) result).error())
                .isInstanceOf(MeetingError.CannotDeleteRunningMeeting.class);
        verify(repository, never()).save(any());
        verifyNoInteractions(publisher);
    }

    @Test
    void batchDeleteWithOneNotFoundIdDeletesNothingAndPublishesNothing() {
        MeetingRepository repository = mock(MeetingRepository.class);
        EventPublisher publisher = mock(EventPublisher.class);
        Meeting eligible = meeting(MeetingStatus.SCHEDULED);
        UUID unknown = UUID.randomUUID();
        when(repository.findActiveByIdWithLock(eligible.getId().value()))
                .thenReturn(Optional.of(eligible));
        when(repository.findActiveByIdWithLock(unknown)).thenReturn(Optional.empty());
        BatchDeleteMeetingsApplicationService service =
                new BatchDeleteMeetingsApplicationService(repository, publisher);

        Result<BatchDeleteMeetingsResult, MeetingError> result =
                service.execute(new BatchDeleteMeetingsCommand(
                        List.of(eligible.getId().value(), unknown), "tenant", "host"));

        assertThat(((Result.Failure<BatchDeleteMeetingsResult, MeetingError>) result).error())
                .isInstanceOf(MeetingError.MeetingNotFound.class);
        verify(repository, never()).save(any());
        verifyNoInteractions(publisher);
    }

    @Test
    void batchDeleteWithOneNonHostIdDeletesNothingAndPublishesNothing() {
        MeetingRepository repository = mock(MeetingRepository.class);
        EventPublisher publisher = mock(EventPublisher.class);
        Meeting eligible = meeting(MeetingStatus.SCHEDULED);
        Meeting foreign = meeting(MeetingStatus.SCHEDULED);
        when(repository.findActiveByIdWithLock(eligible.getId().value()))
                .thenReturn(Optional.of(eligible));
        when(repository.findActiveByIdWithLock(foreign.getId().value()))
                .thenReturn(Optional.of(foreign));
        BatchDeleteMeetingsApplicationService service =
                new BatchDeleteMeetingsApplicationService(repository, publisher);

        Result<BatchDeleteMeetingsResult, MeetingError> result =
                service.execute(new BatchDeleteMeetingsCommand(
                        List.of(eligible.getId().value(), foreign.getId().value()),
                        "tenant",
                        "other-account"));

        assertThat(((Result.Failure<BatchDeleteMeetingsResult, MeetingError>) result).error())
                .isInstanceOf(MeetingError.NotAuthorized.class);
        verify(repository, never()).save(any());
        verifyNoInteractions(publisher);
    }

    private Meeting meeting(MeetingStatus status) {
        Instant start = Instant.now().minus(2, ChronoUnit.HOURS);
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
                MeetingTimeZone.of("Asia/Ho_Chi_Minh"),
                Email.of("host@example.com"),
                InviteeDisplayName.of("Host User"),
                "calendar@example.com",
                0,
                start.minus(1, ChronoUnit.DAYS),
                start.minus(1, ChronoUnit.DAYS),
                null,
                null,
                null,
                null);
        meeting.clearDomainEvents();
        return meeting;
    }
}
