package io.github.smiskinext.meetingmanagement.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.smiskinext.meetingmanagement.application.command.EndMeetingCommand;
import io.github.smiskinext.meetingmanagement.application.command.StartRecordingCommand;
import io.github.smiskinext.meetingmanagement.application.command.StopRecordingCommand;
import io.github.smiskinext.meetingmanagement.application.helper.ParticipationLogCloser;
import io.github.smiskinext.meetingmanagement.domain.MeetingError;
import io.github.smiskinext.meetingmanagement.domain.PublishableEvent;
import io.github.smiskinext.meetingmanagement.domain.model.Meeting;
import io.github.smiskinext.meetingmanagement.domain.model.MeetingStatus;
import io.github.smiskinext.meetingmanagement.domain.model.MeetingType;
import io.github.smiskinext.meetingmanagement.domain.model.Recording;
import io.github.smiskinext.meetingmanagement.domain.model.RecordingStatus;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.LiveKitEgressId;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.LiveKitRoomName;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.MeetingSettings;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.ShortCode;
import io.github.smiskinext.meetingmanagement.domain.port.LiveKitPort;
import io.github.smiskinext.meetingmanagement.domain.port.MeetingRepository;
import io.github.smiskinext.meetingmanagement.domain.port.RecordingRepository;
import io.github.phunguy65.zms.shared.domain.Result;
import io.github.phunguy65.zms.shared.domain.valueobject.MeetingId;
import io.github.phunguy65.zms.shared.domain.valueobject.UserId;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import io.github.smiskinext.meetingmanagement.application.response.RecordingResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class RecordingFlowUseCaseTest {

    private static final String EGRESS_ID = "EG_TEST_123";

    @Mock
    MeetingRepository meetingRepository;

    @Mock
    RecordingRepository recordingRepository;

    @Mock
    LiveKitPort liveKitPort;

    @Mock
    ApplicationEventPublisher eventPublisher;

    @Mock
    ParticipationLogCloser participationLogCloser;

    private StartRecordingUseCase startRecordingUseCase;
    private StopRecordingUseCase stopRecordingUseCase;
    private EndMeetingUseCase endMeetingUseCase;

    @BeforeEach
    void setUp() {
        startRecordingUseCase = new StartRecordingUseCase(
                meetingRepository, recordingRepository, liveKitPort, eventPublisher);
        stopRecordingUseCase =
                new StopRecordingUseCase(meetingRepository, recordingRepository, liveKitPort);
        endMeetingUseCase = new EndMeetingUseCase(
                meetingRepository,
                recordingRepository,
                liveKitPort,
                eventPublisher,
                participationLogCloser);
    }

    @Test
    void startRecording_startsEgressAndPersistsPendingRecording() {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        Meeting meeting = liveMeeting(meetingId, hostId);
        when(meetingRepository.findById(meetingId)).thenReturn(Optional.of(meeting));
        when(recordingRepository.findActiveByMeetingId(meetingId)).thenReturn(Optional.empty());
        when(liveKitPort.startRoomCompositeEgress(
                        MeetingId.of(meetingId),
                        LiveKitRoomName.fromMeetingId(MeetingId.of(meetingId))))
                .thenReturn(Result.success(LiveKitEgressId.of(EGRESS_ID)));
        when(recordingRepository.save(any(Recording.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(liveKitPort.updateRoomMetadata(any(), any())).thenReturn(Result.success());

        var result = startRecordingUseCase.execute(new StartRecordingCommand(meetingId, hostId));

        assertThat(result).isInstanceOf(Result.Success.class);
        var response = ((Result.Success<
                RecordingResponse,
                                MeetingError>)
                        result)
                .value();
        assertThat(response.id()).isNotNull().isInstanceOf(UUID.class);
        assertThat(response.status()).isEqualTo(RecordingStatus.PENDING);

        ArgumentCaptor<Recording> recordingCaptor = ArgumentCaptor.forClass(Recording.class);
        verify(recordingRepository, times(2)).save(recordingCaptor.capture());
        assertThat(recordingCaptor.getAllValues().getLast().getLivekitEgressId())
                .contains(LiveKitEgressId.of(EGRESS_ID));
        assertThat(recordingCaptor.getAllValues().getLast().getStatus())
                .isEqualTo(RecordingStatus.PENDING);
        verify(eventPublisher).publishEvent(any(PublishableEvent.class));
        verify(liveKitPort)
                .updateRoomMetadata(
                        LiveKitRoomName.fromMeetingId(MeetingId.of(meetingId)),
                        "{\"recording\":true}");
    }

    @Test
    void startRecording_rejectsMissingMeeting() {
        UUID meetingId = UUID.randomUUID();

        var result = startRecordingUseCase.execute(
                new StartRecordingCommand(meetingId, UUID.randomUUID()));

        assertThat(result).isInstanceOf(Result.Failure.class);
        verifyNoInteractions(recordingRepository, liveKitPort, eventPublisher);
    }

    @Test
    void startRecording_rejectsNonHostRequester() {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        when(meetingRepository.findById(meetingId))
                .thenReturn(Optional.of(liveMeeting(meetingId, hostId)));

        var result = startRecordingUseCase.execute(
                new StartRecordingCommand(meetingId, UUID.randomUUID()));

        assertThat(result).isInstanceOf(Result.Failure.class);
        verifyNoInteractions(recordingRepository, liveKitPort, eventPublisher);
    }

    @Test
    void startRecording_rejectsWhenMeetingNotLive() {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        when(meetingRepository.findById(meetingId))
                .thenReturn(Optional.of(meetingWithStatus(meetingId, hostId, MeetingStatus.ENDED)));

        var result = startRecordingUseCase.execute(new StartRecordingCommand(meetingId, hostId));

        assertThat(result).isInstanceOf(Result.Failure.class);
        verifyNoInteractions(recordingRepository, liveKitPort, eventPublisher);
    }

    @Test
    void startRecording_rejectsWhenRecordingAlreadyActive() {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        when(meetingRepository.findById(meetingId))
                .thenReturn(Optional.of(liveMeeting(meetingId, hostId)));
        when(recordingRepository.findActiveByMeetingId(meetingId))
                .thenReturn(Optional.of(pendingRecording(meetingId, EGRESS_ID)));

        var result = startRecordingUseCase.execute(new StartRecordingCommand(meetingId, hostId));

        assertThat(result).isInstanceOf(Result.Failure.class);
        verify(liveKitPort, never()).startRoomCompositeEgress(any(), any());
        verify(recordingRepository, never()).save(any(Recording.class));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void startRecording_marksPersistedRecordingFailedWhenEgressStartFails() {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        when(meetingRepository.findById(meetingId))
                .thenReturn(Optional.of(liveMeeting(meetingId, hostId)));
        when(recordingRepository.findActiveByMeetingId(meetingId)).thenReturn(Optional.empty());
        when(liveKitPort.startRoomCompositeEgress(
                        MeetingId.of(meetingId),
                        LiveKitRoomName.fromMeetingId(MeetingId.of(meetingId))))
                .thenReturn(
                        Result.failure(new MeetingError.LiveKitUnavailable("egress unavailable")));
        when(recordingRepository.save(any(Recording.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = startRecordingUseCase.execute(new StartRecordingCommand(meetingId, hostId));

        assertThat(result).isInstanceOf(Result.Failure.class);
        ArgumentCaptor<Recording> recordingCaptor = ArgumentCaptor.forClass(Recording.class);
        verify(recordingRepository, times(2)).save(recordingCaptor.capture());
        assertThat(recordingCaptor.getAllValues().getLast().getStatus())
                .isEqualTo(RecordingStatus.FAILED);
        verify(eventPublisher, times(2)).publishEvent(any(PublishableEvent.class));
    }

    @Test
    void stopRecording_rejectsMissingMeeting() {
        UUID meetingId = UUID.randomUUID();

        var result = stopRecordingUseCase.execute(
                new StopRecordingCommand(meetingId, UUID.randomUUID()));

        assertThat(result).isInstanceOf(Result.Failure.class);
        verifyNoInteractions(recordingRepository, liveKitPort);
    }

    @Test
    void stopRecording_rejectsNonHostRequester() {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        when(meetingRepository.findById(meetingId))
                .thenReturn(Optional.of(liveMeeting(meetingId, hostId)));

        var result = stopRecordingUseCase.execute(
                new StopRecordingCommand(meetingId, UUID.randomUUID()));

        assertThat(result).isInstanceOf(Result.Failure.class);
        verifyNoInteractions(recordingRepository, liveKitPort);
    }

    @Test
    void stopRecording_rejectsWhenMeetingNotLive() {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        when(meetingRepository.findById(meetingId))
                .thenReturn(Optional.of(meetingWithStatus(meetingId, hostId, MeetingStatus.ENDED)));

        var result = stopRecordingUseCase.execute(new StopRecordingCommand(meetingId, hostId));

        assertThat(result).isInstanceOf(Result.Failure.class);
        verifyNoInteractions(recordingRepository, liveKitPort);
    }

    @Test
    void stopRecording_rejectsWhenNoActiveRecording() {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        when(meetingRepository.findById(meetingId))
                .thenReturn(Optional.of(liveMeeting(meetingId, hostId)));
        when(recordingRepository.findActiveByMeetingId(meetingId)).thenReturn(Optional.empty());

        var result = stopRecordingUseCase.execute(new StopRecordingCommand(meetingId, hostId));

        assertThat(result).isInstanceOf(Result.Failure.class);
        verify(liveKitPort, never()).stopEgress(any());
    }

    @Test
    void stopRecording_stopsActiveEgress() {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        Recording recording = pendingRecording(meetingId, EGRESS_ID);
        when(meetingRepository.findById(meetingId))
                .thenReturn(Optional.of(liveMeeting(meetingId, hostId)));
        when(recordingRepository.findActiveByMeetingId(meetingId))
                .thenReturn(Optional.of(recording));
        when(liveKitPort.stopEgress(LiveKitEgressId.of(EGRESS_ID))).thenReturn(Result.success());

        var result = stopRecordingUseCase.execute(new StopRecordingCommand(meetingId, hostId));

        assertThat(result).isInstanceOf(Result.Success.class);
        verify(liveKitPort).stopEgress(LiveKitEgressId.of(EGRESS_ID));
        verify(recordingRepository, never()).save(any(Recording.class));
    }

    @Test
    void stopRecording_neverCallsUpdateRoomMetadata() {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        Recording recording = pendingRecording(meetingId, EGRESS_ID);
        when(meetingRepository.findById(meetingId))
                .thenReturn(Optional.of(liveMeeting(meetingId, hostId)));
        when(recordingRepository.findActiveByMeetingId(meetingId))
                .thenReturn(Optional.of(recording));
        when(liveKitPort.stopEgress(LiveKitEgressId.of(EGRESS_ID))).thenReturn(Result.success());

        stopRecordingUseCase.execute(new StopRecordingCommand(meetingId, hostId));

        verify(liveKitPort, never()).updateRoomMetadata(any(), any());
    }

    @Test
    void stopRecording_treatsAlreadyStoppedEgressAsSuccess() {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        Recording recording = pendingRecording(meetingId, EGRESS_ID);
        when(meetingRepository.findById(meetingId))
                .thenReturn(Optional.of(liveMeeting(meetingId, hostId)));
        when(recordingRepository.findActiveByMeetingId(meetingId))
                .thenReturn(Optional.of(recording));
        when(liveKitPort.stopEgress(LiveKitEgressId.of(EGRESS_ID)))
                .thenReturn(
                        Result.failure(new MeetingError.LiveKitUnavailable("HTTP 404: Not Found")));

        var result = stopRecordingUseCase.execute(new StopRecordingCommand(meetingId, hostId));

        assertThat(result).isInstanceOf(Result.Success.class);
    }

    @Test
    void stopRecording_propagatesLiveKitStopFailure() {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        Recording recording = pendingRecording(meetingId, EGRESS_ID);
        when(meetingRepository.findById(meetingId))
                .thenReturn(Optional.of(liveMeeting(meetingId, hostId)));
        when(recordingRepository.findActiveByMeetingId(meetingId))
                .thenReturn(Optional.of(recording));
        when(liveKitPort.stopEgress(LiveKitEgressId.of(EGRESS_ID)))
                .thenReturn(Result.failure(new MeetingError.LiveKitUnavailable("service down")));

        var result = stopRecordingUseCase.execute(new StopRecordingCommand(meetingId, hostId));

        assertThat(result).isInstanceOf(Result.Failure.class);
    }

    @Test
    void endMeeting_rejectsMissingMeeting() {
        UUID meetingId = UUID.randomUUID();

        var result = endMeetingUseCase.execute(new EndMeetingCommand(meetingId, UUID.randomUUID()));

        assertThat(result).isInstanceOf(Result.Failure.class);
        verifyNoInteractions(
                recordingRepository, liveKitPort, eventPublisher, participationLogCloser);
    }

    @Test
    void endMeeting_rejectsNonHostRequester() {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        when(meetingRepository.findById(meetingId))
                .thenReturn(Optional.of(liveMeeting(meetingId, hostId)));

        var result = endMeetingUseCase.execute(new EndMeetingCommand(meetingId, UUID.randomUUID()));

        assertThat(result).isInstanceOf(Result.Failure.class);
        verifyNoInteractions(
                recordingRepository, liveKitPort, eventPublisher, participationLogCloser);
    }

    @Test
    void endMeeting_deletesRoomWithoutStoppingEgressWhenNoRecordingActive() {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        when(meetingRepository.findById(meetingId))
                .thenReturn(Optional.of(liveMeeting(meetingId, hostId)));
        when(recordingRepository.findActiveByMeetingId(meetingId)).thenReturn(Optional.empty());
        when(liveKitPort.deleteRoom(LiveKitRoomName.fromMeetingId(MeetingId.of(meetingId))))
                .thenReturn(Result.success());
        when(meetingRepository.save(any(Meeting.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = endMeetingUseCase.execute(new EndMeetingCommand(meetingId, hostId));

        assertThat(result).isInstanceOf(Result.Success.class);
        verify(liveKitPort, never()).stopEgress(any());
        verify(liveKitPort).deleteRoom(LiveKitRoomName.fromMeetingId(MeetingId.of(meetingId)));
        verify(participationLogCloser).closeAllActive(meetingId);
    }

    @Test
    void endMeeting_stopsActiveEgressDeletesRoomAndSavesEndedMeeting() {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        Recording recording = pendingRecording(meetingId, EGRESS_ID);
        when(meetingRepository.findById(meetingId))
                .thenReturn(Optional.of(liveMeeting(meetingId, hostId)));
        when(recordingRepository.findActiveByMeetingId(meetingId))
                .thenReturn(Optional.of(recording));
        when(liveKitPort.stopEgress(LiveKitEgressId.of(EGRESS_ID))).thenReturn(Result.success());
        when(liveKitPort.deleteRoom(LiveKitRoomName.fromMeetingId(MeetingId.of(meetingId))))
                .thenReturn(Result.success());
        when(meetingRepository.save(any(Meeting.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = endMeetingUseCase.execute(new EndMeetingCommand(meetingId, hostId));

        assertThat(result).isInstanceOf(Result.Success.class);
        verify(liveKitPort).stopEgress(LiveKitEgressId.of(EGRESS_ID));
        verify(liveKitPort).deleteRoom(LiveKitRoomName.fromMeetingId(MeetingId.of(meetingId)));
        verify(participationLogCloser).closeAllActive(meetingId);
        verify(meetingRepository)
                .save(argThat(savedMeeting -> savedMeeting.getStatus() == MeetingStatus.ENDED));
        verify(eventPublisher).publishEvent(any(PublishableEvent.class));
    }

    @Test
    void endMeeting_propagatesDeleteRoomFailure() {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        when(meetingRepository.findById(meetingId))
                .thenReturn(Optional.of(liveMeeting(meetingId, hostId)));
        when(recordingRepository.findActiveByMeetingId(meetingId)).thenReturn(Optional.empty());
        when(liveKitPort.deleteRoom(LiveKitRoomName.fromMeetingId(MeetingId.of(meetingId))))
                .thenReturn(Result.failure(new MeetingError.LiveKitUnavailable("room not found")));

        var result = endMeetingUseCase.execute(new EndMeetingCommand(meetingId, hostId));

        assertThat(result).isInstanceOf(Result.Failure.class);
        verify(participationLogCloser, never()).closeAllActive(any());
        verify(meetingRepository, never()).save(any(Meeting.class));
    }

    private static Meeting liveMeeting(UUID meetingId, UUID hostId) {
        return meetingWithStatus(meetingId, hostId, MeetingStatus.LIVE);
    }

    private static Meeting meetingWithStatus(UUID meetingId, UUID hostId, MeetingStatus status) {
        return Meeting.reconstitute(
                MeetingId.of(meetingId),
                UserId.of(hostId),
                ShortCode.of("ABC1234567"),
                null,
                null,
                null,
                null,
                MeetingType.INSTANT,
                status,
                MeetingSettings.defaults(),
                Instant.parse("2026-04-02T10:00:00Z"));
    }

    private static Recording pendingRecording(UUID meetingId, String egressId) {
        Recording recording = Recording.startFor(
                MeetingId.of(meetingId), LiveKitRoomName.fromMeetingId(MeetingId.of(meetingId)));
        recording.assignEgressId(LiveKitEgressId.of(egressId));
        recording.clearDomainEvents();
        return recording;
    }
}
