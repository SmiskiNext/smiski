package io.github.smiskinext.meetingmanagement.application.usecase;

import io.github.smiskinext.meetingmanagement.application.command.ApproveJoinRequestCommand;
import io.github.smiskinext.meetingmanagement.application.helper.ParticipantAvatarResolver;
import io.github.smiskinext.meetingmanagement.domain.MeetingError;
import io.github.smiskinext.meetingmanagement.domain.event.JoinRequestApprovedEvent;
import io.github.smiskinext.meetingmanagement.domain.model.JoinRequestResult;
import io.github.smiskinext.meetingmanagement.domain.model.JoinRequestStatus;
import io.github.smiskinext.meetingmanagement.domain.model.ParticipantRole;
import io.github.smiskinext.meetingmanagement.domain.model.ParticipationLog;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.LiveKitIdentity;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.LiveKitRoomName;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.LiveKitTokenRequest;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.ParticipantAttributes;
import io.github.smiskinext.meetingmanagement.domain.port.JoinRequestRepository;
import io.github.smiskinext.meetingmanagement.domain.port.JoinRequestResultStore;
import io.github.smiskinext.meetingmanagement.domain.port.LiveKitPort;
import io.github.smiskinext.meetingmanagement.domain.port.MeetingRepository;
import io.github.smiskinext.meetingmanagement.domain.port.ParticipationLogRepository;
import io.github.phunguy65.zms.shared.domain.Result;
import io.github.phunguy65.zms.shared.domain.valueobject.UserId;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApproveJoinRequestUseCase {

    private static final Logger log = LoggerFactory.getLogger(ApproveJoinRequestUseCase.class);

    private final MeetingRepository meetingRepository;
    private final JoinRequestRepository joinRequestRepository;
    private final ParticipationLogRepository participationLogRepository;
    private final ParticipantAvatarResolver participantAvatarResolver;
    private final LiveKitPort liveKitPort;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final JoinRequestResultStore joinRequestResultStore;

    public ApproveJoinRequestUseCase(
            MeetingRepository meetingRepository,
            JoinRequestRepository joinRequestRepository,
            ParticipationLogRepository participationLogRepository,
            ParticipantAvatarResolver participantAvatarResolver,
            LiveKitPort liveKitPort,
            ApplicationEventPublisher applicationEventPublisher,
            JoinRequestResultStore joinRequestResultStore) {
        this.meetingRepository = meetingRepository;
        this.joinRequestRepository = joinRequestRepository;
        this.participationLogRepository = participationLogRepository;
        this.participantAvatarResolver = participantAvatarResolver;
        this.liveKitPort = liveKitPort;
        this.applicationEventPublisher = applicationEventPublisher;
        this.joinRequestResultStore = joinRequestResultStore;
    }

    @Transactional
    public Result<String, MeetingError> execute(ApproveJoinRequestCommand command) {
        var meetingOpt = meetingRepository.findByIdWithLock(command.meetingId());
        if (meetingOpt.isEmpty()) {
            return Result.failure(new MeetingError.MeetingNotFound(command.meetingId()));
        }
        var meeting = meetingOpt.get();

        if (!meeting.getHostId().equals(UserId.of(command.approvedBy()))) {
            return Result.failure(new MeetingError.NotAuthorized(
                    command.approvedBy(), meeting.getHostId().value()));
        }

        var requestOpt = joinRequestRepository.findById(command.requestId());
        if (requestOpt.isEmpty()) {
            return Result.failure(
                    new MeetingError.JoinRequestNotFound(command.meetingId(), command.requestId()));
        }
        var joinRequest = requestOpt.get();

        int maxParticipants = meeting.getSettings().maxParticipants();
        if (maxParticipants > 0) {
            long activeCount = participationLogRepository.countActiveByMeetingId(
                    meeting.getId().value());
            if (activeCount >= maxParticipants) {
                return Result.failure(
                        new MeetingError.MeetingFull(meeting.getId().value(), maxParticipants));
            }
        }

        var approveResult = joinRequest.approve();
        if (approveResult instanceof Result.Failure<?, MeetingError>(MeetingError error)) {
            return Result.failure(error);
        }

        LiveKitRoomName roomName = LiveKitRoomName.fromMeetingId(meeting.getId());
        ParticipantRole role = joinRequest.getUserId().isPresent()
                ? ParticipantRole.PARTICIPANT
                : ParticipantRole.GUEST;

        LiveKitIdentity identity = joinRequest.getUserId().isPresent()
                ? LiveKitIdentity.fromUser(joinRequest.getUserId().get(), joinRequest.getDeviceId())
                : LiveKitIdentity.forGuest(joinRequest.getDeviceId());

        var tokenResult = liveKitPort.generateToken(new LiveKitTokenRequest(
                roomName,
                identity,
                joinRequest.getDisplayName(),
                role,
                new ParticipantAttributes(
                        participantAvatarResolver.resolveAvatar(
                                joinRequest.getUserId().map(UserId::value).orElse(null)),
                        role),
                meeting.getSettings()));
        if (tokenResult instanceof Result.Failure<?, MeetingError>(MeetingError error)) {
            return Result.failure(error);
        }
        String token = ((Result.Success<String, MeetingError>) tokenResult).value();

        joinRequestRepository.updateStatus(command.requestId(), JoinRequestStatus.APPROVED);

        ParticipationLog participationLog = ParticipationLog.join(
                meeting.getId(),
                joinRequest.getUserId().map(UserId::value).orElse(null),
                joinRequest.getDisplayName(),
                role,
                identity);
        participationLogRepository.save(participationLog);
        log.debug(
                "Recorded participation log for identity '{}' in meeting '{}'",
                identity.value(),
                meeting.getId().value());

        var approvedEvent = new JoinRequestApprovedEvent(
                UUID.randomUUID(),
                command.meetingId(),
                command.requestId(),
                command.approvedBy(),
                token,
                roomName.value(),
                Instant.now());
        applicationEventPublisher.publishEvent(approvedEvent);

        joinRequestResultStore.save(
                JoinRequestResult.approved(command.requestId(), token, roomName.value()));

        joinRequestRepository.removeFromQueue(command.meetingId(), command.requestId());

        return Result.success(token);
    }
}
