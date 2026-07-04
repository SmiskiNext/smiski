package io.github.smiskinext.meetingmanagement.infrastructure.config;

import io.github.smiskinext.meetingmanagement.domain.port.MeetingLimitsPort;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Meeting limits configuration loaded from environment variables or application.yaml.
 *
 * <p>Properties are resolved at startup. Changes require service restart (use K8s rolling restart
 * for zero-downtime updates).
 *
 * <p>Default values ensure the service starts safely in local development.
 */
@Component
@ConfigurationProperties(prefix = "meeting.limits")
public class MeetingLimitsConfig implements MeetingLimitsPort {

    /**
     * System-wide ceiling for maxParticipants. Default: 500.
     */
    private int maxParticipantsCeiling = 500;

    /**
     * Maximum allowed meeting duration in minutes. Default: 480 (8 hours).
     */
    private int maxDurationMinutes = 480;

    /**
     * Minimum allowed meeting duration in minutes. Default: 15.
     */
    private int minDurationMinutes = 15;

    public int getMaxParticipantsCeiling() {
        return maxParticipantsCeiling;
    }

    public void setMaxParticipantsCeiling(int maxParticipantsCeiling) {
        this.maxParticipantsCeiling = maxParticipantsCeiling;
    }

    public int getMaxDurationMinutes() {
        return maxDurationMinutes;
    }

    public void setMaxDurationMinutes(int maxDurationMinutes) {
        this.maxDurationMinutes = maxDurationMinutes;
    }

    public int getMinDurationMinutes() {
        return minDurationMinutes;
    }

    public void setMinDurationMinutes(int minDurationMinutes) {
        this.minDurationMinutes = minDurationMinutes;
    }
}
