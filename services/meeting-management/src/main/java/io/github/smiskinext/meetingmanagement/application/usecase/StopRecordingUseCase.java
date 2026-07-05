package io.github.smiskinext.meetingmanagement.application.usecase;

import io.github.smiskinext.meetingmanagement.application.command.StopRecordingCommand;
import io.github.smiskinext.meetingmanagement.domain.MeetingError;
import io.github.smiskinext.meetingmanagement.domain.model.MeetingStatus;
import io.github.smiskinext.meetingmanagement.domain.port.LiveKitPort;
import io.github.smiskinext.meetingmanagement.domain.port.MeetingRepository;
import io.github.smiskinext.meetingmanagement.domain.port.RecordingRepository;
import io.github.smiskinext.shared.domain.Result;
import io.github.smiskinext.shared.domain.valueobject.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stops an active recording by stopping its LiveKit egress session.
 *
 * <p>This use case does NOT transition the {@code Recording} aggregate state directly.
 * The state transition (RECORDING → COMPLETED or FAILED) is handled by the
 * {@code egress_ended} webhook handler once LiveKit confirms the egress has stopped.
 */
@Service
public class StopRecordingUseCase {

    private final MeetingRepository meetingRepository;
    private final RecordingRepository recordingRepository;
    private final LiveKitPort liveKitPort;

    public StopRecordingUseCase(
            MeetingRepository meetingRepository,
            RecordingRepository recordingRepository,
            LiveKitPort liveKitPort) {
        this.meetingRepository = meetingRepository;
        this.recordingRepository = recordingRepository;
        this.liveKitPort = liveKitPort;
    }

    @Transactional
    public Result<Void, MeetingError> execute(StopRecordingCommand command) {
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

        var recording = recordingRepository.findActiveByMeetingId(command.meetingId());
        if (recording.isEmpty()) {
            return Result.failure(new MeetingError.NoActiveRecording(command.meetingId()));
        }

        var egressId = recording.get().getLivekitEgressId();
        if (egressId.isEmpty()) {
            return Result.failure(new MeetingError.LiveKitUnavailable(
                    "Active recording is missing a LiveKit egress id"));
        }

        var stopResult = liveKitPort.stopEgress(egressId.get());
        if (stopResult instanceof Result.Failure<?, MeetingError>(MeetingError error)) {
            if (isAlreadyStopped(error)) {
                return Result.success();
            }
            return Result.failure(error);
        }

        return Result.success();
    }

    private boolean isAlreadyStopped(MeetingError error) {
        return error instanceof MeetingError.LiveKitUnavailable unavailable
                && unavailable.detail().startsWith("HTTP 404");
    }
}
