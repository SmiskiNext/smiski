package io.github.smiskinext.meet.application.service;

import io.github.smiskinext.meet.application.query.ListPendingJoinRequestsQuery;
import io.github.smiskinext.meet.application.result.ListPendingJoinRequestsResult;
import io.github.smiskinext.meet.application.usecase.ListPendingJoinRequestsUseCase;
import io.github.smiskinext.meet.domain.MeetingError;
import io.github.smiskinext.meet.domain.port.JoinRequestRepository;
import io.github.smiskinext.meet.domain.port.MeetingRepository;
import io.github.smiskinext.meet.domain.projection.MeetingDetail;
import io.github.smiskinext.shared.domain.Result;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Retrieves the paginated list of PENDING join requests for a meeting, restricted to the host.
 *
 * <p>The meeting is loaded via a non-locking read path ({@link MeetingRepository#findDetailById})
 * because no aggregate mutation takes place. Only the host may access the pending queue; any other
 * caller receives {@link MeetingError.NotOwner}.
 */
@Service
@Transactional(readOnly = true)
public class ListPendingJoinRequestsApplicationService implements ListPendingJoinRequestsUseCase {

    private final MeetingRepository meetingRepository;
    private final JoinRequestRepository joinRequestRepository;

    public ListPendingJoinRequestsApplicationService(
            MeetingRepository meetingRepository, JoinRequestRepository joinRequestRepository) {
        this.meetingRepository = meetingRepository;
        this.joinRequestRepository = joinRequestRepository;
    }

    @Override
    public Result<ListPendingJoinRequestsResult, MeetingError> execute(
            ListPendingJoinRequestsQuery query) {
        MeetingDetail meeting =
                meetingRepository.findDetailById(query.meetingId()).orElse(null);
        if (meeting == null) {
            return Result.failure(new MeetingError.MeetingNotFound(query.meetingId()));
        }

        if (!meeting.hostId().equals(query.accountId())) {
            return Result.failure(new MeetingError.NotOwner(query.accountId(), meeting.hostId()));
        }

        return Result.success(new ListPendingJoinRequestsResult(
                joinRequestRepository.findPendingSummariesByMeetingId(
                        query.meetingId(), query.offset(), query.pageSize()),
                joinRequestRepository.countPendingByMeetingId(query.meetingId())));
    }
}
