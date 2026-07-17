package io.github.smiskinext.meet.domain.model;

public enum MeetingStatus {
    SCHEDULED,
    RUNNING,
    COMPLETED,
    CANCELED;

    public boolean canTransitionTo(MeetingStatus target) {
        return switch (this) {
            case SCHEDULED -> target == RUNNING || target == CANCELED;
            case RUNNING -> target == COMPLETED;
            case COMPLETED, CANCELED -> false;
        };
    }
}
