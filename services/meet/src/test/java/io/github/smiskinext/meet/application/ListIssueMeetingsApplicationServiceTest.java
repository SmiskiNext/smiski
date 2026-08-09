package io.github.smiskinext.meet.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.smiskinext.meet.application.query.ListIssueMeetingsQuery;
import io.github.smiskinext.meet.application.result.ListIssueMeetingsResult;
import io.github.smiskinext.meet.application.result.ListMeetingsResult;
import io.github.smiskinext.meet.application.service.ListIssueMeetingsApplicationService;
import io.github.smiskinext.meet.domain.ListMeetingsError;
import io.github.smiskinext.meet.domain.model.AdmissionPolicy;
import io.github.smiskinext.meet.domain.model.MeetingStatus;
import io.github.smiskinext.meet.domain.model.MeetingType;
import io.github.smiskinext.meet.domain.model.valueobject.MeetingSettings;
import io.github.smiskinext.meet.domain.port.MeetingRepository;
import io.github.smiskinext.meet.domain.port.MeetingRepository.IssueMeetingPage;
import io.github.smiskinext.meet.domain.projection.MeetingSummary;
import io.github.smiskinext.shared.domain.OffsetPageResponse;
import io.github.smiskinext.shared.domain.Result;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ListIssueMeetingsApplicationServiceTest {

    private static final String TENANT_ID = "tenant-1";
    private static final String ACCOUNT_ID = "account-1";
    private static final String ISSUE_ID = "10102";

    private MeetingRepository meetingRepository;
    private ListIssueMeetingsApplicationService service;

    @BeforeEach
    void setUp() {
        meetingRepository = mock(MeetingRepository.class);
        service = new ListIssueMeetingsApplicationService(meetingRepository);
    }

    @Test
    void issueWithMeetingsReturnsRequestedPageWithTotalAndCorrectOrder() {
        MeetingSummary newest = summary(Instant.parse("2025-01-03T00:00:00Z"));
        MeetingSummary middle = summary(Instant.parse("2025-01-02T00:00:00Z"));
        MeetingSummary oldest = summary(Instant.parse("2025-01-01T00:00:00Z"));

        when(meetingRepository.findSummariesByIssueId(ISSUE_ID, 0, 20))
                .thenReturn(new IssueMeetingPage(
                        OffsetPageResponse.of(List.of(newest, middle, oldest), 20, 0, false), 3));

        Result<ListIssueMeetingsResult, ListMeetingsError> result =
                service.execute(new ListIssueMeetingsQuery(ISSUE_ID, TENANT_ID, ACCOUNT_ID, 0, 20));

        assertThat(result.isSuccess()).isTrue();
        ListIssueMeetingsResult value = success(result);
        assertThat(value.items()).hasSize(3);
        assertThat(value.total()).isEqualTo(3);
        assertThat(value.offset()).isZero();
        assertThat(value.pageSize()).isEqualTo(20);
        assertThat(value.hasNext()).isFalse();

        List<Instant> createdAts =
                value.items().stream().map(ListMeetingsResult.Item::createdAt).toList();
        assertThat(createdAts).isSortedAccordingTo((a, b) -> b.compareTo(a));
    }

    @Test
    void issueWithNoMeetingsReturnsEmptyDataAndTotalZero() {
        when(meetingRepository.findSummariesByIssueId("99999", 0, 20))
                .thenReturn(new IssueMeetingPage(OffsetPageResponse.empty(20, 0), 0));

        Result<ListIssueMeetingsResult, ListMeetingsError> result =
                service.execute(new ListIssueMeetingsQuery("99999", TENANT_ID, ACCOUNT_ID, 0, 20));

        assertThat(result.isSuccess()).isTrue();
        ListIssueMeetingsResult value = success(result);
        assertThat(value.items()).isEmpty();
        assertThat(value.total()).isZero();
        assertThat(value.hasNext()).isFalse();
    }

    @Test
    void offsetBeyondTotalReturnsEmptyDataWithRealTotalAndHasNextFalse() {
        when(meetingRepository.findSummariesByIssueId(ISSUE_ID, 10, 2))
                .thenReturn(new IssueMeetingPage(OffsetPageResponse.empty(2, 10), 5));

        Result<ListIssueMeetingsResult, ListMeetingsError> result =
                service.execute(new ListIssueMeetingsQuery(ISSUE_ID, TENANT_ID, ACCOUNT_ID, 10, 2));

        assertThat(result.isSuccess()).isTrue();
        ListIssueMeetingsResult value = success(result);
        assertThat(value.items()).isEmpty();
        assertThat(value.total()).isEqualTo(5);
        assertThat(value.offset()).isEqualTo(10);
        assertThat(value.pageSize()).isEqualTo(2);
        assertThat(value.hasNext()).isFalse();
    }

    @Test
    void offsetAndPageSizePassedThroughUnchanged() {
        when(meetingRepository.findSummariesByIssueId(ISSUE_ID, 4, 2))
                .thenReturn(new IssueMeetingPage(
                        OffsetPageResponse.of(List.of(summary(Instant.now())), 2, 4, false), 5));

        Result<ListIssueMeetingsResult, ListMeetingsError> result =
                service.execute(new ListIssueMeetingsQuery(ISSUE_ID, TENANT_ID, ACCOUNT_ID, 4, 2));

        assertThat(result.isSuccess()).isTrue();
        ListIssueMeetingsResult value = success(result);
        assertThat(value.offset()).isEqualTo(4);
        assertThat(value.pageSize()).isEqualTo(2);
    }

    @Test
    void mappingUsesMeetingSummaryMapper() {
        MeetingSummary summary = summary(Instant.parse("2025-01-01T00:00:00Z"));
        when(meetingRepository.findSummariesByIssueId(ISSUE_ID, 0, 20))
                .thenReturn(new IssueMeetingPage(
                        OffsetPageResponse.of(List.of(summary), 20, 0, false), 1));

        Result<ListIssueMeetingsResult, ListMeetingsError> result =
                service.execute(new ListIssueMeetingsQuery(ISSUE_ID, TENANT_ID, ACCOUNT_ID, 0, 20));

        ListIssueMeetingsResult value = success(result);
        ListMeetingsResult.Item item = value.items().getFirst();
        assertThat(item.id()).isEqualTo(summary.id());
        assertThat(item.hostId()).isEqualTo(summary.hostId());
        assertThat(item.shortCode()).isEqualTo(summary.shortCode());
        assertThat(item.issueId()).isEqualTo(summary.issueId());
        assertThat(item.issueKey()).isEqualTo(summary.issueKey());
        assertThat(item.projectKey()).isEqualTo(summary.projectKey());
        assertThat(item.settings().admissionPolicy())
                .isEqualTo(summary.settings().admissionPolicy().name());
    }

    private static MeetingSummary summary(Instant createdAt) {
        MeetingSettings settings =
                new MeetingSettings(AdmissionPolicy.MANUAL_APPROVAL, 50, true, true, true, true);
        return new MeetingSummary(
                UUID.randomUUID(),
                "host-1",
                "abc-defg-hij",
                "Sprint planning",
                "Plan the sprint",
                ISSUE_ID,
                "SMISKI-102",
                "SMISKI",
                null,
                null,
                MeetingType.SCHEDULED,
                MeetingStatus.SCHEDULED,
                "Host Display Name",
                settings,
                createdAt);
    }

    private static ListIssueMeetingsResult success(
            Result<ListIssueMeetingsResult, ListMeetingsError> result) {
        return ((Result.Success<ListIssueMeetingsResult, ListMeetingsError>) result).value();
    }
}
