package io.github.smiskinext.meet.application.service;

import io.github.smiskinext.meet.application.command.DeclineJoinRequestsCommand;
import io.github.smiskinext.meet.application.mapper.JoinDecisionMapper;
import io.github.smiskinext.meet.application.result.DeclineJoinRequestsResult;
import io.github.smiskinext.meet.application.result.JoinDecisionItemResult;
import io.github.smiskinext.meet.application.usecase.DeclineJoinRequestsUseCase;
import io.github.smiskinext.meet.domain.MeetingError;
import io.github.smiskinext.meet.domain.MeetingErrorCode;
import io.github.smiskinext.meet.domain.model.JoinRequest;
import io.github.smiskinext.meet.domain.model.JoinRequestResult;
import io.github.smiskinext.meet.domain.model.JoinRequestStatus;
import io.github.smiskinext.meet.domain.model.Meeting;
import io.github.smiskinext.meet.domain.model.valueobject.MeetingId;
import io.github.smiskinext.meet.domain.port.JoinRequestRepository;
import io.github.smiskinext.meet.domain.port.JoinRequestResultStore;
import io.github.smiskinext.meet.domain.port.MeetingRepository;
import io.github.smiskinext.shared.domain.EventPublisher;
import io.github.smiskinext.shared.domain.Result;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles a host declining one or more pending join requests.
 *
 * <p>Request-level faults (unknown meeting, non-host caller) short-circuit as a {@code
 * Result.failure} so the whole request maps to a 4xx problem response. Individual request ids are
 * then processed best-effort under a single pessimistic meeting-row lock: each id yields a per-item
 * result and no item fault fails the batch. For each declined request the request transitions to
 * {@code DENIED}, its terminal outcome is persisted, a denied event is registered for the
 * transactional outbox, and the request is removed from the pending queue.
 */
@Service
@Transactional
public class DeclineJoinRequestsApplicationService implements DeclineJoinRequestsUseCase {

    private final MeetingRepository meetingRepository;
    private final JoinRequestRepository joinRequestRepository;
    private final JoinRequestResultStore joinRequestResultStore;
    private final EventPublisher eventPublisher;

    public DeclineJoinRequestsApplicationService(
            MeetingRepository meetingRepository,
            JoinRequestRepository joinRequestRepository,
            JoinRequestResultStore joinRequestResultStore,
            EventPublisher eventPublisher) {
        this.meetingRepository = meetingRepository;
        this.joinRequestRepository = joinRequestRepository;
        this.joinRequestResultStore = joinRequestResultStore;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Result<DeclineJoinRequestsResult, MeetingError> execute(
            DeclineJoinRequestsCommand command) {
        UUID meetingId = command.meetingId();
        Optional<Meeting> meetingLookup = meetingRepository.findActiveByIdWithLock(meetingId);
        if (meetingLookup.isEmpty()) {
            return Result.failure(new MeetingError.MeetingNotFound(meetingId));
        }

        Meeting meeting = meetingLookup.get();
        if (!meeting.getHostId().value().equals(command.accountId())) {
            return Result.failure(new MeetingError.NotOwner(
                    command.accountId(), meeting.getHostId().value()));
        }

        List<JoinDecisionItemResult> results =
                new ArrayList<>(command.requestIds().size());
        for (UUID requestId : command.requestIds()) {
            results.add(declineOne(command, meeting.getId(), requestId));
        }

        return Result.success(new DeclineJoinRequestsResult(results));
    }

    private JoinDecisionItemResult declineOne(
            DeclineJoinRequestsCommand command, MeetingId meetingId, UUID requestId) {
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

        Result<Void, MeetingError> denial = request.deny();
        if (denial instanceof Result.Failure<Void, MeetingError>(MeetingError error)) {
            return JoinDecisionItemResult.failed(requestId, error.errorCode().code());
        }

        joinRequestResultStore.save(JoinRequestResult.denied(requestId, null));
        request.registerDeniedEvent(command.tenantId(), command.accountId());
        eventPublisher.publishEventsOf(request);
        joinRequestRepository.removeFromQueue(meetingId.value(), requestId);

        return JoinDecisionMapper.denied(requestId);
    }
}
