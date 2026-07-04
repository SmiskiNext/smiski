package io.github.smiskinext.notification.infrastructure.config;

import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "app.notification")
public class NotificationProperties {

    @Valid private Resend resend = new Resend();

    @Valid private Invitation invitation = new Invitation();

    @Valid private Kafka kafka = new Kafka();

    public Resend getResend() {
        return resend;
    }

    public void setResend(Resend resend) {
        this.resend = resend;
    }

    public Invitation getInvitation() {
        return invitation;
    }

    public void setInvitation(Invitation invitation) {
        this.invitation = invitation;
    }

    public Kafka getKafka() {
        return kafka;
    }

    public void setKafka(Kafka kafka) {
        this.kafka = kafka;
    }

    public void validateRequiredValues() {
        Assert.hasText(resend.apiKey, "app.notification.resend.api-key must not be blank");
        Assert.hasText(resend.fromEmail, "app.notification.resend.from-email must not be blank");
        Assert.hasText(resend.fromName, "app.notification.resend.from-name must not be blank");
        Assert.hasText(
                invitation.joinBaseUrl,
                "app.notification.invitation.join-base-url must not be blank");
        Assert.isTrue(
                !invitation.joinBaseUrl.contains("?"),
                "app.notification.invitation.join-base-url must not include query parameters");
        Assert.hasText(
                kafka.invitationConsumerGroup,
                "app.notification.kafka.invitation-consumer-group must not be blank");
        Assert.hasText(
                kafka.inviteInvalidatedConsumerGroup,
                "app.notification.kafka.invite-invalidated-consumer-group must not be blank");
        Assert.hasText(
                kafka.inviteeRespondedConsumerGroup,
                "app.notification.kafka.invitee-responded-consumer-group must not be blank");
    }

    @PostConstruct
    public void afterPropertiesSet() {
        validateRequiredValues();
    }

    public static class Resend {

        @NotBlank private String apiKey;

        @NotBlank private String fromEmail;

        @NotBlank private String fromName;

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getFromEmail() {
            return fromEmail;
        }

        public void setFromEmail(String fromEmail) {
            this.fromEmail = fromEmail;
        }

        public String getFromName() {
            return fromName;
        }

        public void setFromName(String fromName) {
            this.fromName = fromName;
        }
    }

    public static class Invitation {

        @NotBlank private String joinBaseUrl;

        public String getJoinBaseUrl() {
            return joinBaseUrl;
        }

        public void setJoinBaseUrl(String joinBaseUrl) {
            this.joinBaseUrl = joinBaseUrl;
        }
    }

    public static class Kafka {

        @NotBlank private String invitationConsumerGroup = "notification-meeting-invitations";

        @NotBlank private String inviteInvalidatedConsumerGroup = "notification-meeting-invite-invalidated";

        @NotBlank private String inviteeRespondedConsumerGroup = "notification-invitee-responded";

        public String getInvitationConsumerGroup() {
            return invitationConsumerGroup;
        }

        public void setInvitationConsumerGroup(String invitationConsumerGroup) {
            this.invitationConsumerGroup = invitationConsumerGroup;
        }

        public String getInviteInvalidatedConsumerGroup() {
            return inviteInvalidatedConsumerGroup;
        }

        public void setInviteInvalidatedConsumerGroup(String inviteInvalidatedConsumerGroup) {
            this.inviteInvalidatedConsumerGroup = inviteInvalidatedConsumerGroup;
        }

        public String getInviteeRespondedConsumerGroup() {
            return inviteeRespondedConsumerGroup;
        }

        public void setInviteeRespondedConsumerGroup(String inviteeRespondedConsumerGroup) {
            this.inviteeRespondedConsumerGroup = inviteeRespondedConsumerGroup;
        }
    }
}
