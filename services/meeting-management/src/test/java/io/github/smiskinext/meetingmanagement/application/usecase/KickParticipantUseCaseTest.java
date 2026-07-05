package io.github.smiskinext.meetingmanagement.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.github.smiskinext.meetingmanagement.application.command.KickParticipantCommand;
import io.github.smiskinext.meetingmanagement.domain.MeetingError;
import io.github.smiskinext.meetingmanagement.domain.event.ParticipantKickedEvent;
import io.github.smiskinext.meetingmanagement.domain.model.Meeting;
import io.github.smiskinext.meetingmanagement.domain.model.MeetingStatus;
import io.github.smiskinext.meetingmanagement.domain.model.ParticipantRole;
import io.github.smiskinext.meetingmanagement.domain.model.ParticipationLog;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.LiveKitIdentity;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.LiveKitRoomName;
import io.github.smiskinext.meetingmanagement.domain.port.LiveKitPort;
import io.github.smiskinext.meetingmanagement.domain.port.MeetingRepository;
import io.github.smiskinext.meetingmanagement.domain.port.ParticipationLogRepository;
import io.github.smiskinext.shared.domain.Result;
import io.github.smiskinext.shared.domain.valueobject.MeetingId;
import io.github.smiskinext.shared.domain.valueobject.UserId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KickParticipantUseCaseTest {

    @Mock
    MeetingRepository meetingRepository;

    @Mock
    ParticipationLogRepository participationLogRepository;

    @Mock
    LiveKitPort liveKitPort;

    @Mock
    ApplicationEventPublisher applicationEventPublisher;

    private KickParticipantUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new KickParticipantUseCase(
                meetingRepository,
                participationLogRepository,
                liveKitPort,
                applicationEventPublisher);
    }

    private Meeting liveMeeting(UUID meetingId, UUID hostId) {
        Meeting m = mock(Meeting.class);
        when(m.getId()).thenReturn(MeetingId.of(meetingId));
        when(m.getHostId()).thenReturn(UserId.of(hostId));
        when(m.getStatus()).thenReturn(MeetingStatus.LIVE);
        return m;
    }

    private Meeting nonLiveMeeting(UUID meetingId, UUID hostId, MeetingStatus status) {
        Meeting m = mock(Meeting.class);
        when(m.getId()).thenReturn(MeetingId.of(meetingId));
        when(m.getHostId()).thenReturn(UserId.of(hostId));
        when(m.getStatus()).thenReturn(status);
        return m;
    }

    private ParticipationLog activeSession(
            UUID meetingId, UUID userId, String displayName, ParticipantRole role) {
        ParticipationLog log = mock(ParticipationLog.class);
        when(log.getMeetingId()).thenReturn(MeetingId.of(meetingId));
        when(log.getLivekitIdentity()).thenReturn(LiveKitIdentity.of("device-001"));
        return log;
    }

    @Test
    void execute_hostKicksRegisteredUser_succeeds() {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        Meeting meeting = liveMeeting(meetingId, hostId);
        ParticipationLog session =
                activeSession(meetingId, targetUserId, "Alice", ParticipantRole.PARTICIPANT);

        when(meetingRepository.findById(meetingId)).thenReturn(Optional.of(meeting));
        when(participationLogRepository.findActiveByMeetingIdAndUserId(meetingId, targetUserId))
                .thenReturn(List.of(session));
        when(liveKitPort.removeParticipant(any(LiveKitRoomName.class), anyString()))
                .thenReturn(Result.success());

        var command = new KickParticipantCommand(meetingId, hostId, targetUserId, null);
        var result = useCase.execute(command);

        assertThat(result).isInstanceOf(Result.Success.class);
        verify(liveKitPort)
                .removeParticipant(
                        LiveKitRoomName.fromMeetingId(MeetingId.of(meetingId)), "device-001");
        verify(applicationEventPublisher).publishEvent(any(ParticipantKickedEvent.class));
    }

    @Test
    void execute_hostKicksGuestByDisplayName_succeeds() {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        String guestName = "Bob Guest";
        Meeting meeting = liveMeeting(meetingId, hostId);
        ParticipationLog session = mock(ParticipationLog.class);
        when(session.getLivekitIdentity()).thenReturn(LiveKitIdentity.of("guest:device-xyz"));

        when(meetingRepository.findById(meetingId)).thenReturn(Optional.of(meeting));
        when(participationLogRepository.findActiveByMeetingIdAndDisplayName(meetingId, guestName))
                .thenReturn(List.of(session));
        when(liveKitPort.removeParticipant(any(LiveKitRoomName.class), anyString()))
                .thenReturn(Result.success());

        var command = new KickParticipantCommand(meetingId, hostId, null, guestName);
        var result = useCase.execute(command);

        assertThat(result).isInstanceOf(Result.Success.class);
        verify(applicationEventPublisher).publishEvent(any(ParticipantKickedEvent.class));
    }

    @Test
    void execute_kickMultiDeviceUser_removesAllSessions() {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        Meeting meeting = liveMeeting(meetingId, hostId);

        ParticipationLog session1 = mock(ParticipationLog.class);
        when(session1.getLivekitIdentity()).thenReturn(LiveKitIdentity.of("user01:device-A"));
        ParticipationLog session2 = mock(ParticipationLog.class);
        when(session2.getLivekitIdentity()).thenReturn(LiveKitIdentity.of("user01:device-B"));

        when(meetingRepository.findById(meetingId)).thenReturn(Optional.of(meeting));
        when(participationLogRepository.findActiveByMeetingIdAndUserId(meetingId, targetUserId))
                .thenReturn(List.of(session1, session2));
        when(liveKitPort.removeParticipant(any(LiveKitRoomName.class), eq("user01:device-A")))
                .thenReturn(Result.success());
        when(liveKitPort.removeParticipant(any(LiveKitRoomName.class), eq("user01:device-B")))
                .thenReturn(Result.success());

        var command = new KickParticipantCommand(meetingId, hostId, targetUserId, null);
        var result = useCase.execute(command);

        assertThat(result).isInstanceOf(Result.Success.class);
        verify(liveKitPort, times(2)).removeParticipant(any(LiveKitRoomName.class), anyString());
    }

    @Test
    void execute_kickPublishesCorrectEventFields() {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        Meeting meeting = liveMeeting(meetingId, hostId);
        ParticipationLog session =
                activeSession(meetingId, targetUserId, "Alice", ParticipantRole.PARTICIPANT);

        when(meetingRepository.findById(meetingId)).thenReturn(Optional.of(meeting));
        when(participationLogRepository.findActiveByMeetingIdAndUserId(meetingId, targetUserId))
                .thenReturn(List.of(session));
        when(liveKitPort.removeParticipant(any(LiveKitRoomName.class), anyString()))
                .thenReturn(Result.success());

        var command = new KickParticipantCommand(meetingId, hostId, targetUserId, null);
        useCase.execute(command);

        ArgumentCaptor<ParticipantKickedEvent> captor =
                ArgumentCaptor.forClass(ParticipantKickedEvent.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());
        ParticipantKickedEvent event = captor.getValue();
        assertThat(event.meetingId()).isEqualTo(meetingId);
        assertThat(event.kickedBy()).isEqualTo(hostId);
        assertThat(event.kickedUserId()).isEqualTo(targetUserId);
        assertThat(event.kickedDisplayName()).isNull();
    }

    @Test
    void execute_meetingNotFound_returnsNotFound() {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        when(meetingRepository.findById(meetingId)).thenReturn(Optional.empty());

        var command = new KickParticipantCommand(meetingId, hostId, targetUserId, null);
        var result = useCase.execute(command);

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<?, MeetingError>) result).error())
                .isInstanceOf(MeetingError.MeetingNotFound.class);
    }

    @Test
    void execute_nonHostKicks_returnsForbidden() {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        UUID nonHostId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        Meeting meeting = liveMeeting(meetingId, hostId);

        when(meetingRepository.findById(meetingId)).thenReturn(Optional.of(meeting));

        var command = new KickParticipantCommand(meetingId, nonHostId, targetUserId, null);
        var result = useCase.execute(command);

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<?, MeetingError>) result).error())
                .isInstanceOf(MeetingError.NotAuthorized.class);
    }

    @Test
    void execute_hostSelfKick_returnsBadRequest() {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        Meeting meeting = liveMeeting(meetingId, hostId);

        when(meetingRepository.findById(meetingId)).thenReturn(Optional.of(meeting));

        var command = new KickParticipantCommand(meetingId, hostId, hostId, null);
        var result = useCase.execute(command);

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<?, MeetingError>) result).error())
                .isInstanceOf(MeetingError.CanNotKickSelf.class);
    }

    @Test
    void execute_meetingNotLive_returnsConflict() {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        Meeting meeting = nonLiveMeeting(meetingId, hostId, MeetingStatus.SCHEDULED);

        when(meetingRepository.findById(meetingId)).thenReturn(Optional.of(meeting));

        var command = new KickParticipantCommand(meetingId, hostId, targetUserId, null);
        var result = useCase.execute(command);

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<?, MeetingError>) result).error())
                .isInstanceOf(MeetingError.InvalidStatusTransition.class);
    }

    @Test
    void execute_inactiveRegisteredUser_returnsNotFound() {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        Meeting meeting = liveMeeting(meetingId, hostId);

        when(meetingRepository.findById(meetingId)).thenReturn(Optional.of(meeting));
        when(participationLogRepository.findActiveByMeetingIdAndUserId(meetingId, targetUserId))
                .thenReturn(List.of());

        var command = new KickParticipantCommand(meetingId, hostId, targetUserId, null);
        var result = useCase.execute(command);

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<?, MeetingError>) result).error())
                .isInstanceOf(MeetingError.UserNotInMeeting.class);
    }

    @Test
    void execute_inactiveGuestDisplayName_returnsNotFound() {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        String guestName = "Nobody";
        Meeting meeting = liveMeeting(meetingId, hostId);

        when(meetingRepository.findById(meetingId)).thenReturn(Optional.of(meeting));
        when(participationLogRepository.findActiveByMeetingIdAndDisplayName(meetingId, guestName))
                .thenReturn(List.of());

        var command = new KickParticipantCommand(meetingId, hostId, null, guestName);
        var result = useCase.execute(command);

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<?, MeetingError>) result).error())
                .isInstanceOf(MeetingError.UserNotInMeeting.class);
    }

    @Test
    void execute_neitherUserIdNorDisplayName_returnsBadRequest() {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        Meeting meeting = liveMeeting(meetingId, hostId);

        when(meetingRepository.findById(meetingId)).thenReturn(Optional.of(meeting));

        var command = new KickParticipantCommand(meetingId, hostId, null, null);
        var result = useCase.execute(command);

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<?, MeetingError>) result).error())
                .isInstanceOf(MeetingError.InvalidKickTarget.class);
    }

    @Test
    void execute_bothUserIdAndDisplayName_returnsBadRequest() {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        Meeting meeting = liveMeeting(meetingId, hostId);

        when(meetingRepository.findById(meetingId)).thenReturn(Optional.of(meeting));

        var command = new KickParticipantCommand(meetingId, hostId, targetUserId, "Bob");
        var result = useCase.execute(command);

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<?, MeetingError>) result).error())
                .isInstanceOf(MeetingError.InvalidKickTarget.class);
    }

    @Test
    void execute_partialLiveKitFailure_stillSucceeds() {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        Meeting meeting = liveMeeting(meetingId, hostId);

        ParticipationLog session1 = mock(ParticipationLog.class);
        when(session1.getLivekitIdentity()).thenReturn(LiveKitIdentity.of("user01:device-A"));
        ParticipationLog session2 = mock(ParticipationLog.class);
        when(session2.getLivekitIdentity()).thenReturn(LiveKitIdentity.of("user01:device-B"));

        when(meetingRepository.findById(meetingId)).thenReturn(Optional.of(meeting));
        when(participationLogRepository.findActiveByMeetingIdAndUserId(meetingId, targetUserId))
                .thenReturn(List.of(session1, session2));
        when(liveKitPort.removeParticipant(any(LiveKitRoomName.class), eq("user01:device-A")))
                .thenReturn(Result.failure(new MeetingError.LiveKitUnavailable("timeout")));
        when(liveKitPort.removeParticipant(any(LiveKitRoomName.class), eq("user01:device-B")))
                .thenReturn(Result.success());

        var command = new KickParticipantCommand(meetingId, hostId, targetUserId, null);
        var result = useCase.execute(command);
        assertThat(result).isInstanceOf(Result.Success.class);
        verify(applicationEventPublisher).publishEvent(any(ParticipantKickedEvent.class));
    }

    @Test
    void execute_allLiveKitFailures_returnsLiveKitUnavailable() {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        Meeting meeting = liveMeeting(meetingId, hostId);

        ParticipationLog session = mock(ParticipationLog.class);
        when(session.getLivekitIdentity()).thenReturn(LiveKitIdentity.of("user01:device-A"));

        when(meetingRepository.findById(meetingId)).thenReturn(Optional.of(meeting));
        when(participationLogRepository.findActiveByMeetingIdAndUserId(meetingId, targetUserId))
                .thenReturn(List.of(session));
        when(liveKitPort.removeParticipant(any(LiveKitRoomName.class), anyString()))
                .thenReturn(
                        Result.failure(new MeetingError.LiveKitUnavailable("connection refused")));

        var command = new KickParticipantCommand(meetingId, hostId, targetUserId, null);
        var result = useCase.execute(command);

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<?, MeetingError>) result).error())
                .isInstanceOf(MeetingError.LiveKitUnavailable.class);
        verify(applicationEventPublisher, never()).publishEvent(any(ParticipantKickedEvent.class));
    }
}
