package io.github.smiskinext.shared.infrastructure.web;

import org.jspecify.annotations.NonNull;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.context.annotation.Bean;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Auto-configuration that prepends the versioned API prefix {@code /api/{version}} to every
 * controller handler under the {@code io.github.smiskinext} base package.
 *
 * <p>Combined with {@code spring.mvc.apiversion.use.path-segment: 1}, the {@code {version}} URI
 * variable sits at path-segment index 1 (segment 0 is the literal {@code api}), so the built-in
 * {@code ApiVersionResolver} extracts the integer version from requests such as {@code
 * /api/1/meetings/{id}}. Controllers therefore declare only the resource path at method level and
 * never repeat the {@code /api/{version}} prefix.
 *
 * <p>Scoping the prefix to the application base package leaves framework endpoints — Actuator and
 * springdoc ({@code /v3/api-docs}) — unprefixed at their conventional paths. Active exclusively in a
 * SERVLET web application.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = Type.SERVLET)
@ConditionalOnClass(WebMvcConfigurer.class)
public class ApiPathPrefixAutoConfiguration {

    private static final String VERSIONED_API_PREFIX = "/api/{version}";
    private static final String APPLICATION_BASE_PACKAGE = "io.github.smiskinext";

    @Bean
    public WebMvcConfigurer versionedApiPathPrefixConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void configurePathMatch(@NonNull PathMatchConfigurer configurer) {
                configurer.addPathPrefix(
                        VERSIONED_API_PREFIX,
                        HandlerTypePredicate.forBasePackage(APPLICATION_BASE_PACKAGE));
            }
        };
    }
}
