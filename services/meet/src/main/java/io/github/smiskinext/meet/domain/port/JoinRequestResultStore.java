package io.github.smiskinext.meet.domain.port;

import io.github.smiskinext.meet.domain.model.JoinRequestResult;

import java.util.Optional;
import java.util.UUID;

/**
 * Port for persisting terminal outcomes of join requests so that a late-subscribing SSE client can
 * replay the resolution event when it connects after the host has already acted.
 *
 * <p>Solves the race where the host approves/denies a join request before the requester finishes
 * opening the SSE stream and registering its emitter. The TTL of the persisted result is owned by
 * the implementation and aligned with the join request SSE timeout.
 */
public interface JoinRequestResultStore {

    void save(JoinRequestResult result);

    Optional<JoinRequestResult> findByRequestId(UUID requestId);

    void delete(UUID requestId);
}
