package io.github.smiskinext.meetingmanagement.application.usecase;

import io.github.smiskinext.meetingmanagement.application.command.KickParticipantCommand;
import io.github.smiskinext.meetingmanagement.domain.MeetingError;
import io.github.smiskinext.meetingmanagement.domain.event.ParticipantKickedEvent;
import io.github.smiskinext.meetingmanagement.domain.model.Meeting;
import io.github.smiskinext.meetingmanagement.domain.model.MeetingStatus;
import io.github.smiskinext.meetingmanagement.domain.model.ParticipationLog;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.LiveKitRoomName;
import io.github.smiskinext.meetingmanagement.domain.port.LiveKitPort;
import io.github.smiskinext.meetingmanagement.domain.port.MeetingRepository;
import io.github.smiskinext.meetingmanagement.domain.port.ParticipationLogRepository;
import io.github.phunguy65.zms.shared.domain.Result;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use case for the host to forcibly remove an active participant from a live meeting.
 *
 * <p>Supported targets:
 *
 * <ul>
 *   <li>Registered user — identified by {@code userId}, removes all active sessions for that user
 *       in the meeting
 *   <li>Guest — identified by {@code displayName}, removes all active guest sessions with that
 *       display name in the meeting
 * </ul>
 *
 * <p>The kick is a soft removal through LiveKit. The existing {@code participant_left} webhook
 * flow handles closing participation logs. No rejoin block is applied.
 */
@Service
public class KickParticipantUseCase {

    private final MeetingRepository meetingRepository;
    private final ParticipationLogRepository participationLogRepository;
    private final LiveKitPort liveKitPort;
    private final ApplicationEventPublisher applicationEventPublisher;

    public KickParticipantUseCase(
            MeetingRepository meetingRepository,
            ParticipationLogRepository participationLogRepository,
            LiveKitPort liveKitPort,
            ApplicationEventPublisher applicationEventPublisher) {
        this.meetingRepository = meetingRepository;
        this.participationLogRepository = participationLogRepository;
        this.liveKitPort = liveKitPort;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Transactional
    public Result<Void, MeetingError> execute(KickParticipantCommand command) {
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
            return Result.failure(new MeetingError.InvalidStatusTransition(
                    meeting.getStatus(), MeetingStatus.LIVE));
        }

        if (command.userId() != null && command.userId().equals(command.requesterId())) {
            return Result.failure(
                    new MeetingError.CanNotKickSelf(command.meetingId(), command.requesterId()));
        }

        if (command.userId() == null && command.displayName() == null) {
            return Result.failure(new MeetingError.InvalidKickTarget(
                    "either userId or displayName must be provided"));
        }
        if (command.userId() != null && command.displayName() != null) {
            return Result.failure(new MeetingError.InvalidKickTarget(
                    "provide either userId or displayName, not both"));
        }

        var activeSessions = command.userId() != null
                ? participationLogRepository.findActiveByMeetingIdAndUserId(
                        command.meetingId(), command.userId())
                : participationLogRepository.findActiveByMeetingIdAndDisplayName(
                        command.meetingId(), command.displayName());

        if (activeSessions.isEmpty()) {
            String identifier =
                    command.userId() != null ? command.userId().toString() : command.displayName();
            return Result.failure(
                    new MeetingError.UserNotInMeeting(command.meetingId(), identifier));
        }

        LiveKitRoomName roomName = LiveKitRoomName.fromMeetingId(meeting.getId());
        boolean allFailed = true;
        MeetingError.LiveKitUnavailable lastFailure = null;
        for (ParticipationLog session : activeSessions) {
            var removeResult = liveKitPort.removeParticipant(
                    roomName, session.getLivekitIdentity().value());
            if (removeResult instanceof Result.Success<?, ?> s) {
                allFailed = false;
            } else if (removeResult
                    instanceof Result.Failure<?, MeetingError>(MeetingError error)) {
                if (error instanceof MeetingError.LiveKitUnavailable unavailable) {
                    lastFailure = unavailable;
                } else {
                    allFailed = false;
                }
            }
        }

        if (allFailed && lastFailure != null) {
            return Result.failure(lastFailure);
        }

        var kickedEvent = new ParticipantKickedEvent(
                UUID.randomUUID(),
                command.meetingId(),
                command.requesterId(),
                command.userId(),
                command.displayName(),
                Instant.now());
        applicationEventPublisher.publishEvent(kickedEvent);

        return Result.success();
    }
}
