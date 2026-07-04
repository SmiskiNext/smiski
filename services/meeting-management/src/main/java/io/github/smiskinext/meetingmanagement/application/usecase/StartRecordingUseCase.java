package io.github.smiskinext.meetingmanagement.application.usecase;

import io.github.smiskinext.meetingmanagement.application.command.StartRecordingCommand;
import io.github.smiskinext.meetingmanagement.application.response.RecordingResponse;
import io.github.smiskinext.meetingmanagement.domain.MeetingError;
import io.github.smiskinext.meetingmanagement.domain.PublishableEvent;
import io.github.smiskinext.meetingmanagement.domain.model.MeetingStatus;
import io.github.smiskinext.meetingmanagement.domain.model.Recording;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.LiveKitEgressId;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.LiveKitRoomName;
import io.github.smiskinext.meetingmanagement.domain.port.LiveKitPort;
import io.github.smiskinext.meetingmanagement.domain.port.MeetingRepository;
import io.github.smiskinext.meetingmanagement.domain.port.RecordingRepository;
import io.github.phunguy65.zms.shared.domain.Result;
import io.github.phunguy65.zms.shared.domain.valueobject.MeetingId;
import io.github.phunguy65.zms.shared.domain.valueobject.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StartRecordingUseCase {

    private static final Logger log = LoggerFactory.getLogger(StartRecordingUseCase.class);

    private final MeetingRepository meetingRepository;
    private final RecordingRepository recordingRepository;
    private final LiveKitPort liveKitPort;
    private final ApplicationEventPublisher eventPublisher;

    public StartRecordingUseCase(
            MeetingRepository meetingRepository,
            RecordingRepository recordingRepository,
            LiveKitPort liveKitPort,
            ApplicationEventPublisher eventPublisher) {
        this.meetingRepository = meetingRepository;
        this.recordingRepository = recordingRepository;
        this.liveKitPort = liveKitPort;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Result<RecordingResponse, MeetingError> execute(StartRecordingCommand command) {
        var meeting = meetingRepository.findById(command.meetingId());
        if (meeting.isEmpty()) {
            return Result.failure(new MeetingError.MeetingNotFound(command.meetingId()));
        }
        var m = meeting.get();

        if (!m.getHostId().equals(UserId.of(command.requesterId()))) {
            return Result.failure(new MeetingError.NotAuthorized(
                    command.requesterId(), m.getHostId().value()));
        }

        if (m.getStatus() != MeetingStatus.LIVE) {
            return Result.failure(
                    new MeetingError.InvalidStatusTransition(m.getStatus(), MeetingStatus.LIVE));
        }

        if (recordingRepository.findActiveByMeetingId(command.meetingId()).isPresent()) {
            return Result.failure(new MeetingError.RecordingAlreadyActive(command.meetingId()));
        }

        LiveKitRoomName roomName = LiveKitRoomName.fromMeetingId(m.getId());
        MeetingId meetingId = MeetingId.of(command.meetingId());
        Recording recording = Recording.startFor(meetingId, roomName);

        Recording saved = recordingRepository.save(recording);

        var startEgressResult = liveKitPort.startRoomCompositeEgress(meetingId, roomName);
        if (startEgressResult instanceof Result.Failure<?, MeetingError>(MeetingError error)) {
            failRecording(saved, error.message());
            return Result.failure(error);
        }
        if (!(startEgressResult instanceof Result.Success<LiveKitEgressId, MeetingError> success)) {
            throw new IllegalStateException("Expected LiveKit egress start to succeed or fail");
        }

        saved.assignEgressId(success.value());
        try {
            saved = recordingRepository.save(saved);
        } catch (RuntimeException e) {
            log.error(
                    "Failed to persist recording '{}' after starting LiveKit egress '{}'; stopping egress",
                    saved.getId().value(),
                    success.value().value(),
                    e);
            liveKitPort.stopEgress(success.value());
            throw e;
        }

        var metadataResult = liveKitPort.updateRoomMetadata(roomName, "{\"recording\":true}");
        if (metadataResult instanceof Result.Failure<?, MeetingError>(MeetingError error)) {
            log.warn(
                    "Failed to publish recording metadata for room '{}': {}",
                    roomName.value(),
                    error.message());
        }

        saved.getDomainEvents().stream()
                .filter(e -> e instanceof PublishableEvent)
                .map(e -> (PublishableEvent) e)
                .forEach(eventPublisher::publishEvent);
        saved.clearDomainEvents();

        return Result.success(toResponse(saved));
    }

    private void failRecording(Recording recording, String errorMessage) {
        var failResult = recording.fail(errorMessage);
        if (failResult instanceof Result.Success<?, ?>) {
            var saved = recordingRepository.save(recording);
            saved.getDomainEvents().stream()
                    .filter(e -> e instanceof PublishableEvent)
                    .map(e -> (PublishableEvent) e)
                    .forEach(eventPublisher::publishEvent);
            saved.clearDomainEvents();
        }
    }

    private RecordingResponse toResponse(Recording r) {
        return new RecordingResponse(
                r.getId().value(),
                r.getMeetingId().value(),
                null,
                null,
                r.getStatus(),
                r.getStartedAt(),
                r.getEndedAt().orElse(null),
                r.getDurationSeconds(),
                r.getFileSizeBytes(),
                r.getCreatedAt());
    }
}
