package io.github.smiskinext.notification.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.smiskinext.notification.NotificationApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class NotificationPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(NotificationApplication.class)
            .withPropertyValues(
                    "spring.kafka.bootstrap-servers=localhost:9092",
                    "app.notification.resend.api-key=re_test_key",
                    "app.notification.resend.from-email=notifications@example.com",
                    "app.notification.resend.from-name=Zero Meeting System",
                    "app.notification.invitation.join-base-url=https://app.example.com/join");

    @Test
    void bindsConfiguredValuesAndUsesDefaultConsumerGroup() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            NotificationProperties properties = context.getBean(NotificationProperties.class);
            assertThat(properties.getResend().getApiKey()).isEqualTo("re_test_key");
            assertThat(properties.getResend().getFromEmail())
                    .isEqualTo("notifications@example.com");
            assertThat(properties.getResend().getFromName()).isEqualTo("Zero Meeting System");
            assertThat(properties.getInvitation().getJoinBaseUrl())
                    .isEqualTo("https://app.example.com/join");
            assertThat(properties.getKafka().getInvitationConsumerGroup())
                    .isEqualTo("notification-meeting-invitations");
        });
    }

    @Test
    void failsStartupWhenResendApiKeyIsMissing() {
        assertThatThrownBy(() -> new SpringApplicationBuilder(NotificationApplication.class)
                        .web(WebApplicationType.NONE)
                        .properties(
                                "spring.kafka.bootstrap-servers=localhost:9092",
                                "app.notification.resend.api-key=",
                                "app.notification.resend.from-email=notifications@example.com",
                                "app.notification.resend.from-name=Zero Meeting System",
                                "app.notification.invitation.join-base-url=https://app.example.com/join")
                        .run())
                .hasRootCauseInstanceOf(BindValidationException.class)
                .hasStackTraceContaining("resend.apiKey");
    }

    @Test
    void failsStartupWhenJoinBaseUrlIsMissing() {
        assertThatThrownBy(() -> new SpringApplicationBuilder(NotificationApplication.class)
                        .web(WebApplicationType.NONE)
                        .properties(
                                "spring.kafka.bootstrap-servers=localhost:9092",
                                "app.notification.resend.api-key=re_test_key",
                                "app.notification.resend.from-email=notifications@example.com",
                                "app.notification.resend.from-name=Zero Meeting System",
                                "app.notification.invitation.join-base-url=")
                        .run())
                .hasRootCauseInstanceOf(BindValidationException.class);
    }

    @Test
    void failsStartupWhenJoinBaseUrlContainsQueryParameters() {
        new ApplicationContextRunner()
                .withUserConfiguration(NotificationApplication.class)
                .withPropertyValues(
                        "spring.kafka.bootstrap-servers=localhost:9092",
                        "app.notification.resend.api-key=re_test_key",
                        "app.notification.resend.from-email=notifications@example.com",
                        "app.notification.resend.from-name=Zero Meeting System",
                        "app.notification.invitation.join-base-url=https://app.example.com/join?locale=en")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalArgumentException.class)
                            .hasStackTraceContaining("must not include query parameters");
                });
    }
}
