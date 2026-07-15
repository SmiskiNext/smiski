package io.github.smiskinext.shared.infrastructure.identity;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * Auto-configuration that wires the account identity infrastructure: the request-scoped
 * {@link AccountFilter}. Activated automatically via Spring Boot SPI.
 *
 * <p>Only active when running in a SERVLET container (not Netty/WebFlux).
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = Type.SERVLET)
@EnableConfigurationProperties(AccountProperties.class)
public class AccountAutoConfiguration {

    @Bean
    public FilterRegistrationBean<AccountFilter> accountFilterRegistration(
            AccountProperties properties) {
        FilterRegistrationBean<AccountFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new AccountFilter(properties.getHeader()));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 21);
        return registration;
    }
}
