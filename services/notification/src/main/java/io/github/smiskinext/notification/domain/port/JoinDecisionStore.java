package io.github.smiskinext.notification.domain.port;

import io.github.smiskinext.notification.domain.model.JoinDecision;

import java.util.Optional;
import java.util.UUID;

/**
 * Store of host join decisions keyed per request id, shared across notification replicas so a
 * requester subscribing after the decision arrives — on any replica — can be shown the recorded
 * outcome.
 *
 * <p>Entries carry a time-to-live aligned with the requester notification window; reads return only
 * an unexpired decision. An approved decision retains the LiveKit token and room name.
 */
public interface JoinDecisionStore {

    /**
     * Records (or refreshes) the decision for a join request.
     *
     * @param decision the terminal decision to retain
     */
    void upsert(JoinDecision decision);

    /**
     * Returns the unexpired recorded decision for a request id, if present.
     *
     * @param joinRequestId the join request whose decision to read
     * @return the recorded decision, or {@code Optional.empty()} if none or expired
     */
    Optional<JoinDecision> findByRequestId(UUID joinRequestId);
}
