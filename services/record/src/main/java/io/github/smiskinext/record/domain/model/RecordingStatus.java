package io.github.smiskinext.record.domain.model;

public enum RecordingStatus {
    /**
     * Row created by StartRecordingUseCase; waiting for LiveKit egress_started webhook.
     */
    PENDING,
    /**
     * LiveKit egress is actively capturing (egress_started webhook received).
     */
    RECORDING,
    /**
     * Recording finished and file is available (egress_ended webhook received).
     */
    COMPLETED,
    FAILED;

    public boolean canTransitionTo(RecordingStatus target) {
        return switch (this) {
            case PENDING -> target == RECORDING || target == FAILED;
            case RECORDING -> target == COMPLETED || target == FAILED;
            case COMPLETED, FAILED -> false;
        };
    }
}
