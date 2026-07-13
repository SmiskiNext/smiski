package io.github.smiskinext.shared.infrastructure.tenancy;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * Auto-configuration that wires the tenancy infrastructure: the Hibernate {@link
 * TenantIdentifierResolver} and the request-scoped {@link TenantFilter}. Activated automatically via
 * Spring Boot SPI — no explicit {@code @Import} needed in services.
 *
 * <p>Only active when running in a SERVLET container (not Netty/WebFlux).
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = Type.SERVLET)
@EnableConfigurationProperties(TenancyProperties.class)
public class TenantAutoConfiguration {

    @Bean
    public TenantIdentifierResolver tenantIdentifierResolver() {
        return new TenantIdentifierResolver();
    }

    @Bean
    public FilterRegistrationBean<TenantFilter> tenantFilterRegistration(
            TenancyProperties properties) {
        FilterRegistrationBean<TenantFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new TenantFilter(properties.getHeader()));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        return registration;
    }
}
