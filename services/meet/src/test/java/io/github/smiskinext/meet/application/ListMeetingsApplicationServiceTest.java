package io.github.smiskinext.meet.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.smiskinext.meet.application.query.ListMeetingsQuery;
import io.github.smiskinext.meet.application.result.ListMeetingsResult;
import io.github.smiskinext.meet.application.service.ListMeetingsApplicationService;
import io.github.smiskinext.meet.domain.ListMeetingsError;
import io.github.smiskinext.meet.domain.model.AdmissionPolicy;
import io.github.smiskinext.meet.domain.model.MeetingStatus;
import io.github.smiskinext.meet.domain.model.MeetingType;
import io.github.smiskinext.meet.domain.model.valueobject.MeetingSettings;
import io.github.smiskinext.meet.domain.port.MeetingCursorCodec;
import io.github.smiskinext.meet.domain.port.MeetingRepository;
import io.github.smiskinext.meet.domain.projection.MeetingSearchCriteria;
import io.github.smiskinext.meet.domain.projection.MeetingSortField;
import io.github.smiskinext.meet.domain.projection.MeetingSummary;
import io.github.smiskinext.shared.domain.CursorErrorCode;
import io.github.smiskinext.shared.domain.CursorPageResponse;
import io.github.smiskinext.shared.domain.Result;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ListMeetingsApplicationServiceTest {

    private static final String TENANT = "tenant-1";
    private static final String ACCOUNT = "account-1";

    private MeetingRepository meetingRepository;
    private MeetingCursorCodec cursorCodec;
    private ListMeetingsApplicationService service;

    @BeforeEach
    void setUp() {
        meetingRepository = mock(MeetingRepository.class);
        cursorCodec = mock(MeetingCursorCodec.class);
        service = new ListMeetingsApplicationService(meetingRepository, cursorCodec);
    }

    @Test
    void omittedPageSizeDefaultsToTwenty() {
        stubEmptyPage();

        service.execute(query(0, MeetingSortField.CREATED_AT, null));

        verify(meetingRepository).searchSummaries(any(MeetingSearchCriteria.class), eq(20));
    }

    @Test
    void pageSizeIsClampedToFifty() {
        stubEmptyPage();

        service.execute(query(500, MeetingSortField.CREATED_AT, null));

        verify(meetingRepository).searchSummaries(any(MeetingSearchCriteria.class), eq(50));
    }

    @Test
    void criteriaCarriesFiltersAndSort() {
        stubEmptyPage();
        ListMeetingsQuery query = new ListMeetingsQuery(
                TENANT,
                ACCOUNT,
                "creator-9",
                "retro",
                Set.of(MeetingStatus.SCHEDULED, MeetingStatus.RUNNING),
                "SMISKI-102",
                null,
                MeetingSortField.START_TIME,
                10,
                null);

        service.execute(query);

        ArgumentCaptor<MeetingSearchCriteria> captor =
                ArgumentCaptor.forClass(MeetingSearchCriteria.class);
        verify(meetingRepository).searchSummaries(captor.capture(), eq(10));
        MeetingSearchCriteria criteria = captor.getValue();
        assertThat(criteria.creatorId()).isNotNull();
        assertThat(criteria.creatorId().value()).isEqualTo("creator-9");
        assertThat(criteria.search()).isEqualTo("retro");
        assertThat(criteria.statuses())
                .containsExactlyInAnyOrder(MeetingStatus.SCHEDULED, MeetingStatus.RUNNING);
        assertThat(criteria.issueKey()).isEqualTo("SMISKI-102");
        assertThat(criteria.projectKey()).isNull();
        assertThat(criteria.sort()).isEqualTo(MeetingSortField.START_TIME);
        assertThat(criteria.position()).isNull();
    }

    @Test
    void projectKeyPropagatedIntoCriteria() {
        stubEmptyPage();
        ListMeetingsQuery query = new ListMeetingsQuery(
                TENANT,
                ACCOUNT,
                null,
                null,
                Set.of(),
                null,
                "SMISKI",
                MeetingSortField.CREATED_AT,
                20,
                null);

        service.execute(query);

        ArgumentCaptor<MeetingSearchCriteria> captor =
                ArgumentCaptor.forClass(MeetingSearchCriteria.class);
        verify(meetingRepository).searchSummaries(captor.capture(), eq(20));
        MeetingSearchCriteria criteria = captor.getValue();
        assertThat(criteria.projectKey()).isEqualTo("SMISKI");
    }

    @Test
    void invalidTokenYieldsInvalidCursorAndSkipsQuery() {
        when(cursorCodec.decode("bad")).thenReturn(Result.failure(CursorErrorCode.INVALID_CURSOR));

        Result<ListMeetingsResult, ListMeetingsError> result =
                service.execute(query(20, MeetingSortField.CREATED_AT, "bad"));

        assertThat(result.isFailure()).isTrue();
        assertThat(((Result.Failure<ListMeetingsResult, ListMeetingsError>) result).error())
                .isInstanceOf(ListMeetingsError.InvalidCursor.class);
        verify(meetingRepository, never())
                .searchSummaries(any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void tokenSortMismatchYieldsInvalidCursor() {
        UUID id = UUID.randomUUID();
        when(cursorCodec.decode("token"))
                .thenReturn(Result.success(new MeetingCursorCodec.DecodedCursor(
                        MeetingSortField.START_TIME, Instant.now(), id)));

        Result<ListMeetingsResult, ListMeetingsError> result =
                service.execute(query(20, MeetingSortField.CREATED_AT, "token"));

        assertThat(result.isFailure()).isTrue();
        assertThat(((Result.Failure<ListMeetingsResult, ListMeetingsError>) result).error())
                .isInstanceOf(ListMeetingsError.InvalidCursor.class);
        verify(meetingRepository, never())
                .searchSummaries(any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void matchingTokenBuildsPositionAndQueries() {
        UUID id = UUID.randomUUID();
        Instant ts = Instant.parse("2025-01-01T00:00:00Z");
        when(cursorCodec.decode("token"))
                .thenReturn(Result.success(
                        new MeetingCursorCodec.DecodedCursor(MeetingSortField.CREATED_AT, ts, id)));
        stubEmptyPage();

        service.execute(query(20, MeetingSortField.CREATED_AT, "token"));

        ArgumentCaptor<MeetingSearchCriteria> captor =
                ArgumentCaptor.forClass(MeetingSearchCriteria.class);
        verify(meetingRepository).searchSummaries(captor.capture(), eq(20));
        MeetingSearchCriteria.Position position = captor.getValue().position();
        assertThat(position).isNotNull();
        assertThat(position.sortValue()).isEqualTo(ts);
        assertThat(position.id()).isEqualTo(id);
    }

    @Test
    void nextPageTokenPresentWhenHasNext() {
        MeetingSummary summary = summary(Instant.parse("2025-01-02T00:00:00Z"), null);
        when(meetingRepository.searchSummaries(any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(CursorPageResponse.of(List.of(summary), 20, true));
        when(cursorCodec.encode(
                        eq(MeetingSortField.CREATED_AT), any(Instant.class), any(UUID.class)))
                .thenReturn("next-token");

        Result<ListMeetingsResult, ListMeetingsError> result =
                service.execute(query(20, MeetingSortField.CREATED_AT, null));

        ListMeetingsResult value =
                ((Result.Success<ListMeetingsResult, ListMeetingsError>) result).value();
        assertThat(value.hasNext()).isTrue();
        assertThat(value.nextPageToken()).isEqualTo("next-token");
        verify(cursorCodec).encode(MeetingSortField.CREATED_AT, summary.createdAt(), summary.id());
    }

    @Test
    void startTimeSortEncodesEffectiveStartTime() {
        Instant start = Instant.parse("2025-03-01T09:00:00Z");
        Instant created = Instant.parse("2025-02-01T09:00:00Z");
        MeetingSummary summary = summary(created, start);
        when(meetingRepository.searchSummaries(any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(CursorPageResponse.of(List.of(summary), 20, true));
        when(cursorCodec.encode(any(), any(), any())).thenReturn("t");

        service.execute(query(20, MeetingSortField.START_TIME, null));

        verify(cursorCodec).encode(MeetingSortField.START_TIME, start, summary.id());
    }

    @Test
    void startTimeSortFallsBackToCreatedAtWhenStartTimeNull() {
        Instant created = Instant.parse("2025-02-01T09:00:00Z");
        MeetingSummary summary = summary(created, null);
        when(meetingRepository.searchSummaries(any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(CursorPageResponse.of(List.of(summary), 20, true));
        when(cursorCodec.encode(any(), any(), any())).thenReturn("t");

        service.execute(query(20, MeetingSortField.START_TIME, null));

        verify(cursorCodec).encode(MeetingSortField.START_TIME, created, summary.id());
    }

    @Test
    void nextPageTokenAbsentOnLastPage() {
        MeetingSummary summary = summary(Instant.now(), null);
        when(meetingRepository.searchSummaries(any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(CursorPageResponse.of(List.of(summary), 20, false));

        Result<ListMeetingsResult, ListMeetingsError> result =
                service.execute(query(20, MeetingSortField.CREATED_AT, null));

        ListMeetingsResult value =
                ((Result.Success<ListMeetingsResult, ListMeetingsError>) result).value();
        assertThat(value.hasNext()).isFalse();
        assertThat(value.nextPageToken()).isNull();
        assertThat(value.items()).hasSize(1);
        verify(cursorCodec, never()).encode(any(), any(), any());
    }

    private void stubEmptyPage() {
        when(meetingRepository.searchSummaries(any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(CursorPageResponse.empty(20));
    }

    private static ListMeetingsQuery query(int pageSize, MeetingSortField sort, String pageToken) {
        return new ListMeetingsQuery(
                TENANT, ACCOUNT, null, null, Set.of(), null, null, sort, pageSize, pageToken);
    }

    private static MeetingSummary summary(Instant createdAt, Instant startTime) {
        MeetingSettings settings =
                new MeetingSettings(AdmissionPolicy.MANUAL_APPROVAL, 50, true, true, true, true);
        return new MeetingSummary(
                UUID.randomUUID(),
                "host-1",
                "abc-defg-hij",
                "Retro",
                "desc",
                "ISS-1",
                "SMISKI-1",
                "SMISKI",
                startTime,
                null,
                MeetingType.SCHEDULED,
                MeetingStatus.SCHEDULED,
                settings,
                createdAt);
    }
}
