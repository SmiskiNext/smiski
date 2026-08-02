package io.github.smiskinext.notification.infrastructure.email;

import io.github.smiskinext.shared.infrastructure.web.MessageBundleContributor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the notification service's email message bundle with the shared message source so that
 * email subjects and bodies are resolved from {@code messages/notification.properties}.
 */
@Configuration
@ConditionalOnWebApplication(type = Type.SERVLET)
public class EmailNotificationBundleContributor {

    @Bean
    public MessageBundleContributor notificationMessageBundle() {
        return () -> "classpath:messages/notification";
    }
}
