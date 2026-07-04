package io.github.smiskinext.meetingmanagement.application.usecase;

import io.github.smiskinext.meetingmanagement.domain.event.JoinRequestDeniedEvent;
import io.github.smiskinext.meetingmanagement.domain.event.MeetingEndedEvent;
import io.github.smiskinext.meetingmanagement.domain.model.JoinRequest;
import io.github.smiskinext.meetingmanagement.domain.model.JoinRequestStatus;
import io.github.smiskinext.meetingmanagement.domain.port.JoinRequestRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Handles meeting end events by auto-denying all pending join requests.
 *
 * <p>Listens to {@link MeetingEndedEvent} and cleans up the join request queue for the ended
 * meeting. Publishes {@link JoinRequestDeniedEvent} per pending request to notify guests via SSE.
 */
@Component
public class MeetingEndedJoinRequestHandleUseCase {

    private static final Logger log =
            LoggerFactory.getLogger(MeetingEndedJoinRequestHandleUseCase.class);

    private final JoinRequestRepository joinRequestRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    public MeetingEndedJoinRequestHandleUseCase(
            JoinRequestRepository joinRequestRepository,
            ApplicationEventPublisher applicationEventPublisher) {
        this.joinRequestRepository = joinRequestRepository;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(MeetingEndedEvent event) {
        UUID meetingId = event.aggregateId();

        List<JoinRequest> pendingRequests = joinRequestRepository.findPendingByMeetingId(meetingId);

        if (pendingRequests.isEmpty()) {
            return;
        }

        log.info(
                "Auto-denying {} pending join requests for ended meeting {}",
                pendingRequests.size(),
                meetingId);

        for (JoinRequest joinRequest : pendingRequests) {
            joinRequestRepository.updateStatus(
                    joinRequest.getId().value(), JoinRequestStatus.DENIED);

            var deniedEvent = new JoinRequestDeniedEvent(
                    UUID.randomUUID(), meetingId, joinRequest.getId().value(), null, Instant.now());
            applicationEventPublisher.publishEvent(deniedEvent);
        }

        joinRequestRepository.deleteAllByMeetingId(meetingId);
    }
}
