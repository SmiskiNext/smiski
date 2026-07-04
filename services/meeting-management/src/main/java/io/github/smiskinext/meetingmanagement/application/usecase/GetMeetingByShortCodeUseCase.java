package io.github.smiskinext.meetingmanagement.application.usecase;

import io.github.smiskinext.meetingmanagement.application.query.GetMeetingByShortCodeQuery;
import io.github.smiskinext.meetingmanagement.application.response.MeetingResponse;
import io.github.smiskinext.meetingmanagement.application.response.MeetingSettingsResponse;
import io.github.smiskinext.meetingmanagement.domain.MeetingError;
import io.github.smiskinext.meetingmanagement.domain.model.Meeting;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.MeetingTitle;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.ShortCode;
import io.github.smiskinext.meetingmanagement.domain.port.MeetingRepository;
import io.github.phunguy65.zms.shared.domain.Result;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetMeetingByShortCodeUseCase {

    private final MeetingRepository meetingRepository;

    public GetMeetingByShortCodeUseCase(MeetingRepository meetingRepository) {
        this.meetingRepository = meetingRepository;
    }

    @Transactional(readOnly = true)
    public Result<MeetingResponse, MeetingError> execute(GetMeetingByShortCodeQuery query) {
        ShortCode shortCode = ShortCode.of(query.shortCode());
        return meetingRepository
                .findByShortCode(shortCode)
                .map(m -> Result.<MeetingResponse, MeetingError>success(toResponse(m)))
                .orElseGet(() -> Result.failure(
                        new MeetingError.MeetingNotFoundByShortCode(query.shortCode())));
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
