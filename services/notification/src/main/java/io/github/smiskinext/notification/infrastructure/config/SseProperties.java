package io.github.smiskinext.notification.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SSE connection tuning for host streams.
 *
 * <p>Bound from {@code app.sse.*}. Defaults keep the service safe in local development.
 */
@ConfigurationProperties(prefix = "app.sse")
public class SseProperties {

    /**
     * Host stream connection timeout in milliseconds after which the emitter completes. Default:
     * 300 000 ms (5 minutes).
     */
    private long hostStreamTimeoutMs = 300_000L;

    /**
     * Interval in seconds between heartbeat comments sent to keep idle streams open. Default: 15s.
     */
    private long heartbeatIntervalSeconds = 15L;

    public long getHostStreamTimeoutMs() {
        return hostStreamTimeoutMs;
    }

    public void setHostStreamTimeoutMs(long hostStreamTimeoutMs) {
        this.hostStreamTimeoutMs = hostStreamTimeoutMs;
    }

    public long getHeartbeatIntervalSeconds() {
        return heartbeatIntervalSeconds;
    }

    public void setHeartbeatIntervalSeconds(long heartbeatIntervalSeconds) {
        this.heartbeatIntervalSeconds = heartbeatIntervalSeconds;
    }
}
