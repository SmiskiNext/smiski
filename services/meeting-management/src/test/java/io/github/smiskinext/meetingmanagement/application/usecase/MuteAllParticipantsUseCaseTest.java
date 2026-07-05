package io.github.smiskinext.meetingmanagement.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.github.smiskinext.meetingmanagement.application.command.MuteAllParticipantsCommand;
import io.github.smiskinext.meetingmanagement.domain.MeetingError;
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

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MuteAllParticipantsUseCaseTest {

    @Mock
    MeetingRepository meetingRepository;

    @Mock
    ParticipationLogRepository participationLogRepository;

    @Mock
    LiveKitPort liveKitPort;

    private MuteAllParticipantsUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new MuteAllParticipantsUseCase(
                meetingRepository, participationLogRepository, liveKitPort);
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

    private ParticipationLog participantSession(String identity) {
        ParticipationLog log = mock(ParticipationLog.class);
        when(log.getRole()).thenReturn(ParticipantRole.PARTICIPANT);
        when(log.getLivekitIdentity()).thenReturn(LiveKitIdentity.of(identity));
        return log;
    }

    private ParticipationLog hostSession(String identity) {
        ParticipationLog log = mock(ParticipationLog.class);
        when(log.getRole()).thenReturn(ParticipantRole.HOST);
        when(log.getLivekitIdentity()).thenReturn(LiveKitIdentity.of(identity));
        return log;
    }

    private ParticipationLog guestSession(String identity) {
        ParticipationLog log = mock(ParticipationLog.class);
        when(log.getRole()).thenReturn(ParticipantRole.GUEST);
        when(log.getLivekitIdentity()).thenReturn(LiveKitIdentity.of(identity));
        return log;
    }

    @Test
    void execute_hostMutesAllParticipants_succeeds() {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        Meeting meeting = liveMeeting(meetingId, hostId);
        ParticipationLog session1 = participantSession("user01:device-A");
        ParticipationLog session2 = participantSession("user02:device-B");

        when(meetingRepository.findById(meetingId)).thenReturn(Optional.of(meeting));
        when(participationLogRepository.findActiveByMeetingId(meetingId))
                .thenReturn(List.of(session1, session2));
        when(liveKitPort.muteAllParticipantMicTracks(any(LiveKitRoomName.class), anyList()))
                .thenReturn(Result.success());

        var command = new MuteAllParticipantsCommand(meetingId, hostId);
        var result = useCase.execute(command);

        assertThat(result).isInstanceOf(Result.Success.class);
        verify(liveKitPort).muteAllParticipantMicTracks(any(LiveKitRoomName.class), anyList());
    }

    @Test
    void execute_onlyParticipantRoleIncluded_hostAndGuestExcluded() {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        Meeting meeting = liveMeeting(meetingId, hostId);
        ParticipationLog hostLog = hostSession(hostId + ":device-001");
        ParticipationLog guestLog = guestSession("Guest:device-001");
        ParticipationLog participant1 = participantSession("user01:device-A");
        ParticipationLog participant2 = participantSession("user02:device-B");

        when(meetingRepository.findById(meetingId)).thenReturn(Optional.of(meeting));
        when(participationLogRepository.findActiveByMeetingId(meetingId))
                .thenReturn(List.of(hostLog, guestLog, participant1, participant2));
        when(liveKitPort.muteAllParticipantMicTracks(any(LiveKitRoomName.class), anyList()))
                .thenReturn(Result.success());

        var command = new MuteAllParticipantsCommand(meetingId, hostId);
        useCase.execute(command);

        ArgumentCaptor<List<String>> identitiesCaptor = ArgumentCaptor.forClass(List.class);
        verify(liveKitPort)
                .muteAllParticipantMicTracks(
                        any(LiveKitRoomName.class), identitiesCaptor.capture());
        List<String> capturedIdentities = identitiesCaptor.getValue();
        assertThat(capturedIdentities)
                .containsExactlyInAnyOrder("user01:device-A", "user02:device-B");
        assertThat(capturedIdentities).doesNotContain(hostId + ":device-001");
        assertThat(capturedIdentities).doesNotContain("Guest:device-001");
    }

    @Test
    void execute_emptyParticipantList_returnsSuccess() {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        Meeting meeting = liveMeeting(meetingId, hostId);

        when(meetingRepository.findById(meetingId)).thenReturn(Optional.of(meeting));
        when(participationLogRepository.findActiveByMeetingId(meetingId)).thenReturn(List.of());

        var command = new MuteAllParticipantsCommand(meetingId, hostId);
        var result = useCase.execute(command);

        assertThat(result).isInstanceOf(Result.Success.class);
        verifyNoInteractions(liveKitPort);
    }

    @Test
    void execute_partialFailure_stillSucceeds() {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        Meeting meeting = liveMeeting(meetingId, hostId);
        ParticipationLog session1 = participantSession("user01:device-A");
        ParticipationLog session2 = participantSession("user02:device-B");

        when(meetingRepository.findById(meetingId)).thenReturn(Optional.of(meeting));
        when(participationLogRepository.findActiveByMeetingId(meetingId))
                .thenReturn(List.of(session1, session2));
        when(liveKitPort.muteAllParticipantMicTracks(any(LiveKitRoomName.class), anyList()))
                .thenReturn(Result.success());

        var command = new MuteAllParticipantsCommand(meetingId, hostId);
        var result = useCase.execute(command);

        assertThat(result).isInstanceOf(Result.Success.class);
    }

    @Test
    void execute_allFailures_returnsLiveKitUnavailable() {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        Meeting meeting = liveMeeting(meetingId, hostId);
        ParticipationLog session = participantSession("user01:device-A");

        when(meetingRepository.findById(meetingId)).thenReturn(Optional.of(meeting));
        when(participationLogRepository.findActiveByMeetingId(meetingId))
                .thenReturn(List.of(session));
        when(liveKitPort.muteAllParticipantMicTracks(any(LiveKitRoomName.class), anyList()))
                .thenReturn(
                        Result.failure(new MeetingError.LiveKitUnavailable("connection refused")));

        var command = new MuteAllParticipantsCommand(meetingId, hostId);
        var result = useCase.execute(command);

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<?, MeetingError>) result).error())
                .isInstanceOf(MeetingError.LiveKitUnavailable.class);
    }

    @Test
    void execute_meetingNotFound_returnsMeetingNotFound() {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        when(meetingRepository.findById(meetingId)).thenReturn(Optional.empty());

        var command = new MuteAllParticipantsCommand(meetingId, hostId);
        var result = useCase.execute(command);

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<?, MeetingError>) result).error())
                .isInstanceOf(MeetingError.MeetingNotFound.class);
        verifyNoInteractions(liveKitPort);
    }

    @Test
    void execute_nonHostMutes_returnsNotAuthorized() {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        UUID nonHostId = UUID.randomUUID();
        Meeting meeting = liveMeeting(meetingId, hostId);

        when(meetingRepository.findById(meetingId)).thenReturn(Optional.of(meeting));

        var command = new MuteAllParticipantsCommand(meetingId, nonHostId);
        var result = useCase.execute(command);

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<?, MeetingError>) result).error())
                .isInstanceOf(MeetingError.NotAuthorized.class);
        verifyNoInteractions(liveKitPort);
    }

    @Test
    void execute_meetingNotLive_returnsInvalidStatusTransition() {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        Meeting meeting = nonLiveMeeting(meetingId, hostId, MeetingStatus.SCHEDULED);

        when(meetingRepository.findById(meetingId)).thenReturn(Optional.of(meeting));

        var command = new MuteAllParticipantsCommand(meetingId, hostId);
        var result = useCase.execute(command);

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<?, MeetingError>) result).error())
                .isInstanceOf(MeetingError.MeetingNotLive.class);
        verifyNoInteractions(liveKitPort);
    }
}
