package io.github.smiskinext.meet.application.result;

/**
 * Per-item outcome of a host accept/decline decision.
 *
 * <p>{@code APPROVED} and {@code DENIED} are terminal successes; {@code FAILED} marks an item that
 * could not be processed (unknown, already terminal, expired, wrong meeting, or meeting full) while
 * leaving the rest of the batch unaffected.
 */
public enum JoinDecisionStatus {
    APPROVED,
    DENIED,
    FAILED
}
