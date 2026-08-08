package io.github.smiskinext.meet.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.github.smiskinext.meet.application.command.UpdateMeetingCommand;
import io.github.smiskinext.meet.application.result.UpdateMeetingResult;
import io.github.smiskinext.meet.application.service.UpdateMeetingApplicationService;
import io.github.smiskinext.meet.domain.MeetingError;
import io.github.smiskinext.meet.domain.model.Meeting;
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
import org.junit.jupiter.api.Test;

class UpdateMeetingApplicationServiceTest {

    @Test
    void successfulUpdateUsesLockedLookupSavesAndPublishes() {
        MeetingRepository repository = mock(MeetingRepository.class);
        MeetingInviteeRepository inviteeRepository = mock(MeetingInviteeRepository.class);
        EventPublisher publisher = mock(EventPublisher.class);
        Meeting meeting = meeting();
        when(repository.findByIdWithLock(meeting.getId().value())).thenReturn(Optional.of(meeting));
        when(inviteeRepository.findByMeetingId(meeting.getId().value())).thenReturn(List.of());
        UpdateMeetingApplicationService service =
                new UpdateMeetingApplicationService(repository, inviteeRepository, publisher);

        Result<UpdateMeetingResult, MeetingError> result =
                service.execute(command(meeting, "host"));

        assertThat(result.isSuccess()).isTrue();
        assertThat(((Result.Success<UpdateMeetingResult, MeetingError>) result)
                        .value()
                        .title())
                .isEqualTo("Updated");
        verify(repository).save(meeting);
        verify(publisher).publishEventsOf(meeting);
    }

    @Test
    void missingAndUnauthorizedUpdatesPersistAndPublishNothing() {
        MeetingRepository repository = mock(MeetingRepository.class);
        MeetingInviteeRepository inviteeRepository = mock(MeetingInviteeRepository.class);
        EventPublisher publisher = mock(EventPublisher.class);
        Meeting meeting = meeting();
        UpdateMeetingApplicationService service =
                new UpdateMeetingApplicationService(repository, inviteeRepository, publisher);

        Result<UpdateMeetingResult, MeetingError> missing =
                service.execute(command(meeting, "host"));
        assertThat(((Result.Failure<UpdateMeetingResult, MeetingError>) missing).error())
                .isInstanceOf(MeetingError.MeetingNotFound.class);

        when(repository.findByIdWithLock(meeting.getId().value())).thenReturn(Optional.of(meeting));
        when(inviteeRepository.findByMeetingId(meeting.getId().value())).thenReturn(List.of());
        Result<UpdateMeetingResult, MeetingError> unauthorized =
                service.execute(command(meeting, "other"));
        assertThat(((Result.Failure<UpdateMeetingResult, MeetingError>) unauthorized).error())
                .isInstanceOf(MeetingError.NotAuthorized.class);
        verify(repository, never()).save(any());
        verifyNoInteractions(publisher);
    }

    @Test
    void terminalMeetingsAcceptInformationOnlyUpdate() {
        for (MeetingStatus status :
                new MeetingStatus[] {MeetingStatus.COMPLETED, MeetingStatus.CANCELED}) {
            MeetingRepository repository = mock(MeetingRepository.class);
            MeetingInviteeRepository inviteeRepository = mock(MeetingInviteeRepository.class);
            EventPublisher publisher = mock(EventPublisher.class);
            Meeting meeting = reconstituted(status);
            when(repository.findByIdWithLock(meeting.getId().value()))
                    .thenReturn(Optional.of(meeting));
            when(inviteeRepository.findByMeetingId(meeting.getId().value())).thenReturn(List.of());
            UpdateMeetingApplicationService service =
                    new UpdateMeetingApplicationService(repository, inviteeRepository, publisher);

            Result<UpdateMeetingResult, MeetingError> result =
                    service.execute(infoOnlyCommand(meeting, "host"));

            assertThat(result.isSuccess()).isTrue();
            UpdateMeetingResult value =
                    ((Result.Success<UpdateMeetingResult, MeetingError>) result).value();
            assertThat(value.title()).isEqualTo("Updated");
            assertThat(value.status()).isEqualTo(status.name());
            assertThat(value.issueLink().issueId()).isEqualTo("ISS-2");
            verify(repository).save(meeting);
            verify(publisher).publishEventsOf(meeting);
        }
    }

    @Test
    void terminalMeetingsRejectZoneChangeWithoutPersistingOrPublishing() {
        for (MeetingStatus status :
                new MeetingStatus[] {MeetingStatus.COMPLETED, MeetingStatus.CANCELED}) {
            MeetingRepository repository = mock(MeetingRepository.class);
            MeetingInviteeRepository inviteeRepository = mock(MeetingInviteeRepository.class);
            EventPublisher publisher = mock(EventPublisher.class);
            Meeting meeting = reconstituted(status);
            when(repository.findByIdWithLock(meeting.getId().value()))
                    .thenReturn(Optional.of(meeting));
            when(inviteeRepository.findByMeetingId(meeting.getId().value())).thenReturn(List.of());
            UpdateMeetingApplicationService service =
                    new UpdateMeetingApplicationService(repository, inviteeRepository, publisher);

            Result<UpdateMeetingResult, MeetingError> result =
                    service.execute(zoneChangeCommand(meeting, "host"));

            assertThat(((Result.Failure<UpdateMeetingResult, MeetingError>) result).error())
                    .isInstanceOf(MeetingError.InvalidStatusTransition.class);
            assertThat(meeting.getTitle().value()).isEqualTo("Title");
            verify(repository, never()).save(any());
            verifyNoInteractions(publisher);
        }
    }

    @Test
    void runningMeetingRejectsZoneChangeWithoutPersistingOrPublishing() {
        MeetingRepository repository = mock(MeetingRepository.class);
        MeetingInviteeRepository inviteeRepository = mock(MeetingInviteeRepository.class);
        EventPublisher publisher = mock(EventPublisher.class);
        Meeting meeting = reconstituted(MeetingStatus.RUNNING);
        when(repository.findByIdWithLock(meeting.getId().value())).thenReturn(Optional.of(meeting));
        when(inviteeRepository.findByMeetingId(meeting.getId().value())).thenReturn(List.of());
        UpdateMeetingApplicationService service =
                new UpdateMeetingApplicationService(repository, inviteeRepository, publisher);

        Result<UpdateMeetingResult, MeetingError> result =
                service.execute(zoneChangeCommand(meeting, "host"));

        assertThat(((Result.Failure<UpdateMeetingResult, MeetingError>) result).error())
                .isInstanceOf(MeetingError.InvalidStatusTransition.class);
        verify(repository, never()).save(any());
        verifyNoInteractions(publisher);
    }

    private UpdateMeetingCommand command(Meeting meeting, String accountId) {
        MeetingTimeRange range = meeting.getTimeRange().orElseThrow();
        return new UpdateMeetingCommand(
                meeting.getId().value(),
                "tenant",
                accountId,
                "Updated",
                "Updated description",
                new UpdateMeetingCommand.IssueLink("ISS-2", "PROJ-2", "PROJ"),
                "UTC",
                new UpdateMeetingCommand.TimeRange(range.start(), range.end()));
    }

    private UpdateMeetingCommand infoOnlyCommand(Meeting meeting, String accountId) {
        MeetingTimeRange range = meeting.getTimeRange().orElseThrow();
        return new UpdateMeetingCommand(
                meeting.getId().value(),
                "tenant",
                accountId,
                "Updated",
                "Updated description",
                new UpdateMeetingCommand.IssueLink("ISS-2", "PROJ-2", "PROJ"),
                meeting.getTimeZone().value(),
                new UpdateMeetingCommand.TimeRange(range.start(), range.end()));
    }

    private UpdateMeetingCommand zoneChangeCommand(Meeting meeting, String accountId) {
        MeetingTimeRange range = meeting.getTimeRange().orElseThrow();
        return new UpdateMeetingCommand(
                meeting.getId().value(),
                "tenant",
                accountId,
                "Updated",
                "Updated description",
                new UpdateMeetingCommand.IssueLink("ISS-2", "PROJ-2", "PROJ"),
                "UTC",
                new UpdateMeetingCommand.TimeRange(range.start(), range.end()));
    }

    private Meeting reconstituted(MeetingStatus status) {
        Instant start = Instant.now().minus(2, ChronoUnit.HOURS);
        return Meeting.reconstitute(
                TenantId.of("tenant"),
                MeetingId.of(java.util.UUID.randomUUID()),
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
    }

    private Meeting meeting() {
        Instant start = Instant.now().plus(1, ChronoUnit.HOURS);
        Result<Meeting, MeetingError> result = Meeting.schedule(
                TenantId.of("tenant"),
                AccountId.of("host"),
                MeetingTitle.of("Title"),
                "Description",
                JiraIssueLink.of("ISS-1", "PROJ-1", "PROJ"),
                MeetingTimeRange.of(start, start.plus(1, ChronoUnit.HOURS)),
                MeetingSettings.defaults(),
                MeetingTimeZone.of("Asia/Ho_Chi_Minh"),
                Email.of("host@example.com"),
                InviteeDisplayName.of("Host User"),
                ShortCode.of("ABC123DEF0"));
        Meeting meeting = ((Result.Success<Meeting, MeetingError>) result).value();
        meeting.clearDomainEvents();
        return meeting;
    }
}
