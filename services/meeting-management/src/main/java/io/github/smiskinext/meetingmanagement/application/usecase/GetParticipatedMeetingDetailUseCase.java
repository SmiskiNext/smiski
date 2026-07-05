package io.github.smiskinext.meetingmanagement.application.usecase;

import io.github.smiskinext.meetingmanagement.application.RecordingResponseMapper;
import io.github.smiskinext.meetingmanagement.application.helper.ParticipantAvatarResolver;
import io.github.smiskinext.meetingmanagement.application.query.GetParticipatedMeetingDetailQuery;
import io.github.smiskinext.meetingmanagement.application.response.InviteeResponse;
import io.github.smiskinext.meetingmanagement.application.response.MeetingDetailResponse;
import io.github.smiskinext.meetingmanagement.application.response.MeetingParticipantResponse;
import io.github.smiskinext.meetingmanagement.application.response.MeetingSettingsResponse;
import io.github.smiskinext.meetingmanagement.domain.MeetingError;
import io.github.smiskinext.meetingmanagement.domain.model.Meeting;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.MeetingTitle;
import io.github.smiskinext.meetingmanagement.domain.port.MeetingInviteeRepository;
import io.github.smiskinext.meetingmanagement.domain.port.MeetingRepository;
import io.github.smiskinext.meetingmanagement.domain.port.ParticipationLogRepository;
import io.github.smiskinext.meetingmanagement.domain.port.RecordingRepository;
import io.github.smiskinext.meetingmanagement.domain.projection.InviteeSummary;
import io.github.smiskinext.meetingmanagement.domain.projection.ParticipantSummary;
import io.github.smiskinext.meetingmanagement.domain.projection.RecordingSummary;
import io.github.smiskinext.shared.domain.Result;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetParticipatedMeetingDetailUseCase {

    private final MeetingRepository meetingRepository;
    private final ParticipationLogRepository participationLogRepository;
    private final RecordingRepository recordingRepository;
    private final MeetingInviteeRepository meetingInviteeRepository;
    private final RecordingResponseMapper recordingResponseMapper;
    private final ParticipantAvatarResolver avatarResolver;

    public GetParticipatedMeetingDetailUseCase(
            MeetingRepository meetingRepository,
            ParticipationLogRepository participationLogRepository,
            RecordingRepository recordingRepository,
            MeetingInviteeRepository meetingInviteeRepository,
            RecordingResponseMapper recordingResponseMapper,
            ParticipantAvatarResolver avatarResolver) {
        this.meetingRepository = meetingRepository;
        this.participationLogRepository = participationLogRepository;
        this.recordingRepository = recordingRepository;
        this.meetingInviteeRepository = meetingInviteeRepository;
        this.recordingResponseMapper = recordingResponseMapper;
        this.avatarResolver = avatarResolver;
    }

    @Transactional(readOnly = true)
    public Result<MeetingDetailResponse, MeetingError> execute(
            GetParticipatedMeetingDetailQuery query) {
        if (!query.requesterId().equals(query.userId())) {
            return Result.failure(new MeetingError.NotOwner(query.requesterId(), query.userId()));
        }
        if (!participationLogRepository.existsByMeetingIdAndUserId(
                query.meetingId(), query.userId())) {
            return Result.failure(
                    new MeetingError.NotParticipant(query.userId(), query.meetingId()));
        }

        return meetingRepository
                .findById(query.meetingId())
                .map(meeting -> toResponse(
                        meeting,
                        participationLogRepository.findDistinctParticipantSummariesByMeetingId(
                                meeting.getId().value()),
                        recordingRepository.findCompletedSummariesByMeetingId(
                                meeting.getId().value()),
                        meetingInviteeRepository.findSummariesByMeetingId(
                                meeting.getId().value())))
                .map(Result::<MeetingDetailResponse, MeetingError>success)
                .orElseGet(
                        () -> Result.failure(new MeetingError.MeetingNotFound(query.meetingId())));
    }

    private MeetingDetailResponse toResponse(
            Meeting meeting,
            List<ParticipantSummary> participants,
            List<RecordingSummary> recordings,
            List<InviteeSummary> invitees) {
        List<UUID> userIds = participants.stream()
                .map(ParticipantSummary::userId)
                .filter(Objects::nonNull)
                .toList();
        Map<UUID, String> avatars = avatarResolver.resolveAvatars(userIds);

        return new MeetingDetailResponse(
                meeting.getId().value(),
                meeting.getHostId().value(),
                meeting.getShortCode().value(),
                meeting.getTitle().map(MeetingTitle::value).orElse(null),
                meeting.getDescription().orElse(null),
                meeting.getStartTime().orElse(null),
                meeting.getEndTime().orElse(null),
                meeting.getType(),
                meeting.getStatus(),
                MeetingSettingsResponse.from(meeting.getSettings()),
                meeting.getCreatedAt(),
                participants.stream()
                        .map(s -> MeetingParticipantResponse.fromProjection(
                                s, s.userId() != null ? avatars.get(s.userId()) : null))
                        .toList(),
                recordings.stream().map(recordingResponseMapper::toResponse).toList(),
                invitees.stream().map(InviteeResponse::fromProjection).toList());
    }
}
