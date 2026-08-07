package io.github.smiskinext.meet.application.service;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.smiskinext.meet.application.command.RequestJoinCommand;
import io.github.smiskinext.meet.application.helper.JoinAdmissionSupport;
import io.github.smiskinext.meet.application.result.RequestJoinResult;
import io.github.smiskinext.meet.application.usecase.RequestJoinUseCase;
import io.github.smiskinext.meet.domain.MeetingError;
import io.github.smiskinext.meet.domain.model.AdmissionPolicy;
import io.github.smiskinext.meet.domain.model.JoinRequest;
import io.github.smiskinext.meet.domain.model.JoinRequestResult;
import io.github.smiskinext.meet.domain.model.Meeting;
import io.github.smiskinext.meet.domain.model.MeetingInvitee;
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
import io.github.smiskinext.meet.domain.port.MeetingInviteeRepository;
import io.github.smiskinext.meet.domain.port.MeetingRepository;
import io.github.smiskinext.meet.domain.port.ParticipationLogRepository;
import io.github.smiskinext.shared.domain.EventPublisher;
import io.github.smiskinext.shared.domain.Result;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles joining a meeting under both admission policies.
 *
 * <p>{@code ALLOW_ALL} admits the caller immediately: capacity is enforced via a 3-phase approach
 * (optimistic pre-check → token generation → pessimistic final verification) to minimize lock hold
 * time. The optimistic pre-check performs a dirty read of meeting existence and capacity without
 * locking, failing fast when the meeting is obviously full. Token generation occurs outside any
 * lock. The pessimistic final verification re-checks capacity under a short lock immediately before
 * returning the token, failing with {@code MeetingFull} if capacity was exhausted during token
 * generation. A race condition (optimistic check passes but final verification fails) is logged for
 * observability. No participation log is recorded here; recording the participation session is
 * deferred to the {@code participant_joined} webhook (the source of truth), which avoids orphan
 * logs for callers who obtain a token but never connect.
 *
 * <p>{@code MANUAL_APPROVAL} resolves immediate-admission eligibility first: the host and
 * already-responding invitees (ACCEPTED or TENTATIVE) bypass the queue through the same 3-phase
 * immediate-admission logic as {@code ALLOW_ALL}, and when a bypassing caller has a pre-existing
 * pending request for the same device, that request is reconciled to APPROVED under the final
 * verification lock, its terminal outcome is persisted, and a join-approved event is published.
 * Every other caller creates a pending {@link JoinRequest} in Redis with a fixed TTL without
 * acquiring any database lock, registers a {@code JoinRequestCreated} event drained through the
 * transactional outbox, and returns without issuing a token. A repeated join from the same device
 * while a pending request exists is idempotent and returns the existing request.
 */
@Service
@Transactional
public class RequestJoinApplicationService implements RequestJoinUseCase {

    private static final Logger log = LoggerFactory.getLogger(RequestJoinApplicationService.class);
    private static final Duration JOIN_REQUEST_TTL = Duration.ofMinutes(5);

    private final MeetingRepository meetingRepository;
    private final ParticipationLogRepository participationLogRepository;
    private final JoinRequestRepository joinRequestRepository;
    private final JoinRequestResultStore joinRequestResultStore;
    private final MeetingInviteeRepository meetingInviteeRepository;
    private final LiveKitPort liveKitPort;
    private final EventPublisher eventPublisher;

    public RequestJoinApplicationService(
            MeetingRepository meetingRepository,
            ParticipationLogRepository participationLogRepository,
            JoinRequestRepository joinRequestRepository,
            JoinRequestResultStore joinRequestResultStore,
            MeetingInviteeRepository meetingInviteeRepository,
            LiveKitPort liveKitPort,
            EventPublisher eventPublisher) {
        this.meetingRepository = meetingRepository;
        this.participationLogRepository = participationLogRepository;
        this.joinRequestRepository = joinRequestRepository;
        this.joinRequestResultStore = joinRequestResultStore;
        this.meetingInviteeRepository = meetingInviteeRepository;
        this.liveKitPort = liveKitPort;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Result<RequestJoinResult, MeetingError> execute(RequestJoinCommand command) {
        UUID meetingId = UUID.fromString(command.meetingId());

        Optional<Meeting> meetingLookup = meetingRepository.findActiveById(meetingId);
        if (meetingLookup.isEmpty()) {
            return Result.failure(new MeetingError.MeetingNotFound(meetingId));
        }

        Meeting meeting = meetingLookup.get();
        if (meeting.getSettings().admissionPolicy() == AdmissionPolicy.ALLOW_ALL) {
            return issueAdmissionToken(command, meeting).map(Admission::toApprovedResult);
        }

        Optional<MeetingInvitee> invitee = findInviteeUnlessHost(command, meeting);
        boolean eligibleForBypass = JoinAdmissionSupport.isEligibleForImmediateAdmission(
                meeting, command.accountId(), invitee);
        return eligibleForBypass
                ? admitBypassingCaller(command, meeting, invitee)
                : createPendingRequest(command, meeting);
    }

    /**
     * Looks up the caller's active invitation, skipping the query when the caller hosts the
     * meeting because host identity alone settles eligibility.
     */
    private Optional<MeetingInvitee> findInviteeUnlessHost(
            RequestJoinCommand command, Meeting meeting) {
        String accountId = command.accountId();
        if (meeting.getHostId().value().equals(accountId)) {
            return Optional.empty();
        }
        return meetingInviteeRepository.findByMeetingIdAndAccountId(
                meeting.getId().value(), AccountId.of(accountId));
    }

    private Result<RequestJoinResult, MeetingError> admitBypassingCaller(
            RequestJoinCommand command, Meeting meeting, Optional<MeetingInvitee> invitee) {
        Result<Admission, MeetingError> admission = issueAdmissionToken(command, meeting);
        if (admission instanceof Result.Success<Admission, MeetingError>(Admission granted)) {
            reconcileSupersededRequest(command, granted);
        }
        return admission.map(Admission::toApprovedResult);
    }

    private Result<Admission, MeetingError> issueAdmissionToken(
            RequestJoinCommand command, Meeting meeting) {
        MeetingId meetingId = meeting.getId();
        int limit = meeting.getSettings().maxParticipants();

        // Phase 1: Optimistic pre-check (no lock, dirty read)
        // Fail fast when capacity is obviously full without acquiring lock
        long activeCount = participationLogRepository.countActiveByMeetingId(meetingId.value());
        if (activeCount >= limit) {
            return Result.failure(new MeetingError.MeetingFull(meetingId.value(), limit));
        }

        // Phase 2: Token generation (no lock)
        // Expensive JWT signing happens outside lock to minimize lock hold time
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

        // Phase 3: Final verification (short lock, authoritative check)
        // Re-check capacity under lock to catch races between phase 1 and 3
        // Lock is acquired via findActiveByIdWithLock; the returned object is discarded
        if (meetingRepository.findActiveByIdWithLock(meetingId.value()).isEmpty()) {
            return Result.failure(new MeetingError.MeetingNotFound(meetingId.value()));
        }

        long finalActiveCount =
                participationLogRepository.countActiveByMeetingId(meetingId.value());
        if (finalActiveCount >= limit) {
            log.warn("Capacity race detected for meeting {}", meetingId.value());
            return Result.failure(new MeetingError.MeetingFull(meetingId.value(), limit));
        }

        return Result.success(new Admission(meetingId, token, roomName.value()));
    }

    /**
     * Drives a same-device pending request superseded by the caller's immediate admission to its
     * APPROVED terminal state, so the host's queue self-cleans and a requester already waiting on
     * the request's stream resolves with the same token returned in the join response.
     *
     * <p>A request already denied by the host is terminal in {@link JoinRequest#approve()} and is
     * therefore left exactly as it is: the caller is admitted on the strength of their invitation,
     * independently of any prior request, so no outcome is persisted and no event is published.
     */
    private void reconcileSupersededRequest(RequestJoinCommand command, Admission admission) {
        Optional<JoinRequest> superseded = joinRequestRepository.findByDeviceId(
                admission.meetingId().value(), command.deviceId());
        if (superseded.isEmpty()) {
            return;
        }

        JoinRequest request = superseded.get();
        if (request.approve().isFailure()) {
            return;
        }

        UUID requestId = request.getId().value();
        joinRequestResultStore.save(
                JoinRequestResult.approved(requestId, admission.token(), admission.roomName()));
        request.registerApprovedEvent(
                command.tenantId(), admission.token(), admission.roomName(), command.accountId());
        eventPublisher.publishEventsOf(request);
        joinRequestRepository.removeFromQueue(admission.meetingId().value(), requestId);
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

    /**
     * A granted admission: the issued LiveKit token and the room it admits the caller to.
     */
    private record Admission(MeetingId meetingId, String token, String roomName) {

        RequestJoinResult toApprovedResult() {
            return RequestJoinResult.approved(UuidCreator.getTimeOrderedEpoch(), token, roomName);
        }
    }
}
