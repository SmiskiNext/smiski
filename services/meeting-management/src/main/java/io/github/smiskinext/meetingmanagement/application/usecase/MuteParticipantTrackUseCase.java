package io.github.smiskinext.meetingmanagement.application.usecase;

import io.github.smiskinext.meetingmanagement.application.command.MuteParticipantTrackCommand;
import io.github.smiskinext.meetingmanagement.domain.MeetingError;
import io.github.smiskinext.meetingmanagement.domain.model.Meeting;
import io.github.smiskinext.meetingmanagement.domain.model.MeetingStatus;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.LiveKitRoomName;
import io.github.smiskinext.meetingmanagement.domain.port.LiveKitPort;
import io.github.smiskinext.meetingmanagement.domain.port.MeetingRepository;
import io.github.phunguy65.zms.shared.domain.Result;
import org.springframework.stereotype.Service;

/**
 * Use case for the host to mute a specific participant's track (microphone or camera).
 *
 * <p>The host cannot mute their own tracks via this endpoint (use local device controls instead).
 * The target identity is a LiveKit identity string ({@code "userId:deviceId"}).
 * Self-mute is detected by checking if the identity starts with the host's user ID.
 */
@Service
public class MuteParticipantTrackUseCase {

    private final MeetingRepository meetingRepository;
    private final LiveKitPort liveKitPort;

    public MuteParticipantTrackUseCase(
            MeetingRepository meetingRepository, LiveKitPort liveKitPort) {
        this.meetingRepository = meetingRepository;
        this.liveKitPort = liveKitPort;
    }

    public Result<Void, MeetingError> execute(MuteParticipantTrackCommand command) {
        var meetingOpt = meetingRepository.findById(command.meetingId());
        if (meetingOpt.isEmpty()) {
            return Result.failure(new MeetingError.MeetingNotFound(command.meetingId()));
        }
        Meeting meeting = meetingOpt.get();

        if (!meeting.getHostId().value().equals(command.requesterId())) {
            return Result.failure(new MeetingError.NotAuthorized(
                    command.requesterId(), meeting.getHostId().value()));
        }

        if (meeting.getStatus() != MeetingStatus.LIVE) {
            return Result.failure(
                    new MeetingError.MeetingNotLive(meeting.getId().value()));
        }

        if (isOwnIdentity(command.requesterId().toString(), command.targetIdentity())) {
            return Result.failure(new MeetingError.CanNotMuteSelf());
        }

        LiveKitRoomName roomName = LiveKitRoomName.fromMeetingId(meeting.getId());
        return liveKitPort.muteParticipantTrack(
                roomName, command.targetIdentity(), command.source());
    }

    private boolean isOwnIdentity(String requesterId, String targetIdentity) {
        return targetIdentity.startsWith(requesterId + ":");
    }
}
