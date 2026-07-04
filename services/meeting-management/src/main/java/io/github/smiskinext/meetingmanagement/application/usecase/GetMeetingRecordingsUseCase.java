package io.github.smiskinext.meetingmanagement.application.usecase;

import io.github.smiskinext.meetingmanagement.application.RecordingResponseMapper;
import io.github.smiskinext.meetingmanagement.application.query.GetMeetingRecordingsQuery;
import io.github.smiskinext.meetingmanagement.application.response.RecordingResponse;
import io.github.smiskinext.meetingmanagement.domain.port.RecordingRepository;
import io.github.phunguy65.zms.shared.domain.CursorPageResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetMeetingRecordingsUseCase {

    private final RecordingRepository recordingRepository;
    private final RecordingResponseMapper recordingResponseMapper;

    public GetMeetingRecordingsUseCase(
            RecordingRepository recordingRepository,
            RecordingResponseMapper recordingResponseMapper) {
        this.recordingRepository = recordingRepository;
        this.recordingResponseMapper = recordingResponseMapper;
    }

    @Transactional(readOnly = true)
    public CursorPageResponse<RecordingResponse> execute(GetMeetingRecordingsQuery query) {
        var page = recordingRepository.findSummariesByMeetingId(
                query.meetingId(), query.cursor(), query.pageSize());
        var items =
                page.items().stream().map(recordingResponseMapper::toResponse).toList();
        return new CursorPageResponse<>(items, page.pageSize(), page.hasNext());
    }
}
