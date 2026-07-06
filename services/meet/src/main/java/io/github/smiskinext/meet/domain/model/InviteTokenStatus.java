package io.github.smiskinext.meet.domain.model;

/**
 * Lifecycle status of a per-invitee meeting invite token.
 *
 * <p>Valid transitions: {@code PENDING → USED}, {@code PENDING → REVOKED}.
 * Tokens in {@code EXPIRED} status are those past their {@code expiresAt} timestamp;
 * the system does not write EXPIRED directly — it is a logical state derived during validation.
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
