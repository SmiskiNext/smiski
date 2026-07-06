package io.github.smiskinext.meet.domain.model;

public enum MeetingStatus {
    SCHEDULED,
    LIVE,
    ENDED,
    CANCELLED;

    public boolean canTransitionTo(MeetingStatus target) {
        return switch (this) {
            case SCHEDULED -> target == LIVE || target == CANCELLED;
            case LIVE -> target == ENDED;
            case ENDED, CANCELLED -> false;
        };
    }
}
