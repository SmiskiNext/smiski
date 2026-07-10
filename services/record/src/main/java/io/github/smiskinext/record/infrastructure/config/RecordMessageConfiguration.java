package io.github.smiskinext.record.infrastructure.config;

import io.github.smiskinext.shared.infrastructure.web.MessageBundleContributor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the record context localized error bundle with the shared message source, so that
 * {@code RecordError} {@code title}/{@code detail} text resolves per request locale.
 */
@Configuration
@ConditionalOnWebApplication(type = Type.SERVLET)
public class RecordMessageConfiguration {

    @Bean
    public MessageBundleContributor recordMessageBundle() {
        return () -> "classpath:messages/record";
    }
}
