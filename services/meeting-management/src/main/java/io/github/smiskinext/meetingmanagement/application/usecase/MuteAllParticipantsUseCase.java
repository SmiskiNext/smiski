package io.github.smiskinext.meetingmanagement.application.usecase;

import io.github.smiskinext.meetingmanagement.application.command.MuteAllParticipantsCommand;
import io.github.smiskinext.meetingmanagement.domain.MeetingError;
import io.github.smiskinext.meetingmanagement.domain.model.Meeting;
import io.github.smiskinext.meetingmanagement.domain.model.MeetingStatus;
import io.github.smiskinext.meetingmanagement.domain.model.ParticipantRole;
import io.github.smiskinext.meetingmanagement.domain.model.ParticipationLog;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.LiveKitRoomName;
import io.github.smiskinext.meetingmanagement.domain.port.LiveKitPort;
import io.github.smiskinext.meetingmanagement.domain.port.MeetingRepository;
import io.github.smiskinext.meetingmanagement.domain.port.ParticipationLogRepository;
import io.github.smiskinext.shared.domain.Result;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Use case for the host to mute all active participant microphones in a meeting.
 *
 * <p>Only sessions with {@link ParticipantRole#PARTICIPANT} are included.
 * HOST and GUEST sessions are excluded. Empty participant lists return success
 * without calling LiveKit. The operation uses best-effort semantics.
 */
@Service
public class MuteAllParticipantsUseCase {

    private final MeetingRepository meetingRepository;
    private final ParticipationLogRepository participationLogRepository;
    private final LiveKitPort liveKitPort;

    public MuteAllParticipantsUseCase(
            MeetingRepository meetingRepository,
            ParticipationLogRepository participationLogRepository,
            LiveKitPort liveKitPort) {
        this.meetingRepository = meetingRepository;
        this.participationLogRepository = participationLogRepository;
        this.liveKitPort = liveKitPort;
    }

    public Result<Void, MeetingError> execute(MuteAllParticipantsCommand command) {
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

        List<ParticipationLog> activeSessions =
                participationLogRepository.findActiveByMeetingId(command.meetingId());

        List<String> participantIdentities = activeSessions.stream()
                .filter(session -> session.getRole() == ParticipantRole.PARTICIPANT)
                .map(session -> session.getLivekitIdentity().value())
                .toList();

        if (participantIdentities.isEmpty()) {
            return Result.success();
        }

        LiveKitRoomName roomName = LiveKitRoomName.fromMeetingId(meeting.getId());
        return liveKitPort.muteAllParticipantMicTracks(roomName, participantIdentities);
    }
}
