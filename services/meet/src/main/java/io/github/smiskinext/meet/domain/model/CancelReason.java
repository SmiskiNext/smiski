package io.github.smiskinext.meet.domain.model;

/**
 * Reason a meeting was canceled.
 *
 * <p>{@code HOST_CANCELED} — the host explicitly canceled the scheduled meeting.
 * {@code NO_SHOW} — meeting canceled because nobody joined within the expected window.
 */
public enum CancelReason {
    HOST_CANCELED,
    NO_SHOW
}
