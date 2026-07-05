package io.github.smiskinext.meetingmanagement.application.usecase;

import io.github.smiskinext.meetingmanagement.application.command.FinalizeRecordingCommand;
import io.github.smiskinext.meetingmanagement.domain.MeetingError;
import io.github.smiskinext.meetingmanagement.domain.PublishableEvent;
import io.github.smiskinext.meetingmanagement.domain.model.RecordingStatus;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.LiveKitEgressId;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.LiveKitRoomName;
import io.github.smiskinext.meetingmanagement.domain.port.LiveKitPort;
import io.github.smiskinext.meetingmanagement.domain.port.RecordingRepository;
import io.github.smiskinext.shared.domain.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FinalizeRecordingUseCase {

    private static final Logger log = LoggerFactory.getLogger(FinalizeRecordingUseCase.class);

    private final RecordingRepository recordingRepository;
    private final LiveKitPort liveKitPort;
    private final ApplicationEventPublisher eventPublisher;

    public FinalizeRecordingUseCase(
            RecordingRepository recordingRepository,
            LiveKitPort liveKitPort,
            ApplicationEventPublisher eventPublisher) {
        this.recordingRepository = recordingRepository;
        this.liveKitPort = liveKitPort;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void execute(FinalizeRecordingCommand command) {
        var egressId = LiveKitEgressId.of(command.livekitEgressId());
        var recording = recordingRepository.findByEgressId(egressId);
        if (recording.isEmpty()) {
            log.debug(
                    "egress_ended: no recording found for egress '{}'", command.livekitEgressId());
            return;
        }

        var current = recording.get();
        if (current.getStatus() == RecordingStatus.COMPLETED
                || current.getStatus() == RecordingStatus.FAILED) {
            log.debug(
                    "egress_ended: recording '{}' already finalized as '{}' for egress '{}'",
                    current.getId().value(),
                    current.getStatus(),
                    command.livekitEgressId());
            return;
        }

        if (current.getStatus() == RecordingStatus.PENDING) {
            var activation = current.activate(egressId);
            if (activation instanceof Result.Failure<?, MeetingError>(MeetingError error)) {
                log.warn(
                        "egress_ended: failed to activate pending recording '{}' for egress '{}': {}",
                        current.getId().value(),
                        command.livekitEgressId(),
                        error.message());
                return;
            }
        }

        Result<Void, MeetingError> finalization = command.successful()
                ? current.complete(
                        command.fileUrl(),
                        command.storagePath(),
                        null,
                        command.durationSeconds(),
                        command.fileSizeBytes())
                : current.fail(command.errorMessage());

        if (finalization instanceof Result.Failure<?, MeetingError>(MeetingError error)) {
            log.warn(
                    "egress_ended: failed to finalize recording '{}' for egress '{}': {}",
                    current.getId().value(),
                    command.livekitEgressId(),
                    error.message());
            return;
        }

        var saved = recordingRepository.save(current);
        saved.getDomainEvents().stream()
                .filter(e -> e instanceof PublishableEvent)
                .map(e -> (PublishableEvent) e)
                .forEach(eventPublisher::publishEvent);
        saved.clearDomainEvents();

        LiveKitRoomName roomName = LiveKitRoomName.fromMeetingId(saved.getMeetingId());
        var metadataResult = liveKitPort.updateRoomMetadata(roomName, "{\"recording\":false}");
        if (metadataResult instanceof Result.Failure<?, MeetingError>(MeetingError error)) {
            log.warn(
                    "Failed to clear recording metadata for room '{}': {}",
                    roomName.value(),
                    error.message());
        }
    }
}
