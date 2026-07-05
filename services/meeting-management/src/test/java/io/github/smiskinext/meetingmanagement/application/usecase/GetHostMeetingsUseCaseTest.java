package io.github.smiskinext.meetingmanagement.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.smiskinext.meetingmanagement.application.query.GetHostMeetingsQuery;
import io.github.smiskinext.meetingmanagement.domain.model.MeetingStatus;
import io.github.smiskinext.meetingmanagement.domain.model.MeetingType;
import io.github.smiskinext.meetingmanagement.domain.port.MeetingRepository;
import io.github.smiskinext.meetingmanagement.domain.projection.MeetingSettingsSummary;
import io.github.smiskinext.meetingmanagement.domain.projection.MeetingSummary;
import io.github.smiskinext.shared.domain.CursorPageResponse;
import io.github.smiskinext.shared.domain.ScrollCursor;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetHostMeetingsUseCaseTest {

    @Mock
    MeetingRepository meetingRepository;

    private GetHostMeetingsUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetHostMeetingsUseCase(meetingRepository);
    }

    @Test
    void execute_mapsMeetingSummariesToResponses() {
        UUID hostId = UUID.randomUUID();
        UUID firstMeetingId = UUID.randomUUID();
        UUID secondMeetingId = UUID.randomUUID();
        ScrollCursor cursor =
                new ScrollCursor(Instant.parse("2026-04-01T09:00:00Z"), UUID.randomUUID());
        MeetingSummary firstSummary = new MeetingSummary(
                firstMeetingId,
                hostId,
                "ABC123",
                "Design Review",
                "Sprint planning",
                Instant.parse("2026-04-02T10:00:00Z"),
                Instant.parse("2026-04-02T11:00:00Z"),
                MeetingType.SCHEDULED,
                MeetingStatus.SCHEDULED,
                new MeetingSettingsSummary(
                        "MANUAL_APPROVAL", true, 50, true, true, true, true, true),
                Instant.parse("2026-04-01T08:00:00Z"));
        MeetingSummary secondSummary = new MeetingSummary(
                secondMeetingId,
                hostId,
                "XYZ789",
                null,
                null,
                null,
                null,
                MeetingType.INSTANT,
                MeetingStatus.LIVE,
                new MeetingSettingsSummary(
                        "AUTO_APPROVE", false, 10, true, false, true, true, false),
                Instant.parse("2026-04-01T09:00:00Z"));
        when(meetingRepository.findSummariesByHostId(hostId, cursor, 10))
                .thenReturn(CursorPageResponse.of(
                        java.util.List.of(firstSummary, secondSummary), 10, true));

        var result = useCase.execute(new GetHostMeetingsQuery(hostId, 10, cursor));

        assertThat(result.items()).hasSize(2);
        assertThat(result.items().getFirst()).satisfies(meeting -> {
            assertThat(meeting.id()).isEqualTo(firstMeetingId);
            assertThat(meeting.hostId()).isEqualTo(hostId);
            assertThat(meeting.shortCode()).isEqualTo("ABC123");
            assertThat(meeting.title()).isEqualTo("Design Review");
            assertThat(meeting.description()).isEqualTo("Sprint planning");
            assertThat(meeting.startTime()).isEqualTo(Instant.parse("2026-04-02T10:00:00Z"));
            assertThat(meeting.endTime()).isEqualTo(Instant.parse("2026-04-02T11:00:00Z"));
            assertThat(meeting.type()).isEqualTo(MeetingType.SCHEDULED);
            assertThat(meeting.status()).isEqualTo(MeetingStatus.SCHEDULED);
            assertThat(meeting.createdAt()).isEqualTo(Instant.parse("2026-04-01T08:00:00Z"));
            assertThat(meeting.settings().admissionPolicy()).isEqualTo("MANUAL_APPROVAL");
            assertThat(meeting.settings().allowGuest()).isTrue();
            assertThat(meeting.settings().maxParticipants()).isEqualTo(50);
            assertThat(meeting.settings().allowScreenShare()).isTrue();
            assertThat(meeting.settings().chatEnabled()).isTrue();
            assertThat(meeting.settings().allowMicrophone()).isTrue();
            assertThat(meeting.settings().allowVideo()).isTrue();
            assertThat(meeting.settings().requirePassword()).isTrue();
        });
        assertThat(result.items().get(1)).satisfies(meeting -> {
            assertThat(meeting.id()).isEqualTo(secondMeetingId);
            assertThat(meeting.title()).isNull();
            assertThat(meeting.description()).isNull();
            assertThat(meeting.startTime()).isNull();
            assertThat(meeting.endTime()).isNull();
            assertThat(meeting.type()).isEqualTo(MeetingType.INSTANT);
            assertThat(meeting.status()).isEqualTo(MeetingStatus.LIVE);
            assertThat(meeting.settings().allowGuest()).isFalse();
            assertThat(meeting.settings().requirePassword()).isFalse();
        });
        assertThat(result.hasNext()).isTrue();
        verify(meetingRepository).findSummariesByHostId(hostId, cursor, 10);
    }

    @Test
    void execute_normalizesPageSizeLessThanOne() {
        UUID hostId = UUID.randomUUID();
        when(meetingRepository.findSummariesByHostId(hostId, null, 1))
                .thenReturn(CursorPageResponse.empty(1));

        var result = useCase.execute(new GetHostMeetingsQuery(hostId, 0, null));

        assertThat(result.items()).isEmpty();
        assertThat(result.pageSize()).isEqualTo(1);
        verify(meetingRepository).findSummariesByHostId(hostId, null, 1);
    }

    @Test
    void execute_capsPageSizeAtHundred() {
        UUID hostId = UUID.randomUUID();
        when(meetingRepository.findSummariesByHostId(hostId, null, 100))
                .thenReturn(CursorPageResponse.empty(100));

        var result = useCase.execute(new GetHostMeetingsQuery(hostId, 150, null));

        assertThat(result.pageSize()).isEqualTo(100);
        verify(meetingRepository).findSummariesByHostId(hostId, null, 100);
    }

    @Test
    void execute_returnsEmptyPageWhenNoMeetingsExist() {
        UUID hostId = UUID.randomUUID();
        when(meetingRepository.findSummariesByHostId(hostId, null, 10))
                .thenReturn(CursorPageResponse.empty(10));

        var result = useCase.execute(new GetHostMeetingsQuery(hostId, 10, null));

        assertThat(result.items()).isEmpty();
        assertThat(result.hasNext()).isFalse();
    }
}
