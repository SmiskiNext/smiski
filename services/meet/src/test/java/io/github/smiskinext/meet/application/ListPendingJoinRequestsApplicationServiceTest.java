package io.github.smiskinext.meet.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.smiskinext.meet.application.query.ListPendingJoinRequestsQuery;
import io.github.smiskinext.meet.application.result.ListPendingJoinRequestsResult;
import io.github.smiskinext.meet.application.service.ListPendingJoinRequestsApplicationService;
import io.github.smiskinext.meet.domain.MeetingError;
import io.github.smiskinext.meet.domain.model.AdmissionPolicy;
import io.github.smiskinext.meet.domain.model.JoinRequestStatus;
import io.github.smiskinext.meet.domain.model.MeetingStatus;
import io.github.smiskinext.meet.domain.model.MeetingType;
import io.github.smiskinext.meet.domain.model.valueobject.MeetingSettings;
import io.github.smiskinext.meet.domain.port.JoinRequestRepository;
import io.github.smiskinext.meet.domain.port.MeetingRepository;
import io.github.smiskinext.meet.domain.projection.JoinRequestSummary;
import io.github.smiskinext.meet.domain.projection.MeetingDetail;
import io.github.smiskinext.shared.domain.OffsetPageResponse;
import io.github.smiskinext.shared.domain.Result;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ListPendingJoinRequestsApplicationServiceTest {

    private static final String TENANT_ID = "tenant-1";
    private static final String HOST_ID = "host-account";
    private static final UUID MEETING_ID = UUID.randomUUID();

    private MeetingRepository meetingRepository;
    private JoinRequestRepository joinRequestRepository;
    private ListPendingJoinRequestsApplicationService service;

    @BeforeEach
    void setUp() {
        meetingRepository = mock(MeetingRepository.class);
        joinRequestRepository = mock(JoinRequestRepository.class);
        service = new ListPendingJoinRequestsApplicationService(
                meetingRepository, joinRequestRepository);
    }

    @Test
    void hostWithThreePendingRequestsReturnsThreeItems() {
        stubMeeting();
        List<JoinRequestSummary> summaries = List.of(
                summary("account-1", "Alice", 0),
                summary("account-2", "Bob", 1),
                summary("account-3", "Charlie", 2));
        when(joinRequestRepository.findPendingSummariesByMeetingId(MEETING_ID, 0, 20))
                .thenReturn(OffsetPageResponse.of(summaries, 20, 0, false));
        when(joinRequestRepository.countPendingByMeetingId(MEETING_ID)).thenReturn(3L);

        Result<ListPendingJoinRequestsResult, MeetingError> result = service.execute(query(0, 20));

        assertThat(result.isSuccess()).isTrue();
        ListPendingJoinRequestsResult value = success(result);
        assertThat(value.page().items()).hasSize(3);
        assertThat(value.page().offset()).isZero();
        assertThat(value.page().pageSize()).isEqualTo(20);
        assertThat(value.total()).isEqualTo(3L);
    }

    @Test
    void emptyQueueReturnsEmptyResults() {
        stubMeeting();
        when(joinRequestRepository.findPendingSummariesByMeetingId(MEETING_ID, 0, 20))
                .thenReturn(OffsetPageResponse.empty(20, 0));
        when(joinRequestRepository.countPendingByMeetingId(MEETING_ID)).thenReturn(0L);

        Result<ListPendingJoinRequestsResult, MeetingError> result = service.execute(query(0, 20));

        assertThat(result.isSuccess()).isTrue();
        ListPendingJoinRequestsResult value = success(result);
        assertThat(value.page().items()).isEmpty();
        assertThat(value.total()).isZero();
    }

    @Test
    void secondPageWithOffsetReturnsCorrectSlice() {
        stubMeeting();
        List<JoinRequestSummary> page =
                List.of(summary("account-3", "Charlie", 2), summary("account-4", "Dave", 3));
        when(joinRequestRepository.findPendingSummariesByMeetingId(MEETING_ID, 2, 2))
                .thenReturn(OffsetPageResponse.of(page, 2, 2, true));
        when(joinRequestRepository.countPendingByMeetingId(MEETING_ID)).thenReturn(5L);

        Result<ListPendingJoinRequestsResult, MeetingError> result = service.execute(query(2, 2));

        assertThat(result.isSuccess()).isTrue();
        ListPendingJoinRequestsResult value = success(result);
        assertThat(value.page().items()).hasSize(2);
        assertThat(value.page().offset()).isEqualTo(2);
        assertThat(value.total()).isEqualTo(5L);
    }

    @Test
    void nonHostCallerReturnsNotOwner() {
        stubMeeting();

        Result<ListPendingJoinRequestsResult, MeetingError> result = service.execute(
                new ListPendingJoinRequestsQuery(MEETING_ID, TENANT_ID, "someone-else", 0, 20));

        assertThat(result.isFailure()).isTrue();
        assertThat(failure(result)).isInstanceOf(MeetingError.NotOwner.class);
    }

    @Test
    void meetingNotFoundReturnsMeetingNotFound() {
        when(meetingRepository.findDetailById(MEETING_ID)).thenReturn(Optional.empty());

        Result<ListPendingJoinRequestsResult, MeetingError> result = service.execute(query(0, 20));

        assertThat(result.isFailure()).isTrue();
        assertThat(failure(result)).isInstanceOf(MeetingError.MeetingNotFound.class);
    }

    private ListPendingJoinRequestsQuery query(int offset, int pageSize) {
        return new ListPendingJoinRequestsQuery(MEETING_ID, TENANT_ID, HOST_ID, offset, pageSize);
    }

    private void stubMeeting() {
        MeetingSettings settings =
                new MeetingSettings(AdmissionPolicy.MANUAL_APPROVAL, 50, true, true, true, true);
        MeetingDetail detail = new MeetingDetail(
                MEETING_ID,
                HOST_ID,
                "abc123def0",
                MeetingType.INSTANT,
                MeetingStatus.RUNNING,
                "Sprint",
                "desc",
                "10001",
                "PROJ-1",
                "PROJ",
                settings,
                null,
                null,
                "UTC",
                "host@example.com",
                "Host User",
                UUID.randomUUID().toString(),
                0,
                Instant.now());
        when(meetingRepository.findDetailById(MEETING_ID)).thenReturn(Optional.of(detail));
    }

    private JoinRequestSummary summary(String accountId, String displayName, int secondsOffset) {
        Instant requested = Instant.now().plusSeconds(secondsOffset);
        return new JoinRequestSummary(
                UUID.randomUUID(),
                MEETING_ID,
                accountId,
                displayName,
                JoinRequestStatus.PENDING,
                requested,
                requested.plusSeconds(300));
    }

    private static ListPendingJoinRequestsResult success(
            Result<ListPendingJoinRequestsResult, MeetingError> result) {
        return ((Result.Success<ListPendingJoinRequestsResult, MeetingError>) result).value();
    }

    private static MeetingError failure(
            Result<ListPendingJoinRequestsResult, MeetingError> result) {
        return ((Result.Failure<ListPendingJoinRequestsResult, MeetingError>) result).error();
    }
}
