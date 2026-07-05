package io.github.smiskinext.meetingmanagement.infrastructure.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.smiskinext.meetingmanagement.domain.PublishableEvent;
import io.github.smiskinext.meetingmanagement.domain.model.Recording;
import io.github.smiskinext.meetingmanagement.domain.model.RecordingStatus;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.LiveKitRoomName;
import io.github.smiskinext.meetingmanagement.domain.port.RecordingRepository;
import io.github.smiskinext.meetingmanagement.infrastructure.config.LiveKitProperties;
import io.github.smiskinext.shared.domain.valueobject.MeetingId;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class RecordingCleanupJobTest {

    @Mock
    RecordingRepository recordingRepository;

    @Mock
    ApplicationEventPublisher eventPublisher;

    private RecordingCleanupJob recordingCleanupJob;

    @BeforeEach
    void setUp() {
        LiveKitProperties properties = new LiveKitProperties();
        properties.getRecording().setPendingMaxAge(Duration.ofMinutes(7));
        recordingCleanupJob =
                new RecordingCleanupJob(recordingRepository, properties, eventPublisher);
    }

    @Test
    void failStalePendingRecordings_marksPendingRecordingsFailed() {
        Recording staleRecording = pendingRecording();
        when(recordingRepository.findPendingCreatedBefore(any(Instant.class)))
                .thenReturn(List.of(staleRecording));
        when(recordingRepository.save(any(Recording.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Instant lowerBound = Instant.now().minus(Duration.ofMinutes(7).plusSeconds(2));
        recordingCleanupJob.failStalePendingRecordings();
        Instant upperBound = Instant.now().minus(Duration.ofMinutes(7).minusSeconds(2));

        ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(recordingRepository).findPendingCreatedBefore(cutoffCaptor.capture());
        assertThat(cutoffCaptor.getValue()).isBetween(lowerBound, upperBound);
        assertThat(staleRecording.getStatus()).isEqualTo(RecordingStatus.FAILED);
        assertThat(staleRecording.getErrorMessage())
                .contains("Timed out waiting for LiveKit egress_started webhook");
        verify(recordingRepository).save(staleRecording);
        verify(eventPublisher).publishEvent(any(PublishableEvent.class));
    }

    @Test
    void failStalePendingRecordings_ignoresRecentPendingRecordings() {
        when(recordingRepository.findPendingCreatedBefore(any(Instant.class)))
                .thenReturn(List.of());

        recordingCleanupJob.failStalePendingRecordings();

        verify(recordingRepository, never()).save(any(Recording.class));
        verify(eventPublisher, never()).publishEvent(any(PublishableEvent.class));
    }

    private static Recording pendingRecording() {
        UUID meetingId = UUID.randomUUID();
        Recording recording = Recording.startFor(
                MeetingId.of(meetingId), LiveKitRoomName.fromMeetingId(MeetingId.of(meetingId)));
        recording.clearDomainEvents();
        return recording;
    }
}
