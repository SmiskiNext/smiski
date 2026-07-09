package io.github.smiskinext.meet.domain.model;

/**
 * Reason a participation session row was closed (left_at set).
 *
 * <p>{@code LEFT} — participant left cleanly (participant_left webhook).
 * {@code SUPERSEDED} — an orphaned active session was force-closed when the same
 * identity rejoined (lost leave webhook). Analytics should down-weight superseded
 * sessions because the true leave time is unknown.
 */
public enum CloseReason {
    LEFT,
    SUPERSEDED
}
