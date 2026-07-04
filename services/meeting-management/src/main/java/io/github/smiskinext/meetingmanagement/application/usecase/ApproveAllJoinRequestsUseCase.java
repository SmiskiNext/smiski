package io.github.smiskinext.meetingmanagement.application.usecase;

import io.github.smiskinext.meetingmanagement.application.command.ApproveAllJoinRequestsCommand;
import io.github.smiskinext.meetingmanagement.application.helper.PendingJoinRequestApprover;
import io.github.smiskinext.meetingmanagement.application.response.ApproveAllResponse;
import io.github.smiskinext.meetingmanagement.domain.MeetingError;
import io.github.smiskinext.meetingmanagement.domain.port.MeetingRepository;
import io.github.phunguy65.zms.shared.domain.Result;
import io.github.phunguy65.zms.shared.domain.valueobject.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApproveAllJoinRequestsUseCase {

    private static final Logger log = LoggerFactory.getLogger(ApproveAllJoinRequestsUseCase.class);

    private final MeetingRepository meetingRepository;
    private final PendingJoinRequestApprover pendingJoinRequestApprover;

    public ApproveAllJoinRequestsUseCase(
            MeetingRepository meetingRepository,
            PendingJoinRequestApprover pendingJoinRequestApprover) {
        this.meetingRepository = meetingRepository;
        this.pendingJoinRequestApprover = pendingJoinRequestApprover;
    }

    @Transactional
    public Result<ApproveAllResponse, MeetingError> execute(ApproveAllJoinRequestsCommand command) {
        var meetingOpt = meetingRepository.findByIdWithLock(command.meetingId());
        if (meetingOpt.isEmpty()) {
            return Result.failure(new MeetingError.MeetingNotFound(command.meetingId()));
        }
        var meeting = meetingOpt.get();

        if (!meeting.getHostId().equals(UserId.of(command.approvedBy()))) {
            return Result.failure(new MeetingError.NotAuthorized(
                    command.approvedBy(), meeting.getHostId().value()));
        }

        return switch (pendingJoinRequestApprover.approveAll(meeting, command.approvedBy())) {
            case Result.Success<Integer, MeetingError> s ->
                Result.success(new ApproveAllResponse(s.value()));
            case Result.Failure<Integer, MeetingError> f -> Result.failure(f.error());
        };
    }
}
