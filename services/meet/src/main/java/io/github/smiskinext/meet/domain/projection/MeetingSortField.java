package io.github.smiskinext.meet.domain.projection;

/**
 * Ordering modes supported by the tenant meeting listing.
 *
 * <p>Both modes order newest-first and use the meeting {@code id} (UUIDv7) as a total-order
 * tie-breaker.
 */
public enum MeetingSortField {

    /** Orders by meeting creation time. */
    CREATED_AT,

    /**
     * Orders by the effective start time, which is the scheduled {@code startTime} when present and
     * the creation time when absent (as for INSTANT meetings).
     */
    START_TIME
}
