package io.github.smiskinext.meetingmanagement.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.smiskinext.meetingmanagement.application.command.ActivateRecordingCommand;
import io.github.smiskinext.meetingmanagement.application.command.FinalizeRecordingCommand;
import io.github.smiskinext.meetingmanagement.domain.PublishableEvent;
import io.github.smiskinext.meetingmanagement.domain.model.Recording;
import io.github.smiskinext.meetingmanagement.domain.model.RecordingStatus;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.LiveKitEgressId;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.LiveKitRoomName;
import io.github.smiskinext.meetingmanagement.domain.port.LiveKitPort;
import io.github.smiskinext.meetingmanagement.domain.port.RecordingRepository;
import io.github.phunguy65.zms.shared.domain.Result;
import io.github.phunguy65.zms.shared.domain.valueobject.MeetingId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class RecordingWebhookUseCaseTest {

    private static final String EGRESS_ID = "EG_WEBHOOK_123";

    @Mock
    RecordingRepository recordingRepository;

    @Mock
    LiveKitPort liveKitPort;

    @Mock
    ApplicationEventPublisher eventPublisher;

    private ActivateRecordingUseCase activateRecordingUseCase;
    private FinalizeRecordingUseCase finalizeRecordingUseCase;

    @BeforeEach
    void setUp() {
        activateRecordingUseCase = new ActivateRecordingUseCase(recordingRepository);
        finalizeRecordingUseCase =
                new FinalizeRecordingUseCase(recordingRepository, liveKitPort, eventPublisher);
    }

    @Test
    void activateRecording_ignoresDuplicateStartedWebhook() {
        Recording recording = startedRecording(EGRESS_ID);
        when(recordingRepository.findByEgressId(LiveKitEgressId.of(EGRESS_ID)))
                .thenReturn(Optional.of(recording));

        activateRecordingUseCase.execute(new ActivateRecordingCommand(EGRESS_ID));

        verify(recordingRepository, never()).save(any(Recording.class));
    }

    @Test
    void activateRecording_transitionsPendingToRecording() {
        Recording recording = pendingRecording(EGRESS_ID);
        when(recordingRepository.findByEgressId(LiveKitEgressId.of(EGRESS_ID)))
                .thenReturn(Optional.of(recording));
        when(recordingRepository.save(any(Recording.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        activateRecordingUseCase.execute(new ActivateRecordingCommand(EGRESS_ID));

        assertThat(recording.getStatus()).isEqualTo(RecordingStatus.RECORDING);
        verify(recordingRepository).save(recording);
    }

    @Test
    void activateRecording_ignoresMissingRecording() {
        when(recordingRepository.findByEgressId(LiveKitEgressId.of(EGRESS_ID)))
                .thenReturn(Optional.empty());

        activateRecordingUseCase.execute(new ActivateRecordingCommand(EGRESS_ID));

        verify(recordingRepository, never()).save(any(Recording.class));
    }

    @Test
    void finalizeRecording_completesPendingRecordingFromWebhook() {
        Recording recording = pendingRecording(EGRESS_ID);
        when(recordingRepository.findByEgressId(LiveKitEgressId.of(EGRESS_ID)))
                .thenReturn(Optional.of(recording));
        when(recordingRepository.save(any(Recording.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(liveKitPort.updateRoomMetadata(any(), any())).thenReturn(Result.success());

        finalizeRecordingUseCase.execute(new FinalizeRecordingCommand(
                EGRESS_ID,
                true,
                "s3://recordings/meeting.mp4",
                "meetings/abc/egress.mp4",
                null,
                42,
                2048L));

        assertThat(recording.getStatus()).isEqualTo(RecordingStatus.COMPLETED);
        assertThat(recording.getFileUrl()).contains("s3://recordings/meeting.mp4");
        assertThat(recording.getStoragePath()).contains("meetings/abc/egress.mp4");
        assertThat(recording.getDurationSeconds()).isEqualTo(42);
        assertThat(recording.getFileSizeBytes()).isEqualTo(2048L);
        verify(recordingRepository).save(recording);
        verify(eventPublisher).publishEvent(any(PublishableEvent.class));
        verify(liveKitPort)
                .updateRoomMetadata(
                        LiveKitRoomName.fromMeetingId(recording.getMeetingId()),
                        "{\"recording\":false}");
    }

    @Test
    void finalizeRecording_ignoresMissingRecording() {
        when(recordingRepository.findByEgressId(LiveKitEgressId.of(EGRESS_ID)))
                .thenReturn(Optional.empty());

        finalizeRecordingUseCase.execute(new FinalizeRecordingCommand(
                EGRESS_ID,
                true,
                "s3://recordings/meeting.mp4",
                "meetings/abc/egress.mp4",
                null,
                42,
                2048L));

        verify(recordingRepository, never()).save(any(Recording.class));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void finalizeRecording_marksRecordingFailedWhenWebhookContainsError() {
        Recording recording = startedRecording(EGRESS_ID);
        when(recordingRepository.findByEgressId(LiveKitEgressId.of(EGRESS_ID)))
                .thenReturn(Optional.of(recording));
        when(recordingRepository.save(any(Recording.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(liveKitPort.updateRoomMetadata(any(), any())).thenReturn(Result.success());

        finalizeRecordingUseCase.execute(new FinalizeRecordingCommand(
                EGRESS_ID, false, null, null, "egress crashed", 0, 0L));

        assertThat(recording.getStatus()).isEqualTo(RecordingStatus.FAILED);
        assertThat(recording.getErrorMessage()).contains("egress crashed");
        verify(recordingRepository).save(recording);
        verify(eventPublisher).publishEvent(any(PublishableEvent.class));
        verify(liveKitPort)
                .updateRoomMetadata(
                        LiveKitRoomName.fromMeetingId(recording.getMeetingId()),
                        "{\"recording\":false}");
    }

    @Test
    void finalizeRecording_completesAlreadyRecordingSession() {
        Recording recording = startedRecording(EGRESS_ID);
        when(recordingRepository.findByEgressId(LiveKitEgressId.of(EGRESS_ID)))
                .thenReturn(Optional.of(recording));
        when(recordingRepository.save(any(Recording.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(liveKitPort.updateRoomMetadata(any(), any())).thenReturn(Result.success());

        finalizeRecordingUseCase.execute(new FinalizeRecordingCommand(
                EGRESS_ID,
                true,
                "s3://recordings/meeting.mp4",
                "meetings/abc/egress.mp4",
                null,
                42,
                2048L));

        assertThat(recording.getStatus()).isEqualTo(RecordingStatus.COMPLETED);
        verify(recordingRepository).save(recording);
        verify(liveKitPort)
                .updateRoomMetadata(
                        LiveKitRoomName.fromMeetingId(recording.getMeetingId()),
                        "{\"recording\":false}");
    }

    @Test
    void finalizeRecording_ignoresDuplicateEndedWebhook() {
        Recording recording = completedRecording(EGRESS_ID);
        when(recordingRepository.findByEgressId(LiveKitEgressId.of(EGRESS_ID)))
                .thenReturn(Optional.of(recording));

        finalizeRecordingUseCase.execute(new FinalizeRecordingCommand(
                EGRESS_ID,
                true,
                "s3://recordings/meeting.mp4",
                "meetings/abc/egress.mp4",
                null,
                42,
                2048L));

        verify(recordingRepository, never()).save(any(Recording.class));
        verifyNoInteractions(eventPublisher);
    }

    private static Recording pendingRecording(String egressId) {
        UUID meetingId = UUID.randomUUID();
        Recording recording = Recording.startFor(
                MeetingId.of(meetingId), LiveKitRoomName.fromMeetingId(MeetingId.of(meetingId)));
        recording.assignEgressId(LiveKitEgressId.of(egressId));
        recording.clearDomainEvents();
        return recording;
    }

    private static Recording startedRecording(String egressId) {
        Recording recording = pendingRecording(egressId);
        assertThat(recording.activate(LiveKitEgressId.of(egressId)))
                .isInstanceOf(io.github.phunguy65.zms.shared.domain.Result.Success.class);
        recording.clearDomainEvents();
        return recording;
    }

    private static Recording completedRecording(String egressId) {
        Recording recording = startedRecording(egressId);
        assertThat(recording.complete(
                        "s3://recordings/meeting.mp4", "meetings/abc/egress.mp4", null, 42, 2048L))
                .isInstanceOf(io.github.phunguy65.zms.shared.domain.Result.Success.class);
        recording.clearDomainEvents();
        return recording;
    }
}
