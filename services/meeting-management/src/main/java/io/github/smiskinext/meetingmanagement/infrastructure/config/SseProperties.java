package io.github.smiskinext.meetingmanagement.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * SSE timeout configuration loaded from environment variables or application.yaml.
 *
 * <p>Properties are resolved at startup. Changes require service restart (use K8s rolling restart
 * for zero-downtime updates).
 *
 * <p>Default values ensure the service starts safely in local development.
 */
@Component
@ConfigurationProperties(prefix = "app.sse")
public class SseProperties {

    /**
     * General SSE connection timeout in milliseconds for host emitters. Default: 300 000 ms (5 min).
     */
    private long timeoutMs = 300_000L;

    /**
     * SSE connection timeout in milliseconds for guest join-request emitters. Default: 600 000 ms
     * (10 min).
     */
    private long joinRequestTimeoutMs = 600_000L;

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public long getJoinRequestTimeoutMs() {
        return joinRequestTimeoutMs;
    }

    public void setJoinRequestTimeoutMs(long joinRequestTimeoutMs) {
        this.joinRequestTimeoutMs = joinRequestTimeoutMs;
    }
}
