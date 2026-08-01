package io.github.smiskinext.meet.infrastructure.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Kafka consumer tuning for the tenant lifecycle consumers.
 *
 * <p>Bound from {@code app.tenant.kafka.*}. Fixed consumer groups ensure each event is processed
 * by exactly one replica; retry and dead-letter settings bound failure handling.
 */
@ConfigurationProperties(prefix = "app.tenant.kafka")
public class TenantKafkaProperties {

    /** Fixed group for the tenant-installed consumer. */
    private String installedConsumerGroup = "meet-tenant-installed";

    /** Fixed group for the tenant-uninstalled consumer. */
    private String uninstalledConsumerGroup = "meet-tenant-uninstalled";

    /** Total delivery attempts before a message is routed to the dead-letter topic. */
    private int retryAttempts = 3;

    /** Fixed backoff in milliseconds between delivery attempts. */
    private long retryBackoffMs = 1000L;

    /** Suffix appended to the source topic name to form its dead-letter topic. */
    private String deadLetterSuffix = ".dlt";

    public String getInstalledConsumerGroup() {
        return installedConsumerGroup;
    }

    public void setInstalledConsumerGroup(String installedConsumerGroup) {
        this.installedConsumerGroup = installedConsumerGroup;
    }

    public String getUninstalledConsumerGroup() {
        return uninstalledConsumerGroup;
    }

    public void setUninstalledConsumerGroup(String uninstalledConsumerGroup) {
        this.uninstalledConsumerGroup = uninstalledConsumerGroup;
    }

    public int getRetryAttempts() {
        return retryAttempts;
    }

    public void setRetryAttempts(int retryAttempts) {
        this.retryAttempts = retryAttempts;
    }

    public long getRetryBackoffMs() {
        return retryBackoffMs;
    }

    public void setRetryBackoffMs(long retryBackoffMs) {
        this.retryBackoffMs = retryBackoffMs;
    }

    public String getDeadLetterSuffix() {
        return deadLetterSuffix;
    }

    public void setDeadLetterSuffix(String deadLetterSuffix) {
        this.deadLetterSuffix = deadLetterSuffix;
    }
}
