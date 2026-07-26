package io.github.smiskinext.meet.application.service;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.smiskinext.meet.application.command.RequestJoinCommand;
import io.github.smiskinext.meet.application.result.RequestJoinResult;
import io.github.smiskinext.meet.application.usecase.RequestJoinUseCase;
import io.github.smiskinext.meet.domain.MeetingError;
import io.github.smiskinext.meet.domain.model.AdmissionPolicy;
import io.github.smiskinext.meet.domain.model.JoinRequest;
import io.github.smiskinext.meet.domain.model.Meeting;
import io.github.smiskinext.meet.domain.model.ParticipantRole;
import io.github.smiskinext.meet.domain.model.valueobject.AccountId;
import io.github.smiskinext.meet.domain.model.valueobject.LiveKitIdentity;
import io.github.smiskinext.meet.domain.model.valueobject.LiveKitRoomName;
import io.github.smiskinext.meet.domain.model.valueobject.LiveKitTokenRequest;
import io.github.smiskinext.meet.domain.model.valueobject.MeetingId;
import io.github.smiskinext.meet.domain.model.valueobject.ParticipantAttributes;
import io.github.smiskinext.meet.domain.port.JoinRequestRepository;
import io.github.smiskinext.meet.domain.port.LiveKitPort;
import io.github.smiskinext.meet.domain.port.MeetingRepository;
import io.github.smiskinext.meet.domain.port.ParticipationLogRepository;
import io.github.smiskinext.shared.domain.EventPublisher;
import io.github.smiskinext.shared.domain.Result;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles joining a meeting under both admission policies.
 *
 * <p>{@code ALLOW_ALL} admits the caller immediately: capacity is enforced while holding a
 * pessimistic lock on the meeting row so concurrent joins cannot exceed {@code maxParticipants} and
 * a LiveKit token is issued. No participation log is recorded here; recording the participation
 * session is deferred to the {@code participant_joined} webhook (the source of truth), which avoids
 * orphan logs for callers who obtain a token but never connect.
 *
 * <p>{@code MANUAL_APPROVAL} creates a pending {@link JoinRequest} in Redis with a fixed TTL,
 * registers a {@code JoinRequestCreated} event drained through the transactional outbox, and returns
 * without issuing a token. A repeated join from the same device while a pending request exists is
 * idempotent and returns the existing request.
 */
@Service
@Transactional
public class RequestJoinApplicationService implements RequestJoinUseCase {

    private static final Duration JOIN_REQUEST_TTL = Duration.ofMinutes(5);

    private final MeetingRepository meetingRepository;
    private final ParticipationLogRepository participationLogRepository;
    private final JoinRequestRepository joinRequestRepository;
    private final LiveKitPort liveKitPort;
    private final EventPublisher eventPublisher;

    public RequestJoinApplicationService(
            MeetingRepository meetingRepository,
            ParticipationLogRepository participationLogRepository,
            JoinRequestRepository joinRequestRepository,
            LiveKitPort liveKitPort,
            EventPublisher eventPublisher) {
        this.meetingRepository = meetingRepository;
        this.participationLogRepository = participationLogRepository;
        this.joinRequestRepository = joinRequestRepository;
        this.liveKitPort = liveKitPort;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Result<RequestJoinResult, MeetingError> execute(RequestJoinCommand command) {
        UUID meetingId = UUID.fromString(command.meetingId());
        Optional<Meeting> meetingLookup = meetingRepository.findActiveByIdWithLock(meetingId);
        if (meetingLookup.isEmpty()) {
            return Result.failure(new MeetingError.MeetingNotFound(meetingId));
        }

        Meeting meeting = meetingLookup.get();
        return meeting.getSettings().admissionPolicy() == AdmissionPolicy.ALLOW_ALL
                ? admitImmediately(command, meeting)
                : createPendingRequest(command, meeting);
    }

    private Result<RequestJoinResult, MeetingError> admitImmediately(
            RequestJoinCommand command, Meeting meeting) {
        MeetingId meetingId = meeting.getId();
        int limit = meeting.getSettings().maxParticipants();
        long activeCount = participationLogRepository.countActiveByMeetingId(meetingId.value());
        if (activeCount >= limit) {
            return Result.failure(new MeetingError.MeetingFull(meetingId.value(), limit));
        }

        AccountId accountId = AccountId.of(command.accountId());
        LiveKitRoomName roomName = LiveKitRoomName.fromMeetingId(meetingId);
        LiveKitIdentity identity = LiveKitIdentity.fromAccount(accountId, command.deviceId());
        ParticipantAttributes attributes =
                new ParticipantAttributes(command.avatarUrl(), ParticipantRole.PARTICIPANT);
        LiveKitTokenRequest tokenRequest = new LiveKitTokenRequest(
                roomName,
                identity,
                command.displayName(),
                ParticipantRole.PARTICIPANT,
                attributes,
                command.tenantId(),
                meeting.getSettings());

        Result<String, MeetingError> tokenResult = liveKitPort.generateToken(tokenRequest);
        if (tokenResult instanceof Result.Failure<String, MeetingError>(MeetingError error)) {
            return Result.failure(error);
        }
        String token = ((Result.Success<String, MeetingError>) tokenResult).value();

        return Result.success(RequestJoinResult.approved(
                UuidCreator.getTimeOrderedEpoch(), token, roomName.value()));
    }

    private Result<RequestJoinResult, MeetingError> createPendingRequest(
            RequestJoinCommand command, Meeting meeting) {
        MeetingId meetingId = meeting.getId();
        Optional<JoinRequest> existing =
                joinRequestRepository.findByDeviceId(meetingId.value(), command.deviceId());
        if (existing.isPresent()) {
            return Result.success(
                    RequestJoinResult.pending(existing.get().getId().value()));
        }

        Instant expiresAt = Instant.now().plus(JOIN_REQUEST_TTL);
        JoinRequest request = JoinRequest.create(
                meetingId,
                AccountId.of(command.accountId()),
                command.displayName(),
                command.deviceId(),
                command.avatarUrl(),
                expiresAt);
        joinRequestRepository.save(request, JOIN_REQUEST_TTL);

        request.registerCreatedEvent(command.tenantId());
        eventPublisher.publishEventsOf(request);

        return Result.success(RequestJoinResult.pending(request.getId().value()));
    }
}
