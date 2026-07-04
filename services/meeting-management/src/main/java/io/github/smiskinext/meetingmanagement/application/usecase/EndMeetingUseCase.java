package io.github.smiskinext.meetingmanagement.application.usecase;

import io.github.smiskinext.meetingmanagement.application.command.EndMeetingCommand;
import io.github.smiskinext.meetingmanagement.application.helper.ParticipationLogCloser;
import io.github.smiskinext.meetingmanagement.domain.MeetingError;
import io.github.smiskinext.meetingmanagement.domain.PublishableEvent;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.LiveKitRoomName;
import io.github.smiskinext.meetingmanagement.domain.port.LiveKitPort;
import io.github.smiskinext.meetingmanagement.domain.port.MeetingRepository;
import io.github.smiskinext.meetingmanagement.domain.port.RecordingRepository;
import io.github.phunguy65.zms.shared.domain.Result;
import io.github.phunguy65.zms.shared.domain.valueobject.UserId;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EndMeetingUseCase {

    private final MeetingRepository meetingRepository;
    private final RecordingRepository recordingRepository;
    private final LiveKitPort liveKitPort;
    private final ApplicationEventPublisher eventPublisher;
    private final ParticipationLogCloser participationLogCloser;

    public EndMeetingUseCase(
            MeetingRepository meetingRepository,
            RecordingRepository recordingRepository,
            LiveKitPort liveKitPort,
            ApplicationEventPublisher eventPublisher,
            ParticipationLogCloser participationLogCloser) {
        this.meetingRepository = meetingRepository;
        this.recordingRepository = recordingRepository;
        this.liveKitPort = liveKitPort;
        this.eventPublisher = eventPublisher;
        this.participationLogCloser = participationLogCloser;
    }

    @Transactional
    public Result<Void, MeetingError> execute(EndMeetingCommand command) {
        var meeting = meetingRepository.findById(command.meetingId());
        if (meeting.isEmpty()) {
            return Result.failure(new MeetingError.MeetingNotFound(command.meetingId()));
        }
        var m = meeting.get();

        if (!m.getHostId().equals(UserId.of(command.requesterId()))) {
            return Result.failure(new MeetingError.NotAuthorized(
                    command.requesterId(), m.getHostId().value()));
        }

        var endResult = m.end();
        if (endResult instanceof Result.Failure<?, MeetingError>(MeetingError error)) {
            return Result.failure(error);
        }

        var activeRecording = recordingRepository.findActiveByMeetingId(command.meetingId());
        if (activeRecording.isPresent()) {
            var egressId = activeRecording.get().getLivekitEgressId();
            if (egressId.isEmpty()) {
                return Result.failure(new MeetingError.LiveKitUnavailable(
                        "Active recording is missing a LiveKit egress id"));
            }

            var stopResult = liveKitPort.stopEgress(egressId.get());
            if (stopResult instanceof Result.Failure<?, MeetingError>(MeetingError error)) {
                return Result.failure(error);
            }
        }

        var deleteResult = liveKitPort.deleteRoom(LiveKitRoomName.fromMeetingId(m.getId()));
        if (deleteResult instanceof Result.Failure<?, MeetingError>(MeetingError error)) {
            return Result.failure(error);
        }

        // Belt-and-suspenders: close any active participation logs now.
        // LiveKit will also fire participant_left + room_finished webhooks after deleteRoom(),
        // but those are async and may be delayed or lost. Closing here ensures countActive
        // returns 0 immediately and orphaned rows don't linger.
        participationLogCloser.closeAllActive(m.getId().value());

        var saved = meetingRepository.save(m);
        saved.getDomainEvents().stream()
                .filter(e -> e instanceof PublishableEvent)
                .map(e -> (PublishableEvent) e)
                .forEach(eventPublisher::publishEvent);
        saved.clearDomainEvents();

        return Result.success();
    }
}
