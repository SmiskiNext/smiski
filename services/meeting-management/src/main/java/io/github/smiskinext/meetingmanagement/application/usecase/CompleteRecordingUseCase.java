package io.github.smiskinext.meetingmanagement.application.usecase;

import io.github.smiskinext.meetingmanagement.application.command.CompleteRecordingCommand;
import io.github.smiskinext.meetingmanagement.domain.MeetingError;
import io.github.smiskinext.meetingmanagement.domain.PublishableEvent;
import io.github.smiskinext.meetingmanagement.domain.port.RecordingRepository;
import io.github.phunguy65.zms.shared.domain.Result;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompleteRecordingUseCase {

    private final RecordingRepository recordingRepository;
    private final ApplicationEventPublisher eventPublisher;

    public CompleteRecordingUseCase(
            RecordingRepository recordingRepository, ApplicationEventPublisher eventPublisher) {
        this.recordingRepository = recordingRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Result<Void, MeetingError> execute(CompleteRecordingCommand command) {
        var recording = recordingRepository.findById(command.recordingId());
        if (recording.isEmpty()) {
            return Result.failure(new MeetingError.RecordingNotFound(command.recordingId()));
        }
        var r = recording.get();

        var result = r.complete(
                command.fileUrl(),
                command.storagePath(),
                command.thumbnailUrl(),
                command.durationSeconds(),
                command.fileSizeBytes());
        if (result instanceof Result.Failure<?, MeetingError>(MeetingError error)) {
            return Result.failure(error);
        }

        var saved = recordingRepository.save(r);
        saved.getDomainEvents().stream()
                .filter(e -> e instanceof PublishableEvent)
                .map(e -> (PublishableEvent) e)
                .forEach(eventPublisher::publishEvent);
        saved.clearDomainEvents();

        return Result.success();
    }
}
