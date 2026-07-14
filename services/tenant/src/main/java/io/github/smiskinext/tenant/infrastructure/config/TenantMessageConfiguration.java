package io.github.smiskinext.tenant.infrastructure.config;

import io.github.smiskinext.shared.infrastructure.web.MessageBundleContributor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnWebApplication(type = Type.SERVLET)
public class TenantMessageConfiguration {

    @Bean
    public MessageBundleContributor tenantMessageBundle() {
        return () -> "classpath:messages/tenant";
    }
}
