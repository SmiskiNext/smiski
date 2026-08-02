package io.github.smiskinext.notification.infrastructure.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Kafka consumer configuration for the tenant lifecycle consumers in the notification service.
 *
 * <p>Bound from {@code app.notification.kafka.*}. Fixed consumer groups ensure each event is
 * processed by exactly one replica; retry and dead-letter settings are inherited from
 * {@link EmailConsumerProperties} via the shared error handler.
 */
@ConfigurationProperties(prefix = "app.notification.kafka")
public class TenantKafkaProperties {

    /** Fixed group for the tenant-installed consumer. */
    private String tenantInstalledConsumerGroup = "notification-tenant-installed";

    /** Fixed group for the tenant-uninstalled consumer. */
    private String tenantUninstalledConsumerGroup = "notification-tenant-uninstalled";

    /** Total delivery attempts before a message is routed to the dead-letter topic. */
    private int retryAttempts = 3;

    /** Fixed backoff in milliseconds between delivery attempts. */
    private long retryBackoffMs = 1000L;

    /** Suffix appended to the source topic name to form its dead-letter topic. */
    private String deadLetterSuffix = ".dlt";

    public String getTenantInstalledConsumerGroup() {
        return tenantInstalledConsumerGroup;
    }

    public void setTenantInstalledConsumerGroup(String tenantInstalledConsumerGroup) {
        this.tenantInstalledConsumerGroup = tenantInstalledConsumerGroup;
    }

    public String getTenantUninstalledConsumerGroup() {
        return tenantUninstalledConsumerGroup;
    }

    public void setTenantUninstalledConsumerGroup(String tenantUninstalledConsumerGroup) {
        this.tenantUninstalledConsumerGroup = tenantUninstalledConsumerGroup;
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
