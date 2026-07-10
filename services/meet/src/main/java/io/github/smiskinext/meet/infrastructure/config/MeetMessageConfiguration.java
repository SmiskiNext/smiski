package io.github.smiskinext.meet.infrastructure.config;

import io.github.smiskinext.shared.infrastructure.web.MessageBundleContributor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the meeting-management localized error bundle with the shared message source, so that
 * {@code MeetingError} {@code title}/{@code detail} text resolves per request locale.
 */
@Configuration
@ConditionalOnWebApplication(type = Type.SERVLET)
public class MeetMessageConfiguration {

    @Bean
    public MessageBundleContributor meetMessageBundle() {
        return () -> "classpath:messages/meet";
    }
}
