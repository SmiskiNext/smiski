package io.github.smiskinext.meetingmanagement.domain.model;

/**
 * Lifecycle states of a join request in the admission queue.
 */
public enum JoinRequestStatus {

    /**
     * Request submitted and awaiting host decision.
     */
    PENDING,

    /**
     * Host approved the request; participant may now join.
     */
    APPROVED,

    /**
     * Host denied the request; participant cannot join.
     */
    DENIED,

    /**
     * Request was not acted upon before its TTL elapsed.
     */
    EXPIRED;

    /**
     * Returns {@code true} if this status is a terminal (non-actionable) state.
     */
    public boolean isTerminal() {
        return this == APPROVED || this == DENIED || this == EXPIRED;
    }
}
