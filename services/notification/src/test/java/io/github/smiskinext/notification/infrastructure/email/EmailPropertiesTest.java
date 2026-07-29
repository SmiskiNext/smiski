package io.github.smiskinext.notification.infrastructure.email;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class EmailPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(EmailPropertiesConfig.class);

    @Test
    void blankApiKeyFailsStartup() {
        runner.withPropertyValues(
                        "app.notification.email.api-key=",
                        "app.notification.email.sender=calendar@test.local")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void configuredApiKeyAndSenderBindSuccessfully() {
        runner.withPropertyValues(
                        "app.notification.email.api-key=resend-key",
                        "app.notification.email.sender=calendar@test.local")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    EmailProperties properties = context.getBean(EmailProperties.class);
                    assertThat(properties.getApiKey()).isEqualTo("resend-key");
                    assertThat(properties.getSender()).isEqualTo("calendar@test.local");
                });
    }

    @EnableConfigurationProperties(EmailProperties.class)
    static class EmailPropertiesConfig {}
}
