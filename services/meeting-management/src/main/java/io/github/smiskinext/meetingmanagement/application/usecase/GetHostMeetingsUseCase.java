package io.github.smiskinext.meetingmanagement.application.usecase;

import io.github.smiskinext.meetingmanagement.application.query.GetHostMeetingsQuery;
import io.github.smiskinext.meetingmanagement.application.response.MeetingResponse;
import io.github.smiskinext.meetingmanagement.application.response.MeetingSettingsResponse;
import io.github.smiskinext.meetingmanagement.domain.port.MeetingRepository;
import io.github.smiskinext.meetingmanagement.domain.projection.MeetingSummary;
import io.github.smiskinext.shared.domain.CursorPageResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetHostMeetingsUseCase {

    private final MeetingRepository meetingRepository;

    public GetHostMeetingsUseCase(MeetingRepository meetingRepository) {
        this.meetingRepository = meetingRepository;
    }

    @Transactional(readOnly = true)
    public CursorPageResponse<MeetingResponse> execute(GetHostMeetingsQuery query) {
        var page = meetingRepository.findSummariesByHostId(
                query.hostId(), query.cursor(), query.pageSize());
        var items = page.items().stream().map(this::toResponse).toList();
        return new CursorPageResponse<>(items, page.pageSize(), page.hasNext());
    }

    private MeetingResponse toResponse(MeetingSummary meeting) {
        return new MeetingResponse(
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
                meeting.createdAt());
    }
}
