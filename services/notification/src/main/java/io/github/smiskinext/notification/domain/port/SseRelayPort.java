package io.github.smiskinext.notification.domain.port;

import io.github.smiskinext.notification.domain.model.JoinDecision;
import io.github.smiskinext.notification.domain.model.PendingJoinRequest;

import java.util.UUID;

/**
 * Outbound port for pushing SSE events to connected clients.
 *
 * <p>Pure-Java interface — no Spring or framework imports. Implemented by
 * {@code SseConnectionManager} in the infrastructure layer.
 */
public interface SseRelayPort {

    /**
     * Pushes a {@code join_request_created} event to every host emitter held locally for the
     * meeting.
     *
     * @param meetingId the target meeting
     * @param request   the pending join request to relay
     */
    void pushJoinRequestCreated(UUID meetingId, PendingJoinRequest request);

    /**
     * Pushes the host's decision to every requester emitter held locally for the request id.
     *
     * @param requestId the target join request
     * @param decision  the recorded decision to deliver
     */
    void pushJoinResolved(UUID requestId, JoinDecision decision);
}
