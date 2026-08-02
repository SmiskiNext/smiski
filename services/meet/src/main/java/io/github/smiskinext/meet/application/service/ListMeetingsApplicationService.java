package io.github.smiskinext.meet.application.service;

import io.github.smiskinext.meet.application.mapper.MeetingSummaryMapper;
import io.github.smiskinext.meet.application.query.ListMeetingsQuery;
import io.github.smiskinext.meet.application.result.ListMeetingsResult;
import io.github.smiskinext.meet.application.usecase.ListMeetingsUseCase;
import io.github.smiskinext.meet.domain.ListMeetingsError;
import io.github.smiskinext.meet.domain.model.valueobject.AccountId;
import io.github.smiskinext.meet.domain.port.MeetingCursorCodec;
import io.github.smiskinext.meet.domain.port.MeetingRepository;
import io.github.smiskinext.meet.domain.projection.MeetingSearchCriteria;
import io.github.smiskinext.meet.domain.projection.MeetingSortField;
import io.github.smiskinext.meet.domain.projection.MeetingSummary;
import io.github.smiskinext.shared.domain.CursorPageResponse;
import io.github.smiskinext.shared.domain.Result;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lists tenant meetings with optional filters and keyset pagination.
 *
 * <p>Clamps the page size, decodes and validates the continuation token (binding it to the request
 * sort field), delegates the keyset query to the {@link MeetingRepository}, and encodes the next
 * page token from the last item when further pages exist.
 */
@Service
@Transactional(readOnly = true)
public class ListMeetingsApplicationService implements ListMeetingsUseCase {

    /** Default page size applied when a request omits {@code pageSize} or supplies a non-positive value. */
    public static final int DEFAULT_PAGE_SIZE = 20;

    /** Hard upper bound on the page size. */
    public static final int MAX_PAGE_SIZE = 50;

    private final MeetingRepository meetingRepository;
    private final MeetingCursorCodec cursorCodec;

    public ListMeetingsApplicationService(
            MeetingRepository meetingRepository, MeetingCursorCodec cursorCodec) {
        this.meetingRepository = meetingRepository;
        this.cursorCodec = cursorCodec;
    }

    @Override
    public Result<ListMeetingsResult, ListMeetingsError> execute(ListMeetingsQuery query) {
        int pageSize = resolvePageSize(query.pageSize());

        Result<MeetingSearchCriteria.@Nullable Position, ListMeetingsError> positionResult =
                resolvePosition(query.pageToken(), query.sort());
        if (positionResult
                instanceof Result.Failure<?, ListMeetingsError>(ListMeetingsError error)) {
            return Result.failure(error);
        }
        MeetingSearchCriteria.Position position = ((Result.Success<
                                MeetingSearchCriteria.Position, ListMeetingsError>)
                        positionResult)
                .value();

        MeetingSearchCriteria criteria = new MeetingSearchCriteria(
                query.creatorId() == null ? null : AccountId.of(query.creatorId()),
                query.search(),
                query.statuses(),
                query.issueKey(),
                query.projectKey(),
                query.sort(),
                position);

        CursorPageResponse<MeetingSummary> page =
                meetingRepository.searchSummaries(criteria, pageSize);

        List<ListMeetingsResult.Item> items =
                page.items().stream().map(MeetingSummaryMapper::toItem).toList();

        String nextPageToken = page.hasNext() ? encodeNextToken(query.sort(), page.items()) : null;

        return Result.success(new ListMeetingsResult(items, page.hasNext(), nextPageToken));
    }

    private static int resolvePageSize(int requested) {
        if (requested <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(requested, MAX_PAGE_SIZE);
    }

    private Result<MeetingSearchCriteria.@Nullable Position, ListMeetingsError> resolvePosition(
            @Nullable String pageToken, MeetingSortField sort) {
        if (pageToken == null || pageToken.isBlank()) {
            return Result.success(null);
        }
        return cursorCodec
                .decode(pageToken)
                .fold(
                        decoded -> decoded.sort() == sort
                                ? Result.success(new MeetingSearchCriteria.Position(
                                        decoded.sortValue(), decoded.id()))
                                : Result.failure(new ListMeetingsError.InvalidCursor()),
                        _ -> Result.failure(new ListMeetingsError.InvalidCursor()));
    }

    private String encodeNextToken(MeetingSortField sort, List<MeetingSummary> items) {
        MeetingSummary last = items.getLast();
        Instant sortValue = effectiveSortValue(sort, last);
        return cursorCodec.encode(sort, sortValue, last.id());
    }

    private static Instant effectiveSortValue(MeetingSortField sort, MeetingSummary summary) {
        if (sort == MeetingSortField.START_TIME && summary.startTime() != null) {
            return summary.startTime();
        }
        return summary.createdAt();
    }
}
