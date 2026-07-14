package io.github.smiskinext.meet.domain.model;

/**
 * Lifecycle status of the single invite token carried inline on a {@link MeetingInvitee}.
 *
 * <p>Valid transitions: {@code PENDING → USED}, {@code PENDING → REVOKED}.
 * The {@code EXPIRED} status is a logical state derived from the token's expiry timestamp
 * during validation; it is never persisted directly.
 */
public enum InviteTokenStatus {
    PENDING,
    USED,
    REVOKED,
    EXPIRED;

    /**
     * Returns {@code true} when a status transition from this state to {@code target} is
     * permitted by domain rules.
     */
    public boolean canTransitionTo(InviteTokenStatus target) {
        return switch (this) {
            case PENDING -> target == USED || target == REVOKED;
            case USED, REVOKED, EXPIRED -> false;
        };
    }
}
