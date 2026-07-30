package io.github.smiskinext.meet.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.github.smiskinext.meet.application.service.NoShowMeetingCancelerApplicationService;
import io.github.smiskinext.meet.domain.model.CancelReason;
import io.github.smiskinext.meet.domain.model.Meeting;
import io.github.smiskinext.meet.domain.model.MeetingStatus;
import io.github.smiskinext.meet.domain.model.MeetingType;
import io.github.smiskinext.meet.domain.model.valueobject.*;
import io.github.smiskinext.meet.domain.port.MeetingInviteeRepository;
import io.github.smiskinext.meet.domain.port.MeetingRepository;
import io.github.smiskinext.shared.domain.EventPublisher;
import io.github.smiskinext.shared.domain.valueobject.TenantId;
import io.github.smiskinext.shared.infrastructure.tenancy.TenantContext;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class NoShowMeetingCancelerTest {

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void expiredScheduledMeetingIsCanceledWithNoShowAndEventPublished() {
        MeetingRepository repository = mock(MeetingRepository.class);
        MeetingInviteeRepository inviteeRepository = mock(MeetingInviteeRepository.class);
        EventPublisher publisher = mock(EventPublisher.class);

        Meeting meeting = scheduledMeeting();
        when(repository.findActiveByIdWithLock(meeting.getId().value()))
                .thenReturn(Optional.of(meeting));
        when(inviteeRepository.findByMeetingId(meeting.getId().value())).thenReturn(List.of());
        when(repository.save(meeting)).thenReturn(meeting);

        NoShowMeetingCancelerApplicationService canceler =
                new NoShowMeetingCancelerApplicationService(
                        repository, inviteeRepository, publisher);

        canceler.cancelExpiredMeeting(meeting.getId().value(), "tenant");

        assertThat(meeting.getStatus()).isEqualTo(MeetingStatus.CANCELED);
        assertThat(meeting.getCancelReason()).contains(CancelReason.NO_SHOW);
        verify(repository).save(meeting);
        verify(publisher).publishEventsOf(meeting);
    }

    @Test
    void meetingAlreadyTransitionedIsSkippedWithoutSaveOrPublish() {
        MeetingRepository repository = mock(MeetingRepository.class);
        MeetingInviteeRepository inviteeRepository = mock(MeetingInviteeRepository.class);
        EventPublisher publisher = mock(EventPublisher.class);

        for (MeetingStatus nonScheduled : new MeetingStatus[] {
            MeetingStatus.RUNNING, MeetingStatus.COMPLETED, MeetingStatus.CANCELED
        }) {
            Meeting meeting = meetingWithStatus(nonScheduled);
            when(repository.findActiveByIdWithLock(meeting.getId().value()))
                    .thenReturn(Optional.of(meeting));
            when(inviteeRepository.findByMeetingId(meeting.getId().value())).thenReturn(List.of());

            NoShowMeetingCancelerApplicationService canceler =
                    new NoShowMeetingCancelerApplicationService(
                            repository, inviteeRepository, publisher);

            canceler.cancelExpiredMeeting(meeting.getId().value(), "tenant");

            assertThat(meeting.getStatus()).isEqualTo(nonScheduled);
            verify(repository, never()).save(meeting);
            verifyNoInteractions(publisher);

            clearInvocations(repository, inviteeRepository, publisher);
        }
    }

    @Test
    void tenantContextIsSetBeforeProcessingAndClearedAfterEachMeetingEvenOnException() {
        MeetingRepository repository = mock(MeetingRepository.class);
        MeetingInviteeRepository inviteeRepository = mock(MeetingInviteeRepository.class);
        EventPublisher publisher = mock(EventPublisher.class);

        UUID meetingId = UUID.randomUUID();
        when(repository.findActiveByIdWithLock(meetingId))
                .thenThrow(new RuntimeException("simulated failure"));

        NoShowMeetingCancelerApplicationService canceler =
                new NoShowMeetingCancelerApplicationService(
                        repository, inviteeRepository, publisher);

        try {
            canceler.cancelExpiredMeeting(meetingId, "test-tenant");
        } catch (RuntimeException ignored) {
        }

        assertThat(TenantContext.getCurrentTenant()).isEqualTo(TenantContext.DEFAULT_TENANT);
    }

    @Test
    void cancelAllExpiredQueriesRepositoryAndCancelsEachRow() {
        MeetingRepository repository = mock(MeetingRepository.class);
        MeetingInviteeRepository inviteeRepository = mock(MeetingInviteeRepository.class);
        EventPublisher publisher = mock(EventPublisher.class);

        Meeting first = scheduledMeeting();
        Meeting second = scheduledMeeting();
        when(repository.findScheduledExpiredAcrossTenants(anyInt(), any()))
                .thenReturn(List.of(
                        new MeetingRepository.MeetingIdAndTenant(first.getId().value(), "tenant"),
                        new MeetingRepository.MeetingIdAndTenant(
                                second.getId().value(), "tenant")));
        when(repository.findActiveByIdWithLock(first.getId().value()))
                .thenReturn(Optional.of(first));
        when(repository.findActiveByIdWithLock(second.getId().value()))
                .thenReturn(Optional.of(second));
        when(inviteeRepository.findByMeetingId(any())).thenReturn(List.of());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NoShowMeetingCancelerApplicationService canceler =
                new NoShowMeetingCancelerApplicationService(
                        repository, inviteeRepository, publisher);

        canceler.cancelAllExpired(100);

        assertThat(first.getStatus()).isEqualTo(MeetingStatus.CANCELED);
        assertThat(second.getStatus()).isEqualTo(MeetingStatus.CANCELED);
        verify(publisher).publishEventsOf(first);
        verify(publisher).publishEventsOf(second);
    }

    private Meeting scheduledMeeting() {
        return meetingWithStatus(MeetingStatus.SCHEDULED);
    }

    private Meeting meetingWithStatus(MeetingStatus status) {
        Instant start = Instant.now().minus(3, ChronoUnit.HOURS);
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
