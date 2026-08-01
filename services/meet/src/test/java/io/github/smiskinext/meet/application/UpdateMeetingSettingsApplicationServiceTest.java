package io.github.smiskinext.meet.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.github.smiskinext.meet.application.command.UpdateMeetingSettingsCommand;
import io.github.smiskinext.meet.application.result.UpdateMeetingSettingsResult;
import io.github.smiskinext.meet.application.service.UpdateMeetingSettingsApplicationService;
import io.github.smiskinext.meet.domain.MeetingError;
import io.github.smiskinext.meet.domain.model.Meeting;
import io.github.smiskinext.meet.domain.model.MeetingStatus;
import io.github.smiskinext.meet.domain.model.ParticipantRole;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class UpdateMeetingSettingsApplicationServiceTest {

    private MeetingRepository meetingRepository;
    private ParticipationLogRepository participationLogRepository;
    private EventPublisher eventPublisher;
    private LiveKitPort liveKitPort;
    private UpdateMeetingSettingsApplicationService service;

    @BeforeEach
    void setUp() {
        meetingRepository = mock(MeetingRepository.class);
        participationLogRepository = mock(ParticipationLogRepository.class);
        eventPublisher = mock(EventPublisher.class);
        liveKitPort = mock(LiveKitPort.class);
        service = new UpdateMeetingSettingsApplicationService(
                meetingRepository, participationLogRepository, eventPublisher, liveKitPort);
    }

    @Test
    void successfulSettingsUpdateSavesPublishesAndEnforcesPermissions() {
        Meeting meeting = runningMeeting();
        UUID meetingId = meeting.getId().value();
        when(meetingRepository.findByIdWithLock(meetingId)).thenReturn(Optional.of(meeting));
        when(participationLogRepository.findActiveByMeetingId(meetingId))
                .thenReturn(List.of(
                        participationLog(meetingId, ParticipantRole.HOST, "host-identity"),
                        participationLog(meetingId, ParticipantRole.PARTICIPANT, "p1-identity")));
        when(liveKitPort.updateParticipantPermissions(any(), any(), any()))
                .thenReturn(Result.success());

        UpdateMeetingSettingsCommand command = new UpdateMeetingSettingsCommand(
                meetingId, "tenant", "host", "ALLOW_ALL", 50, false, true, true, true);
        Result<UpdateMeetingSettingsResult, MeetingError> result = service.execute(command);

        assertThat(result.isSuccess()).isTrue();
        UpdateMeetingSettingsResult value =
                ((Result.Success<UpdateMeetingSettingsResult, MeetingError>) result).value();
        assertThat(value.allowScreenShare()).isFalse();
        verify(meetingRepository).save(meeting);
        verify(eventPublisher).publishEventsOf(meeting);

        ArgumentCaptor<ParticipantGrants> grantsCaptor =
                ArgumentCaptor.forClass(ParticipantGrants.class);
        verify(liveKitPort)
                .updateParticipantPermissions(any(), eq("p1-identity"), grantsCaptor.capture());
        ParticipantGrants enforcedGrants = grantsCaptor.getValue();
        assertThat(enforcedGrants.canPublish()).isTrue();
        assertThat(enforcedGrants.allowedSources())
                .contains("microphone", "camera")
                .doesNotContain("screen_share", "screen_share_audio");
    }

    @Test
    void hostIsSkippedDuringEnforcement() {
        Meeting meeting = runningMeeting();
        UUID meetingId = meeting.getId().value();
        when(meetingRepository.findByIdWithLock(meetingId)).thenReturn(Optional.of(meeting));
        when(participationLogRepository.findActiveByMeetingId(meetingId))
                .thenReturn(List.of(
                        participationLog(meetingId, ParticipantRole.HOST, "host-identity")));

        UpdateMeetingSettingsCommand command = new UpdateMeetingSettingsCommand(
                meetingId, "tenant", "host", "ALLOW_ALL", 50, false, true, true, true);
        service.execute(command);

        verify(liveKitPort, never()).updateParticipantPermissions(any(), any(), any());
    }

    @Test
    void enablingSourceUpdatesParticipants() {
        Meeting meeting = runningMeetingWithAllMediaDisabled();
        UUID meetingId = meeting.getId().value();
        when(meetingRepository.findByIdWithLock(meetingId)).thenReturn(Optional.of(meeting));
        when(participationLogRepository.findActiveByMeetingId(meetingId))
                .thenReturn(List.of(
                        participationLog(meetingId, ParticipantRole.PARTICIPANT, "p1-identity")));
        when(liveKitPort.updateParticipantPermissions(any(), any(), any()))
                .thenReturn(Result.success());

        UpdateMeetingSettingsCommand command = new UpdateMeetingSettingsCommand(
                meetingId, "tenant", "host", "ALLOW_ALL", 50, true, true, true, true);
        Result<UpdateMeetingSettingsResult, MeetingError> result = service.execute(command);

        assertThat(result.isSuccess()).isTrue();
        ArgumentCaptor<ParticipantGrants> grantsCaptor =
                ArgumentCaptor.forClass(ParticipantGrants.class);
        verify(liveKitPort)
                .updateParticipantPermissions(any(), eq("p1-identity"), grantsCaptor.capture());
        ParticipantGrants grants = grantsCaptor.getValue();
        assertThat(grants.canPublish()).isTrue();
        assertThat(grants.allowedSources())
                .contains("microphone", "camera", "screen_share", "screen_share_audio");
    }

    @Test
    void liveKitFailureDoesNotRollBackSettingsOrEvent() {
        Meeting meeting = runningMeeting();
        UUID meetingId = meeting.getId().value();
        when(meetingRepository.findByIdWithLock(meetingId)).thenReturn(Optional.of(meeting));
        when(participationLogRepository.findActiveByMeetingId(meetingId))
                .thenReturn(List.of(
                        participationLog(meetingId, ParticipantRole.PARTICIPANT, "p1-identity")));
        when(liveKitPort.updateParticipantPermissions(any(), any(), any()))
                .thenReturn(
                        Result.failure(new MeetingError.LiveKitUnavailable("connection refused")));

        UpdateMeetingSettingsCommand command = new UpdateMeetingSettingsCommand(
                meetingId, "tenant", "host", "ALLOW_ALL", 50, false, true, true, true);
        Result<UpdateMeetingSettingsResult, MeetingError> result = service.execute(command);

        assertThat(result.isSuccess()).isTrue();
        verify(meetingRepository).save(meeting);
        verify(eventPublisher).publishEventsOf(meeting);
    }

    @Test
    void missingMeetingReturnsNotFound() {
        UUID meetingId = UUID.randomUUID();
        when(meetingRepository.findByIdWithLock(meetingId)).thenReturn(Optional.empty());
        UpdateMeetingSettingsCommand command = new UpdateMeetingSettingsCommand(
                meetingId, "tenant", "host", "ALLOW_ALL", 50, true, true, true, true);

        Result<UpdateMeetingSettingsResult, MeetingError> result = service.execute(command);

        assertThat(result.isFailure()).isTrue();
        assertThat(((Result.Failure<UpdateMeetingSettingsResult, MeetingError>) result).error())
                .isInstanceOf(MeetingError.MeetingNotFound.class);
        verify(meetingRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void nonHostReturnsNotAuthorized() {
        Meeting meeting = runningMeeting();
        UUID meetingId = meeting.getId().value();
        when(meetingRepository.findByIdWithLock(meetingId)).thenReturn(Optional.of(meeting));
        UpdateMeetingSettingsCommand command = new UpdateMeetingSettingsCommand(
                meetingId, "tenant", "other-account", "ALLOW_ALL", 50, true, true, true, true);

        Result<UpdateMeetingSettingsResult, MeetingError> result = service.execute(command);

        assertThat(result.isFailure()).isTrue();
        assertThat(((Result.Failure<UpdateMeetingSettingsResult, MeetingError>) result).error())
                .isInstanceOf(MeetingError.NotAuthorized.class);
        verify(meetingRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void completedMeetingRejectsSettingsUpdate() {
        Meeting meeting = completedMeeting();
        UUID meetingId = meeting.getId().value();
        when(meetingRepository.findByIdWithLock(meetingId)).thenReturn(Optional.of(meeting));
        UpdateMeetingSettingsCommand command = new UpdateMeetingSettingsCommand(
                meetingId, "tenant", "host", "ALLOW_ALL", 50, true, true, true, true);

        Result<UpdateMeetingSettingsResult, MeetingError> result = service.execute(command);

        assertThat(result.isFailure()).isTrue();
        verify(meetingRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    private Meeting runningMeeting() {
        Instant start = Instant.now().minus(1, ChronoUnit.HOURS);
        return Meeting.reconstitute(
                TenantId.of("tenant"),
                MeetingId.of(UUID.randomUUID()),
                AccountId.of("host"),
                ShortCode.of("ABC123DEF0"),
                MeetingTitle.of("Title"),
                "Description",
                JiraIssueLink.of("ISS-1", "PROJ-1", "PROJ"),
                MeetingTimeRange.of(start, start.plus(1, ChronoUnit.HOURS)),
                null,
                io.github.smiskinext.meet.domain.model.MeetingType.SCHEDULED,
                MeetingStatus.RUNNING,
                MeetingSettings.defaults(),
                MeetingTimeZone.of("Asia/Ho_Chi_Minh"),
                Email.of("host@example.com"),
                InviteeDisplayName.of("Host User"),
                "cal@example.com",
                0,
                start.minus(1, ChronoUnit.DAYS),
                start.minus(1, ChronoUnit.DAYS),
                null,
                null,
                null,
                null);
    }

    private Meeting runningMeetingWithAllMediaDisabled() {
        Instant start = Instant.now().minus(1, ChronoUnit.HOURS);
        MeetingSettings restrictedSettings = new MeetingSettings(
                io.github.smiskinext.meet.domain.model.AdmissionPolicy.MANUAL_APPROVAL,
                100,
                false,
                true,
                false,
                false);
        return Meeting.reconstitute(
                TenantId.of("tenant"),
                MeetingId.of(UUID.randomUUID()),
                AccountId.of("host"),
                ShortCode.of("ABC123DEF0"),
                MeetingTitle.of("Title"),
                "Description",
                JiraIssueLink.of("ISS-1", "PROJ-1", "PROJ"),
                MeetingTimeRange.of(start, start.plus(1, ChronoUnit.HOURS)),
                null,
                io.github.smiskinext.meet.domain.model.MeetingType.SCHEDULED,
                MeetingStatus.RUNNING,
                restrictedSettings,
                MeetingTimeZone.of("Asia/Ho_Chi_Minh"),
                Email.of("host@example.com"),
                InviteeDisplayName.of("Host User"),
                "cal@example.com",
                0,
                start.minus(1, ChronoUnit.DAYS),
                start.minus(1, ChronoUnit.DAYS),
                null,
                null,
                null,
                null);
    }

    private Meeting completedMeeting() {
        Instant start = Instant.now().minus(2, ChronoUnit.HOURS);
        return Meeting.reconstitute(
                TenantId.of("tenant"),
                MeetingId.of(UUID.randomUUID()),
                AccountId.of("host"),
                ShortCode.of("ABC123DEF0"),
                MeetingTitle.of("Title"),
                "Description",
                JiraIssueLink.of("ISS-1", "PROJ-1", "PROJ"),
                MeetingTimeRange.of(start, start.plus(1, ChronoUnit.HOURS)),
                null,
                io.github.smiskinext.meet.domain.model.MeetingType.SCHEDULED,
                MeetingStatus.COMPLETED,
                MeetingSettings.defaults(),
                MeetingTimeZone.of("Asia/Ho_Chi_Minh"),
                Email.of("host@example.com"),
                InviteeDisplayName.of("Host User"),
                "cal@example.com",
                0,
                start.minus(1, ChronoUnit.DAYS),
                start.minus(1, ChronoUnit.DAYS),
                null,
                null,
                null,
                null);
    }

    private ParticipationLog participationLog(
            UUID meetingId, ParticipantRole role, String identity) {
        return ParticipationLog.reconstitute(
                TenantId.of("tenant"),
                ParticipationLogId.of(UUID.randomUUID()),
                MeetingId.of(meetingId),
                AccountId.of(role == ParticipantRole.HOST ? "host" : "participant"),
                role,
                LiveKitIdentity.of(identity),
                null,
                Instant.now().minus(10, ChronoUnit.MINUTES),
                null,
                null);
    }
}
