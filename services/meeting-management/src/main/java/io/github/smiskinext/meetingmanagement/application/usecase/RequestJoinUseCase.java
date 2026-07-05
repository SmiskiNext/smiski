package io.github.smiskinext.meetingmanagement.application.usecase;

import io.github.smiskinext.meetingmanagement.application.command.RequestJoinCommand;
import io.github.smiskinext.meetingmanagement.application.helper.ParticipantAvatarResolver;
import io.github.smiskinext.meetingmanagement.application.response.RequestJoinResponse;
import io.github.smiskinext.meetingmanagement.domain.MeetingError;
import io.github.smiskinext.meetingmanagement.domain.PublishableEvent;
import io.github.smiskinext.meetingmanagement.domain.event.JoinRequestCreatedEvent;
import io.github.phunguy65.zms.meetingmanagement.domain.model.*;
import io.github.smiskinext.meetingmanagement.domain.model.*;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.LiveKitIdentity;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.LiveKitRoomName;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.LiveKitTokenRequest;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.ParticipantAttributes;
import io.github.phunguy65.zms.meetingmanagement.domain.port.*;
import io.github.smiskinext.shared.domain.Result;
import io.github.smiskinext.shared.domain.valueobject.MeetingId;
import io.github.smiskinext.shared.domain.valueobject.UserId;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import io.github.smiskinext.meetingmanagement.domain.port.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RequestJoinUseCase {

    private static final Logger log = LoggerFactory.getLogger(RequestJoinUseCase.class);

    private final MeetingRepository meetingRepository;
    private final JoinRequestRepository joinRequestRepository;
    private final ParticipationLogRepository participationLogRepository;
    private final ParticipantAvatarResolver participantAvatarResolver;
    private final LiveKitPort liveKitPort;
    private final PasswordHasher passwordHasher;
    private final ApplicationEventPublisher applicationEventPublisher;

    public RequestJoinUseCase(
            MeetingRepository meetingRepository,
            JoinRequestRepository joinRequestRepository,
            ParticipationLogRepository participationLogRepository,
            ParticipantAvatarResolver participantAvatarResolver,
            LiveKitPort liveKitPort,
            PasswordHasher passwordHasher,
            ApplicationEventPublisher applicationEventPublisher) {
        this.meetingRepository = meetingRepository;
        this.joinRequestRepository = joinRequestRepository;
        this.participationLogRepository = participationLogRepository;
        this.participantAvatarResolver = participantAvatarResolver;
        this.liveKitPort = liveKitPort;
        this.passwordHasher = passwordHasher;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    /**
     * Processes a join request and implicitly starts a scheduled meeting when the requester is the host.
     */
    @Transactional
    public Result<RequestJoinResponse, MeetingError> execute(RequestJoinCommand command) {
        var meetingOpt = meetingRepository.findByIdWithLock(command.meetingId());
        if (meetingOpt.isEmpty()) {
            return Result.failure(new MeetingError.MeetingNotFound(command.meetingId()));
        }
        var meeting = meetingOpt.get();

        boolean isHost =
                command.userId() != null && meeting.getHostId().equals(UserId.of(command.userId()));
        var statusResult = prepareMeetingForJoin(meeting, isHost);
        if (statusResult instanceof Result.Failure<?, MeetingError>(MeetingError error)) {
            return Result.failure(error);
        }

        if (command.userId() == null && !meeting.getSettings().allowGuest()) {
            return Result.failure(
                    new MeetingError.GuestNotAllowed(meeting.getId().value()));
        }

        if (!isHost && meeting.getSettings().isPasswordProtected()) {
            String rawPassword = command.password();
            if (rawPassword == null
                    || rawPassword.isBlank()
                    || !passwordHasher.verify(rawPassword, meeting.getSettings().password())) {
                return Result.failure(
                        new MeetingError.InvalidPassword(meeting.getId().value()));
            }
        }

        LiveKitRoomName roomName = LiveKitRoomName.fromMeetingId(meeting.getId());

        if (isHost || meeting.getSettings().admissionPolicy() == AdmissionPolicy.ALLOW_ALL) {
            ParticipantRole role = isHost
                    ? ParticipantRole.HOST
                    : (command.userId() == null
                            ? ParticipantRole.GUEST
                            : ParticipantRole.PARTICIPANT);

            int maxParticipants = meeting.getSettings().maxParticipants();
            if (maxParticipants > 0 && role != ParticipantRole.HOST) {
                long activeCount = participationLogRepository.countActiveByMeetingId(
                        meeting.getId().value());
                if (activeCount >= maxParticipants) {
                    return Result.failure(
                            new MeetingError.MeetingFull(meeting.getId().value(), maxParticipants));
                }
            }

            LiveKitIdentity identity = command.userId() != null
                    ? LiveKitIdentity.fromUser(UserId.of(command.userId()), command.deviceId())
                    : LiveKitIdentity.forGuest(command.deviceId());

            var tokenResult = liveKitPort.generateToken(new LiveKitTokenRequest(
                    roomName,
                    identity,
                    command.displayName(),
                    role,
                    new ParticipantAttributes(
                            participantAvatarResolver.resolveAvatar(command.userId()), role),
                    meeting.getSettings()));
            if (tokenResult instanceof Result.Failure<?, MeetingError>(MeetingError error)) {
                return Result.failure(error);
            }
            String token = ((Result.Success<String, MeetingError>) tokenResult).value();

            ParticipationLog participationLog = ParticipationLog.join(
                    meeting.getId(), command.userId(), command.displayName(), role, identity);
            participationLogRepository.save(participationLog);
            publishMeetingEvents(meetingRepository.save(meeting));
            log.debug(
                    "Recorded participation log for identity '{}' in meeting '{}'",
                    identity.value(),
                    meeting.getId().value());

            return Result.success(new RequestJoinResponse(
                    UUID.randomUUID(), JoinRequestStatus.APPROVED, token, roomName.value()));
        }

        Optional<JoinRequest> existing = findExisting(command, meeting.getId());
        if (existing.isPresent()) {
            JoinRequest req = existing.get();
            return Result.success(
                    new RequestJoinResponse(req.getId().value(), req.getStatus(), null, null));
        }

        Duration ttl = Duration.ofMinutes(5);

        JoinRequest joinRequest = JoinRequest.create(
                meeting.getId(),
                command.userId() != null ? UserId.of(command.userId()) : null,
                command.displayName(),
                command.deviceId(),
                Instant.now().plus(ttl));

        joinRequestRepository.save(joinRequest, ttl);
        publishMeetingEvents(meetingRepository.save(meeting));

        var createdEvent = new JoinRequestCreatedEvent(
                UUID.randomUUID(),
                meeting.getId().value(),
                joinRequest.getId().value(),
                command.userId(),
                command.displayName(),
                command.deviceId(),
                Instant.now());
        applicationEventPublisher.publishEvent(createdEvent);

        return Result.success(new RequestJoinResponse(
                joinRequest.getId().value(), JoinRequestStatus.PENDING, null, null));
    }

    private Result<Void, MeetingError> prepareMeetingForJoin(Meeting meeting, boolean isHost) {
        MeetingStatus status = meeting.getStatus();
        if (isHost) {
            if (status == MeetingStatus.SCHEDULED) {
                return meeting.start();
            }
            if (status == MeetingStatus.LIVE) {
                return Result.success();
            }
            return Result.failure(
                    new MeetingError.InvalidStatusTransition(status, MeetingStatus.LIVE));
        }

        if (status != MeetingStatus.LIVE) {
            return Result.failure(
                    new MeetingError.InvalidStatusTransition(status, MeetingStatus.LIVE));
        }

        return Result.success();
    }

    private void publishMeetingEvents(Meeting meeting) {
        meeting.getDomainEvents().stream()
                .filter(PublishableEvent.class::isInstance)
                .map(PublishableEvent.class::cast)
                .forEach(applicationEventPublisher::publishEvent);
        meeting.clearDomainEvents();
    }

    private Optional<JoinRequest> findExisting(RequestJoinCommand command, MeetingId meetingId) {
        if (command.userId() != null) {
            var byUser = joinRequestRepository.findPendingByMeetingId(meetingId.value()).stream()
                    .filter(r -> r.getUserId()
                            .map(uid -> uid.value().equals(command.userId()))
                            .orElse(false))
                    .filter(r -> r.getStatus() == JoinRequestStatus.PENDING)
                    .findFirst();
            if (byUser.isPresent()) return byUser;
        }

        return joinRequestRepository
                .findByDeviceId(meetingId.value(), command.deviceId())
                .filter(r -> r.getStatus() == JoinRequestStatus.PENDING);
    }
}
