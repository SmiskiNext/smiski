package io.github.smiskinext.notification.infrastructure.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Email consumer tuning for the calendar-mail Kafka listeners.
 *
 * <p>Bound from {@code app.notification.kafka.*}. The fixed consumer groups ensure each event is
 * emailed by exactly one replica; the retry and dead-letter settings bound send-failure handling.
 */
@ConfigurationProperties(prefix = "app.notification.kafka")
public class EmailConsumerProperties {

    /** Fixed group for the invitations-created invite emails. */
    private String invitationConsumerGroup = "notification-meeting-invitations";

    /** Fixed group for the invitee-response reply emails. */
    private String inviteeRespondedConsumerGroup = "notification-invitee-responded";

    /** Total delivery attempts before a message is routed to the dead-letter topic. */
    private int emailRetryAttempts = 3;

    /** Fixed backoff in milliseconds between delivery attempts. */
    private long emailRetryBackoffMs = 1000L;

    /** Suffix appended to the source topic to form its dead-letter topic. */
    private String emailDeadLetterSuffix = ".dlt";

    public String getInvitationConsumerGroup() {
        return invitationConsumerGroup;
    }

    public void setInvitationConsumerGroup(String invitationConsumerGroup) {
        this.invitationConsumerGroup = invitationConsumerGroup;
    }

    public String getInviteeRespondedConsumerGroup() {
        return inviteeRespondedConsumerGroup;
    }

    public void setInviteeRespondedConsumerGroup(String inviteeRespondedConsumerGroup) {
        this.inviteeRespondedConsumerGroup = inviteeRespondedConsumerGroup;
    }

    public int getEmailRetryAttempts() {
        return emailRetryAttempts;
    }

    public void setEmailRetryAttempts(int emailRetryAttempts) {
        this.emailRetryAttempts = emailRetryAttempts;
    }

    public long getEmailRetryBackoffMs() {
        return emailRetryBackoffMs;
    }

    public void setEmailRetryBackoffMs(long emailRetryBackoffMs) {
        this.emailRetryBackoffMs = emailRetryBackoffMs;
    }

    public String getEmailDeadLetterSuffix() {
        return emailDeadLetterSuffix;
    }

    public void setEmailDeadLetterSuffix(String emailDeadLetterSuffix) {
        this.emailDeadLetterSuffix = emailDeadLetterSuffix;
    }
}
