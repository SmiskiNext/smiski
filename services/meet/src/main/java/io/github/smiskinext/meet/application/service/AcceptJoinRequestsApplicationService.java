package io.github.smiskinext.meet.application.service;

import io.github.smiskinext.meet.application.command.AcceptJoinRequestsCommand;
import io.github.smiskinext.meet.application.mapper.JoinDecisionMapper;
import io.github.smiskinext.meet.application.result.AcceptJoinRequestsResult;
import io.github.smiskinext.meet.application.result.JoinDecisionItemResult;
import io.github.smiskinext.meet.application.result.JoinDecisionStatus;
import io.github.smiskinext.meet.application.usecase.AcceptJoinRequestsUseCase;
import io.github.smiskinext.meet.domain.MeetingError;
import io.github.smiskinext.meet.domain.MeetingErrorCode;
import io.github.smiskinext.meet.domain.model.JoinRequest;
import io.github.smiskinext.meet.domain.model.JoinRequestResult;
import io.github.smiskinext.meet.domain.model.JoinRequestStatus;
import io.github.smiskinext.meet.domain.model.Meeting;
import io.github.smiskinext.meet.domain.model.ParticipantRole;
import io.github.smiskinext.meet.domain.model.valueobject.AccountId;
import io.github.smiskinext.meet.domain.model.valueobject.LiveKitIdentity;
import io.github.smiskinext.meet.domain.model.valueobject.LiveKitRoomName;
import io.github.smiskinext.meet.domain.model.valueobject.LiveKitTokenRequest;
import io.github.smiskinext.meet.domain.model.valueobject.MeetingId;
import io.github.smiskinext.meet.domain.model.valueobject.ParticipantAttributes;
import io.github.smiskinext.meet.domain.port.JoinRequestRepository;
import io.github.smiskinext.meet.domain.port.JoinRequestResultStore;
import io.github.smiskinext.meet.domain.port.LiveKitPort;
import io.github.smiskinext.meet.domain.port.MeetingRepository;
import io.github.smiskinext.meet.domain.port.ParticipationLogRepository;
import io.github.smiskinext.shared.domain.EventPublisher;
import io.github.smiskinext.shared.domain.Result;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles a host accepting one or more pending join requests.
 *
 * <p>Request-level faults (unknown meeting, non-host caller) short-circuit as a {@code
 * Result.failure} so the whole request maps to a 4xx problem response. Individual request ids are
 * then processed best-effort: tokens are pre-generated outside any lock, then capacity is verified
 * and requests are approved under a single pessimistic meeting-row lock. Each id yields a per-item
 * result and no item fault fails the batch.
 *
 * <p>Capacity is read once under lock and decremented in memory as approvals succeed so a batch can
 * never admit more than {@code maxParticipants}. For each admitted request a {@code PARTICIPANT}
 * LiveKit token (pre-generated before lock acquisition) is used, the request transitions to {@code
 * APPROVED}, its terminal outcome is persisted, an approved event is registered for the
 * transactional outbox, and the request is removed from the pending queue. No participation log is
 * recorded here; recording is deferred to the {@code participant_joined} webhook.
 */
@Service
@Transactional
public class AcceptJoinRequestsApplicationService implements AcceptJoinRequestsUseCase {

    private final MeetingRepository meetingRepository;
    private final ParticipationLogRepository participationLogRepository;
    private final JoinRequestRepository joinRequestRepository;
    private final JoinRequestResultStore joinRequestResultStore;
    private final LiveKitPort liveKitPort;
    private final EventPublisher eventPublisher;

    public AcceptJoinRequestsApplicationService(
            MeetingRepository meetingRepository,
            ParticipationLogRepository participationLogRepository,
            JoinRequestRepository joinRequestRepository,
            JoinRequestResultStore joinRequestResultStore,
            LiveKitPort liveKitPort,
            EventPublisher eventPublisher) {
        this.meetingRepository = meetingRepository;
        this.participationLogRepository = participationLogRepository;
        this.joinRequestRepository = joinRequestRepository;
        this.joinRequestResultStore = joinRequestResultStore;
        this.liveKitPort = liveKitPort;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Result<AcceptJoinRequestsResult, MeetingError> execute(
            AcceptJoinRequestsCommand command) {
        UUID meetingId = command.meetingId();

        // Authorization phase (no lock)
        // Reject unknown meetings and non-host callers before minting any token
        Optional<Meeting> meetingForToken = meetingRepository.findActiveById(meetingId);
        if (meetingForToken.isEmpty()) {
            return Result.failure(new MeetingError.MeetingNotFound(meetingId));
        }
        Meeting meetingSnapshot = meetingForToken.get();
        if (!meetingSnapshot.getHostId().value().equals(command.accountId())) {
            return Result.failure(new MeetingError.NotOwner(
                    command.accountId(), meetingSnapshot.getHostId().value()));
        }

        // Token pre-generation phase (no lock)
        // Generate all tokens before acquiring lock to minimize lock hold time
        LiveKitRoomName roomName = LiveKitRoomName.fromMeetingId(MeetingId.of(meetingId));

        Map<UUID, String> preGeneratedTokens = new HashMap<>();
        for (UUID requestId : command.requestIds()) {
            Optional<JoinRequest> lookup = joinRequestRepository.findById(requestId);
            if (lookup.isEmpty()) {
                continue;
            }
            JoinRequest request = lookup.get();
            if (!request.getMeetingId().value().equals(meetingId)) {
                continue;
            }
            if (request.getStatus() != JoinRequestStatus.PENDING) {
                continue;
            }

            Result<String, MeetingError> tokenResult =
                    liveKitPort.generateToken(participantTokenRequest(
                            command.tenantId(), meetingSnapshot, request, roomName));
            if (tokenResult instanceof Result.Success<String, MeetingError>(String token)) {
                preGeneratedTokens.put(requestId, token);
            }
        }

        // Final verification and approval phase (with lock)
        // Acquire lock only after all tokens are generated
        Optional<Meeting> meetingLookup = meetingRepository.findActiveByIdWithLock(meetingId);
        if (meetingLookup.isEmpty()) {
            return Result.failure(new MeetingError.MeetingNotFound(meetingId));
        }

        Meeting meeting = meetingLookup.get();
        if (!meeting.getHostId().value().equals(command.accountId())) {
            return Result.failure(new MeetingError.NotOwner(
                    command.accountId(), meeting.getHostId().value()));
        }

        // Capacity enforcement under lock
        // Read capacity once, decrement in-memory for each approved request
        int maxParticipants = meeting.getSettings().maxParticipants();
        long activeCount = participationLogRepository.countActiveByMeetingId(meetingId);
        long seatsLeft = maxParticipants - activeCount;

        Instant now = Instant.now();
        List<JoinDecisionItemResult> results =
                new ArrayList<>(command.requestIds().size());
        for (UUID requestId : command.requestIds()) {
            JoinDecisionItemResult item =
                    acceptOne(command, meeting, requestId, now, seatsLeft, preGeneratedTokens);
            if (item.status() == JoinDecisionStatus.APPROVED) {
                seatsLeft--;
            }
            results.add(item);
        }

        return Result.success(new AcceptJoinRequestsResult(results));
    }

    private JoinDecisionItemResult acceptOne(
            AcceptJoinRequestsCommand command,
            Meeting meeting,
            UUID requestId,
            Instant now,
            long seatsLeft,
            Map<UUID, String> preGeneratedTokens) {
        MeetingId meetingId = meeting.getId();
        Optional<JoinRequest> lookup = joinRequestRepository.findById(requestId);
        if (lookup.isEmpty()) {
            return JoinDecisionMapper.failed(requestId, MeetingErrorCode.JOIN_REQUEST_NOT_FOUND);
        }

        JoinRequest request = lookup.get();
        if (!request.getMeetingId().value().equals(meetingId.value())) {
            return JoinDecisionMapper.failed(requestId, MeetingErrorCode.JOIN_REQUEST_NOT_FOUND);
        }
        if (request.getStatus() != JoinRequestStatus.PENDING) {
            MeetingErrorCode reason = request.getStatus() == JoinRequestStatus.EXPIRED
                    ? MeetingErrorCode.JOIN_REQUEST_EXPIRED
                    : MeetingErrorCode.INVALID_JOIN_REQUEST_TRANSITION;
            return JoinDecisionMapper.failed(requestId, reason);
        }
        if (request.getExpiresAt().isBefore(now)) {
            return JoinDecisionMapper.failed(requestId, MeetingErrorCode.JOIN_REQUEST_EXPIRED);
        }
        if (seatsLeft <= 0) {
            return JoinDecisionMapper.failed(requestId, MeetingErrorCode.MEETING_FULL);
        }

        String token = preGeneratedTokens.get(requestId);
        if (token == null) {
            return JoinDecisionItemResult.failed(
                    requestId, MeetingErrorCode.LIVEKIT_UNAVAILABLE.code());
        }

        Result<Void, MeetingError> approval = request.approve();
        if (approval instanceof Result.Failure<Void, MeetingError>(MeetingError error)) {
            return JoinDecisionItemResult.failed(requestId, error.errorCode().code());
        }

        LiveKitRoomName roomName = LiveKitRoomName.fromMeetingId(meetingId);
        joinRequestResultStore.save(JoinRequestResult.approved(requestId, token, roomName.value()));
        request.registerApprovedEvent(
                command.tenantId(), token, roomName.value(), command.accountId());
        eventPublisher.publishEventsOf(request);
        joinRequestRepository.removeFromQueue(meetingId.value(), requestId);

        return JoinDecisionMapper.approved(requestId, token, roomName.value());
    }

    private LiveKitTokenRequest participantTokenRequest(
            String tenantId, Meeting meeting, JoinRequest request, LiveKitRoomName roomName) {
        AccountId accountId = request.getAccountId();
        LiveKitIdentity identity = LiveKitIdentity.fromAccount(accountId, request.getDeviceId());
        ParticipantAttributes attributes =
                new ParticipantAttributes(request.getAvatarUrl(), ParticipantRole.PARTICIPANT);
        return new LiveKitTokenRequest(
                roomName,
                identity,
                request.getDisplayName(),
                ParticipantRole.PARTICIPANT,
                attributes,
                tenantId,
                meeting.getSettings());
    }
}
