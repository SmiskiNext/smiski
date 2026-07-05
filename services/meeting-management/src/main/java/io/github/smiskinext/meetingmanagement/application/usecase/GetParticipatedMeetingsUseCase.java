package io.github.smiskinext.meetingmanagement.application.usecase;

import io.github.smiskinext.meetingmanagement.application.query.GetParticipatedMeetingsQuery;
import io.github.smiskinext.meetingmanagement.application.response.MeetingResponse;
import io.github.smiskinext.meetingmanagement.application.response.MeetingSettingsResponse;
import io.github.smiskinext.meetingmanagement.application.response.ParticipatedMeetingListItemResponse;
import io.github.smiskinext.meetingmanagement.application.response.ParticipatedMeetingPageResponse;
import io.github.smiskinext.meetingmanagement.domain.MeetingError;
import io.github.smiskinext.meetingmanagement.domain.port.MeetingRepository;
import io.github.smiskinext.meetingmanagement.domain.projection.ParticipatedMeetingSummary;
import io.github.smiskinext.shared.domain.Result;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetParticipatedMeetingsUseCase {

    private final MeetingRepository meetingRepository;

    public GetParticipatedMeetingsUseCase(MeetingRepository meetingRepository) {
        this.meetingRepository = meetingRepository;
    }

    @Transactional(readOnly = true)
    public Result<ParticipatedMeetingPageResponse, MeetingError> execute(
            GetParticipatedMeetingsQuery query) {
        if (!query.requesterId().equals(query.userId())) {
            return Result.failure(new MeetingError.NotOwner(query.requesterId(), query.userId()));
        }

        var page = meetingRepository.findParticipatedSummariesByUserId(
                query.userId(), query.statuses(), query.cursor(), query.pageSize());
        var items = page.items().stream().map(this::toResponse).toList();
        return Result.success(
                new ParticipatedMeetingPageResponse(items, page.pageSize(), page.hasNext()));
    }

    private ParticipatedMeetingListItemResponse toResponse(ParticipatedMeetingSummary meeting) {
        return new ParticipatedMeetingListItemResponse(
                meeting.lastJoinedAt(),
                new MeetingResponse(
                        meeting.id(),
                        meeting.hostId(),
                        meeting.shortCode(),
                        meeting.title(),
                        meeting.description(),
                        meeting.startTime(),
                        meeting.endTime(),
                        meeting.type(),
                        meeting.status(),
                        new MeetingSettingsResponse(
                                meeting.settings().admissionPolicy(),
                                meeting.settings().allowGuest(),
                                meeting.settings().maxParticipants(),
                                meeting.settings().allowScreenShare(),
                                meeting.settings().chatEnabled(),
                                meeting.settings().allowMicrophone(),
                                meeting.settings().allowVideo(),
                                meeting.settings().passwordProtected(),
                                0,
                                false),
                        meeting.createdAt()));
    }
}
