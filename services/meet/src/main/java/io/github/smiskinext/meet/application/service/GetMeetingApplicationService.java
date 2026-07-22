package io.github.smiskinext.meet.application.service;

import io.github.smiskinext.meet.application.mapper.GetMeetingMapper;
import io.github.smiskinext.meet.application.query.GetMeetingQuery;
import io.github.smiskinext.meet.application.result.GetMeetingResult;
import io.github.smiskinext.meet.application.usecase.GetMeetingUseCase;
import io.github.smiskinext.meet.domain.MeetingError;
import io.github.smiskinext.meet.domain.model.Meeting;
import io.github.smiskinext.meet.domain.port.MeetingInviteeRepository;
import io.github.smiskinext.meet.domain.port.MeetingRepository;
import io.github.smiskinext.meet.domain.port.ParticipationLogRepository;
import io.github.smiskinext.meet.domain.projection.InviteeSummary;
import io.github.smiskinext.meet.domain.projection.ParticipantSummary;
import io.github.smiskinext.shared.domain.Result;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Retrieves a single tenant-scoped meeting together with its active invitees and its distinct
 * joined participants.
 *
 * <p>Unknown, soft-deleted, and other-tenant meetings are indistinguishable to the caller: each
 * yields {@link MeetingError.MeetingNotFound}. Tenant isolation is enforced by the repository's
 * {@code @TenantId} filter; any authenticated tenant member may read the meeting.
 */
@Service
@Transactional(readOnly = true)
public class GetMeetingApplicationService implements GetMeetingUseCase {

    private final MeetingRepository meetingRepository;
    private final MeetingInviteeRepository meetingInviteeRepository;
    private final ParticipationLogRepository participationLogRepository;

    public GetMeetingApplicationService(
            MeetingRepository meetingRepository,
            MeetingInviteeRepository meetingInviteeRepository,
            ParticipationLogRepository participationLogRepository) {
        this.meetingRepository = meetingRepository;
        this.meetingInviteeRepository = meetingInviteeRepository;
        this.participationLogRepository = participationLogRepository;
    }

    @Override
    public Result<GetMeetingResult, MeetingError> execute(GetMeetingQuery query) {
        Meeting meeting = meetingRepository.findById(query.meetingId()).orElse(null);
        if (meeting == null || meeting.getDeletedAt().isPresent()) {
            return Result.failure(new MeetingError.MeetingNotFound(query.meetingId()));
        }

        List<InviteeSummary> invitees =
                meetingInviteeRepository.findSummariesByMeetingId(query.meetingId());
        List<ParticipantSummary> participants =
                participationLogRepository.findDistinctParticipantSummariesByMeetingId(
                        query.meetingId());

        return Result.success(GetMeetingMapper.toResult(meeting, invitees, participants));
    }
}
