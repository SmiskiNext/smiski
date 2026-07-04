package io.github.smiskinext.meetingmanagement.application.usecase;

import io.github.smiskinext.meetingmanagement.application.query.GetMeetingQuery;
import io.github.smiskinext.meetingmanagement.application.response.MeetingResponse;
import io.github.smiskinext.meetingmanagement.application.response.MeetingSettingsResponse;
import io.github.smiskinext.meetingmanagement.domain.MeetingError;
import io.github.smiskinext.meetingmanagement.domain.model.Meeting;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.MeetingTitle;
import io.github.smiskinext.meetingmanagement.domain.port.MeetingRepository;
import io.github.phunguy65.zms.shared.domain.Result;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetMeetingUseCase {

    private final MeetingRepository meetingRepository;

    public GetMeetingUseCase(MeetingRepository meetingRepository) {
        this.meetingRepository = meetingRepository;
    }

    @Transactional(readOnly = true)
    public Result<MeetingResponse, MeetingError> execute(GetMeetingQuery query) {
        return meetingRepository
                .findById(query.meetingId())
                .map(m -> Result.<MeetingResponse, MeetingError>success(toResponse(m)))
                .orElseGet(
                        () -> Result.failure(new MeetingError.MeetingNotFound(query.meetingId())));
    }

    private MeetingResponse toResponse(Meeting m) {
        return new MeetingResponse(
                m.getId().value(),
                m.getHostId().value(),
                m.getShortCode().value(),
                m.getTitle().map(MeetingTitle::value).orElse(null),
                m.getDescription().orElse(null),
                m.getStartTime().orElse(null),
                m.getEndTime().orElse(null),
                m.getType(),
                m.getStatus(),
                MeetingSettingsResponse.from(m.getSettings()),
                m.getCreatedAt());
    }
}
