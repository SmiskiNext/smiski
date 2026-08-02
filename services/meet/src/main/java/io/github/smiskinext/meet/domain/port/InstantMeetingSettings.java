package io.github.smiskinext.meet.domain.port;

import java.time.Duration;

/**
 * Provides the configured default duration for instant meetings.
 */
public interface InstantMeetingSettings {

    Duration defaultDuration();
}
