package io.github.smiskinext.meet.application.service;

import io.github.smiskinext.meet.application.mapper.MeetingSummaryMapper;
import io.github.smiskinext.meet.application.query.ListIssueMeetingsQuery;
import io.github.smiskinext.meet.application.result.ListIssueMeetingsResult;
import io.github.smiskinext.meet.application.result.ListMeetingsResult;
import io.github.smiskinext.meet.application.usecase.ListIssueMeetingsUseCase;
import io.github.smiskinext.meet.domain.ListMeetingsError;
import io.github.smiskinext.meet.domain.port.MeetingRepository;
import io.github.smiskinext.meet.domain.port.MeetingRepository.IssueMeetingPage;
import io.github.smiskinext.shared.domain.Result;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lists meetings linked to a specific Jira issue using offset pagination.
 *
 * <p>Delegates the query to {@link MeetingRepository#findSummariesByIssueId} and maps domain
 * summaries to result items via {@link MeetingSummaryMapper}. An unknown issue id simply yields an
 * empty page with total zero — it is never a failure.
 */
@Service
@Transactional(readOnly = true)
public class ListIssueMeetingsApplicationService implements ListIssueMeetingsUseCase {

    private final MeetingRepository meetingRepository;

    public ListIssueMeetingsApplicationService(MeetingRepository meetingRepository) {
        this.meetingRepository = meetingRepository;
    }

    @Override
    public Result<ListIssueMeetingsResult, ListMeetingsError> execute(
            ListIssueMeetingsQuery query) {
        IssueMeetingPage page = meetingRepository.findSummariesByIssueId(
                query.issueId(), query.offset(), query.pageSize());

        List<ListMeetingsResult.Item> items =
                page.page().items().stream().map(MeetingSummaryMapper::toItem).toList();

        return Result.success(new ListIssueMeetingsResult(
                items,
                page.total(),
                query.offset(),
                query.pageSize(),
                page.page().hasNext()));
    }
}
