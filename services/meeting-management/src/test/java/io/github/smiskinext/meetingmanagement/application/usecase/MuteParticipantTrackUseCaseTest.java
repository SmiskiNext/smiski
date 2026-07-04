package io.github.smiskinext.meetingmanagement.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.github.smiskinext.meetingmanagement.application.command.MuteParticipantTrackCommand;
import io.github.smiskinext.meetingmanagement.domain.MeetingError;
import io.github.smiskinext.meetingmanagement.domain.model.Meeting;
import io.github.smiskinext.meetingmanagement.domain.model.MeetingStatus;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.LiveKitRoomName;
import io.github.smiskinext.meetingmanagement.domain.port.LiveKitPort;
import io.github.smiskinext.meetingmanagement.domain.port.MeetingRepository;
import io.github.phunguy65.zms.shared.domain.Result;
import io.github.phunguy65.zms.shared.domain.valueobject.MeetingId;
import io.github.phunguy65.zms.shared.domain.valueobject.UserId;
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
class MuteParticipantTrackUseCaseTest {

    @Mock
    MeetingRepository meetingRepository;

    @Mock
    LiveKitPort liveKitPort;

    private MuteParticipantTrackUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new MuteParticipantTrackUseCase(meetingRepository, liveKitPort);
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

    @Test
    void execute_hostMutesParticipant_succeeds() {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        String targetIdentity = "user01:device-A";
        Meeting meeting = liveMeeting(meetingId, hostId);

        when(meetingRepository.findById(meetingId)).thenReturn(Optional.of(meeting));
        when(liveKitPort.muteParticipantTrack(any(LiveKitRoomName.class), anyString(), anyString()))
                .thenReturn(Result.success());

        var command =
                new MuteParticipantTrackCommand(meetingId, hostId, targetIdentity, "microphone");
        var result = useCase.execute(command);

        assertThat(result).isInstanceOf(Result.Success.class);
    }

    @Test
    void execute_muteMicrophone_resolvesCorrectTrackSid() {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        String targetIdentity = "user01:device-B";
        Meeting meeting = liveMeeting(meetingId, hostId);
        LiveKitRoomName roomName = LiveKitRoomName.fromMeetingId(MeetingId.of(meetingId));

        when(meetingRepository.findById(meetingId)).thenReturn(Optional.of(meeting));
        when(liveKitPort.muteParticipantTrack(any(LiveKitRoomName.class), anyString(), anyString()))
                .thenReturn(Result.success());

        var command =
                new MuteParticipantTrackCommand(meetingId, hostId, targetIdentity, "microphone");
        useCase.execute(command);

        ArgumentCaptor<LiveKitRoomName> roomCaptor = ArgumentCaptor.forClass(LiveKitRoomName.class);
        ArgumentCaptor<String> identityCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> sourceCaptor = ArgumentCaptor.forClass(String.class);
        verify(liveKitPort)
                .muteParticipantTrack(
                        roomCaptor.capture(), identityCaptor.capture(), sourceCaptor.capture());
        assertThat(identityCaptor.getValue()).isEqualTo(targetIdentity);
        assertThat(sourceCaptor.getValue()).isEqualTo("microphone");
    }

    @Test
    void execute_hostMutesSelf_returnsCanNotMuteSelf() {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        String selfIdentity = hostId + ":device-001";
        Meeting meeting = liveMeeting(meetingId, hostId);

        when(meetingRepository.findById(meetingId)).thenReturn(Optional.of(meeting));

        var command =
                new MuteParticipantTrackCommand(meetingId, hostId, selfIdentity, "microphone");
        var result = useCase.execute(command);

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<?, MeetingError>) result).error())
                .isInstanceOf(MeetingError.CanNotMuteSelf.class);
        verifyNoInteractions(liveKitPort);
    }

    @Test
    void execute_trackNotFound_returnsTrackNotFound() {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        String targetIdentity = "user01:device-A";
        Meeting meeting = liveMeeting(meetingId, hostId);

        when(meetingRepository.findById(meetingId)).thenReturn(Optional.of(meeting));
        when(liveKitPort.muteParticipantTrack(any(LiveKitRoomName.class), anyString(), anyString()))
                .thenReturn(Result.failure(
                        new MeetingError.TrackNotFound(targetIdentity, "microphone")));

        var command =
                new MuteParticipantTrackCommand(meetingId, hostId, targetIdentity, "microphone");
        var result = useCase.execute(command);

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<?, MeetingError>) result).error())
                .isInstanceOf(MeetingError.TrackNotFound.class);
    }

    @Test
    void execute_meetingNotFound_returnsMeetingNotFound() {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        when(meetingRepository.findById(meetingId)).thenReturn(Optional.empty());

        var command =
                new MuteParticipantTrackCommand(meetingId, hostId, "user01:device-A", "microphone");
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

        var command = new MuteParticipantTrackCommand(
                meetingId, nonHostId, "user01:device-A", "microphone");
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

        var command =
                new MuteParticipantTrackCommand(meetingId, hostId, "user01:device-A", "microphone");
        var result = useCase.execute(command);

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<?, MeetingError>) result).error())
                .isInstanceOf(MeetingError.MeetingNotLive.class);
        verifyNoInteractions(liveKitPort);
    }
}
