package io.github.smiskinext.meetingmanagement.application.usecase;

import io.github.smiskinext.meetingmanagement.application.helper.ParticipantAvatarResolver;
import io.github.smiskinext.meetingmanagement.application.query.GetJoinRequestsQuery;
import io.github.smiskinext.meetingmanagement.application.response.JoinRequestResponse;
import io.github.smiskinext.meetingmanagement.domain.MeetingError;
import io.github.smiskinext.meetingmanagement.domain.port.JoinRequestRepository;
import io.github.smiskinext.meetingmanagement.domain.port.MeetingRepository;
import io.github.smiskinext.meetingmanagement.domain.projection.JoinRequestSummary;
import io.github.phunguy65.zms.shared.domain.OffsetPageResponse;
import io.github.phunguy65.zms.shared.domain.Result;
import io.github.phunguy65.zms.shared.domain.valueobject.UserId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetJoinRequestsUseCase {

    private final MeetingRepository meetingRepository;
    private final JoinRequestRepository joinRequestRepository;
    private final ParticipantAvatarResolver avatarResolver;

    public GetJoinRequestsUseCase(
            MeetingRepository meetingRepository,
            JoinRequestRepository joinRequestRepository,
            ParticipantAvatarResolver avatarResolver) {
        this.meetingRepository = meetingRepository;
        this.joinRequestRepository = joinRequestRepository;
        this.avatarResolver = avatarResolver;
    }

    @Transactional(readOnly = true)
    public Result<OffsetPageResponse<JoinRequestResponse>, MeetingError> execute(
            GetJoinRequestsQuery query) {
        var meetingOpt = meetingRepository.findById(query.meetingId());
        if (meetingOpt.isEmpty()) {
            return Result.failure(new MeetingError.MeetingNotFound(query.meetingId()));
        }
        var meeting = meetingOpt.get();

        if (!meeting.getHostId().equals(UserId.of(query.requesterId()))) {
            return Result.failure(new MeetingError.NotAuthorized(
                    query.requesterId(), meeting.getHostId().value()));
        }

        var page = joinRequestRepository.findPendingSummariesByMeetingId(
                query.meetingId(), query.offset(), query.pageSize());

        List<UUID> userIds = page.items().stream()
                .map(JoinRequestSummary::userId)
                .filter(Objects::nonNull)
                .toList();
        Map<UUID, String> avatars = avatarResolver.resolveAvatars(userIds);

        List<JoinRequestResponse> responses =
                page.items().stream().map(r -> toResponse(r, avatars)).toList();

        return Result.success(
                OffsetPageResponse.of(responses, page.pageSize(), page.offset(), page.hasNext()));
    }

    private JoinRequestResponse toResponse(JoinRequestSummary request, Map<UUID, String> avatars) {
        return new JoinRequestResponse(
                request.id(),
                request.meetingId(),
                request.userId(),
                request.displayName(),
                request.userId() != null ? avatars.get(request.userId()) : null,
                request.status(),
                request.requestedAt(),
                request.expiresAt());
    }
}
