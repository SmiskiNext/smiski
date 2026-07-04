package io.github.smiskinext.meetingmanagement.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.smiskinext.meetingmanagement.application.RecordingResponseMapper;
import io.github.smiskinext.meetingmanagement.application.query.GetMeetingRecordingsQuery;
import io.github.smiskinext.meetingmanagement.domain.model.RecordingStatus;
import io.github.smiskinext.meetingmanagement.domain.port.RecordingRepository;
import io.github.smiskinext.meetingmanagement.domain.port.StoragePort;
import io.github.smiskinext.meetingmanagement.domain.projection.RecordingSummary;
import io.github.phunguy65.zms.shared.domain.CursorPageResponse;
import io.github.phunguy65.zms.shared.domain.ScrollCursor;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetMeetingRecordingsUseCaseTest {

    @Mock
    RecordingRepository recordingRepository;

    @Mock
    StoragePort storagePort;

    private GetMeetingRecordingsUseCase useCase;

    @BeforeEach
    void setUp() {
        var mapper = new RecordingResponseMapper(storagePort);
        useCase = new GetMeetingRecordingsUseCase(recordingRepository, mapper);
    }

    @Test
    void execute_mapsRecordingSummariesToPlainUuidResponses() {
        UUID meetingId = UUID.randomUUID();
        UUID firstRecordingId = UUID.randomUUID();
        UUID secondRecordingId = UUID.randomUUID();
        ScrollCursor cursor =
                new ScrollCursor(Instant.parse("2026-04-01T09:00:00Z"), UUID.randomUUID());
        RecordingSummary firstSummary = new RecordingSummary(
                firstRecordingId,
                meetingId,
                "https://cdn.example/video.mp4",
                "meetings/abc/recording.mp4",
                "https://cdn.example/thumb.jpg",
                RecordingStatus.COMPLETED,
                Instant.parse("2026-04-01T10:00:00Z"),
                Instant.parse("2026-04-01T10:30:00Z"),
                1800,
                2048L,
                Instant.parse("2026-04-01T09:59:00Z"));
        RecordingSummary secondSummary = new RecordingSummary(
                secondRecordingId,
                meetingId,
                null,
                null,
                null,
                RecordingStatus.PENDING,
                Instant.parse("2026-04-01T11:00:00Z"),
                null,
                0,
                0L,
                Instant.parse("2026-04-01T10:59:00Z"));
        when(recordingRepository.findSummariesByMeetingId(meetingId, cursor, 5))
                .thenReturn(CursorPageResponse.of(
                        java.util.List.of(firstSummary, secondSummary), 5, false));
        when(storagePort.generatePresignedUrl(
                        eq("meetings/abc/recording.mp4"), any(Duration.class)))
                .thenReturn("https://presigned.example/video.mp4");

        var result = useCase.execute(new GetMeetingRecordingsQuery(meetingId, 5, cursor));

        assertThat(result.items()).hasSize(2);
        assertThat(result.items().getFirst()).satisfies(recording -> {
            assertThat(recording.id()).isEqualTo(firstRecordingId);
            assertThat(recording.meetingId()).isEqualTo(meetingId);
            assertThat(recording.fileUrl()).isEqualTo("https://presigned.example/video.mp4");
            assertThat(recording.thumbnailUrl()).isEqualTo("https://cdn.example/thumb.jpg");
            assertThat(recording.status()).isEqualTo(RecordingStatus.COMPLETED);
            assertThat(recording.startedAt()).isEqualTo(Instant.parse("2026-04-01T10:00:00Z"));
            assertThat(recording.endedAt()).isEqualTo(Instant.parse("2026-04-01T10:30:00Z"));
            assertThat(recording.durationSeconds()).isEqualTo(1800);
            assertThat(recording.fileSizeBytes()).isEqualTo(2048L);
            assertThat(recording.createdAt()).isEqualTo(Instant.parse("2026-04-01T09:59:00Z"));
        });
        assertThat(result.items().get(1)).satisfies(recording -> {
            assertThat(recording.id()).isEqualTo(secondRecordingId);
            assertThat(recording.fileUrl()).isNull();
            assertThat(recording.thumbnailUrl()).isNull();
            assertThat(recording.status()).isEqualTo(RecordingStatus.PENDING);
            assertThat(recording.endedAt()).isNull();
            assertThat(recording.durationSeconds()).isZero();
            assertThat(recording.fileSizeBytes()).isZero();
        });
        verify(recordingRepository).findSummariesByMeetingId(meetingId, cursor, 5);
    }

    @Test
    void execute_normalizesPageSizeLessThanOne() {
        UUID meetingId = UUID.randomUUID();
        when(recordingRepository.findSummariesByMeetingId(meetingId, null, 1))
                .thenReturn(CursorPageResponse.empty(1));

        var result = useCase.execute(new GetMeetingRecordingsQuery(meetingId, 0, null));

        assertThat(result.items()).isEmpty();
        assertThat(result.pageSize()).isEqualTo(1);
        verify(recordingRepository).findSummariesByMeetingId(meetingId, null, 1);
    }

    @Test
    void execute_capsPageSizeAtHundred() {
        UUID meetingId = UUID.randomUUID();
        when(recordingRepository.findSummariesByMeetingId(meetingId, null, 100))
                .thenReturn(CursorPageResponse.empty(100));

        var result = useCase.execute(new GetMeetingRecordingsQuery(meetingId, 101, null));

        assertThat(result.pageSize()).isEqualTo(100);
        verify(recordingRepository).findSummariesByMeetingId(meetingId, null, 100);
    }

    @Test
    void execute_returnsEmptyPageWhenNoRecordingsExist() {
        UUID meetingId = UUID.randomUUID();
        when(recordingRepository.findSummariesByMeetingId(meetingId, null, 10))
                .thenReturn(CursorPageResponse.empty(10));

        var result = useCase.execute(new GetMeetingRecordingsQuery(meetingId, 10, null));

        assertThat(result.items()).isEmpty();
        assertThat(result.hasNext()).isFalse();
    }
}
