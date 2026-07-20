package io.github.smiskinext.meet.domain.model;

/**
 * Status of a meeting invitation, aligned with the iCalendar (RFC 5545) PARTSTAT property.
 */
public enum InviteeStatus {
    NEEDS_ACTION,
    ACCEPTED,
    DECLINED,
    TENTATIVE;

    public boolean canTransitionTo(InviteeStatus target) {
        return switch (this) {
            case NEEDS_ACTION -> target == ACCEPTED || target == DECLINED || target == TENTATIVE;
            case TENTATIVE -> target == ACCEPTED || target == DECLINED;
            case ACCEPTED -> target == DECLINED || target == TENTATIVE;
            case DECLINED -> false;
        };
    }
}
