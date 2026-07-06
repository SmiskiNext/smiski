package io.github.smiskinext.meet.domain.port;

/**
 * Outbound port for reading dynamic meeting limit configuration.
 *
 * <p>Implemented by {@code MeetingLimitsConfig} in the infrastructure layer.
 */
public interface MeetingLimitsPort {

    /**
     * System-wide ceiling for maxParticipants.
     */
    int getMaxParticipantsCeiling();

    /**
     * Maximum allowed meeting duration in minutes.
     */
    int getMaxDurationMinutes();

    /**
     * Minimum allowed meeting duration in minutes.
     */
    int getMinDurationMinutes();
}
