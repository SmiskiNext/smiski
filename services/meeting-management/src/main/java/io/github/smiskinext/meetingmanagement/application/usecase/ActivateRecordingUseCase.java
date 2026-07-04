package io.github.smiskinext.meetingmanagement.application.usecase;

import io.github.smiskinext.meetingmanagement.application.command.ActivateRecordingCommand;
import io.github.smiskinext.meetingmanagement.domain.MeetingError;
import io.github.smiskinext.meetingmanagement.domain.model.RecordingStatus;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.LiveKitEgressId;
import io.github.smiskinext.meetingmanagement.domain.port.RecordingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ActivateRecordingUseCase {

    private static final Logger log = LoggerFactory.getLogger(ActivateRecordingUseCase.class);

    private final RecordingRepository recordingRepository;

    public ActivateRecordingUseCase(RecordingRepository recordingRepository) {
        this.recordingRepository = recordingRepository;
    }

    @Transactional
    public void execute(ActivateRecordingCommand command) {
        var egressId = LiveKitEgressId.of(command.livekitEgressId());
        var recording = recordingRepository.findByEgressId(egressId);
        if (recording.isEmpty()) {
            log.debug(
                    "egress_started: no recording found for egress '{}'",
                    command.livekitEgressId());
            return;
        }

        var current = recording.get();
        if (current.getStatus() == RecordingStatus.RECORDING
                || current.getStatus() == RecordingStatus.COMPLETED
                || current.getStatus() == RecordingStatus.FAILED) {
            log.debug(
                    "egress_started: recording '{}' already in status '{}' for egress '{}'",
                    current.getId().value(),
                    current.getStatus(),
                    command.livekitEgressId());
            return;
        }

        var activation = current.activate(egressId);
        if (activation
                instanceof io.github.phunguy65.zms.shared.domain.Result.Failure<?, ?> failure) {
            log.warn(
                    "egress_started: failed to activate recording '{}' for egress '{}': {}",
                    current.getId().value(),
                    command.livekitEgressId(),
                    ((MeetingError)
                                    failure.error())
                            .message());
            return;
        }

        recordingRepository.save(current);
    }
}
