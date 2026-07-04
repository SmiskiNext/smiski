package io.github.smiskinext.meetingmanagement.application.usecase;

import io.github.smiskinext.meetingmanagement.application.RecordingResponseMapper;
import io.github.smiskinext.meetingmanagement.application.query.GetRecordingQuery;
import io.github.smiskinext.meetingmanagement.application.response.RecordingResponse;
import io.github.smiskinext.meetingmanagement.domain.MeetingError;
import io.github.smiskinext.meetingmanagement.domain.port.RecordingRepository;
import io.github.phunguy65.zms.shared.domain.Result;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetRecordingUseCase {

    private final RecordingRepository recordingRepository;
    private final RecordingResponseMapper recordingResponseMapper;

    public GetRecordingUseCase(
            RecordingRepository recordingRepository,
            RecordingResponseMapper recordingResponseMapper) {
        this.recordingRepository = recordingRepository;
        this.recordingResponseMapper = recordingResponseMapper;
    }

    @Transactional(readOnly = true)
    public Result<RecordingResponse, MeetingError> execute(GetRecordingQuery query) {
        return recordingRepository
                .findById(query.recordingId())
                .map(r -> Result.<RecordingResponse, MeetingError>success(
                        recordingResponseMapper.toResponse(r)))
                .orElseGet(() ->
                        Result.failure(new MeetingError.RecordingNotFound(query.recordingId())));
    }
}
