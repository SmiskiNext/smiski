package io.github.smiskinext.meetingmanagement.application.usecase;

import io.github.smiskinext.meetingmanagement.application.helper.ParticipantAvatarResolver;
import io.github.smiskinext.meetingmanagement.application.query.GetParticipantsQuery;
import io.github.smiskinext.meetingmanagement.application.response.ParticipantListItemResponse;
import io.github.smiskinext.meetingmanagement.domain.MeetingError;
import io.github.smiskinext.meetingmanagement.domain.port.MeetingRepository;
import io.github.smiskinext.meetingmanagement.domain.port.ParticipationLogRepository;
import io.github.smiskinext.meetingmanagement.domain.projection.ParticipantSummary;
import io.github.phunguy65.zms.shared.domain.Result;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetParticipantsUseCase {

    private final MeetingRepository meetingRepository;
    private final ParticipationLogRepository participationLogRepository;
    private final ParticipantAvatarResolver avatarResolver;

    public GetParticipantsUseCase(
            MeetingRepository meetingRepository,
            ParticipationLogRepository participationLogRepository,
            ParticipantAvatarResolver avatarResolver) {
        this.meetingRepository = meetingRepository;
        this.participationLogRepository = participationLogRepository;
        this.avatarResolver = avatarResolver;
    }

    @Transactional(readOnly = true)
    public Result<List<ParticipantListItemResponse>, MeetingError> execute(
            GetParticipantsQuery query) {
        if (meetingRepository.findById(query.meetingId()).isEmpty()) {
            return Result.failure(new MeetingError.MeetingNotFound(query.meetingId()));
        }

        var summaries =
                participationLogRepository.findParticipantSummariesByMeetingId(query.meetingId());

        List<UUID> userIds = summaries.stream()
                .map(ParticipantSummary::userId)
                .filter(Objects::nonNull)
                .toList();
        Map<UUID, String> avatars = avatarResolver.resolveAvatars(userIds);

        return Result.success(summaries.stream()
                .map(s -> ParticipantListItemResponse.fromProjection(
                        s, s.userId() != null ? avatars.get(s.userId()) : null))
                .toList());
    }
}
