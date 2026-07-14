package io.github.smiskinext.shared.infrastructure.web;

import io.swagger.v3.oas.models.OpenAPI;

import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration that registers the {@link ProblemDetailOpenApiCustomizer} so the shared
 * {@code ProblemDetail} schema and common error responses ({@code 405}/{@code 415}/{@code 500}) are
 * appended to every service's generated OpenAPI document.
 *
 * <p>Active only when the OpenAPI model is on the classpath and the application is a servlet web
 * application. Mirrors the conditional pattern of {@link OpenApiServerAutoConfiguration}.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = Type.SERVLET)
@ConditionalOnClass(OpenAPI.class)
public class OpenApiProblemDetailAutoConfiguration {

    @Bean
    public GlobalOpenApiCustomizer problemDetailOpenApiCustomizer() {
        return new ProblemDetailOpenApiCustomizer();
    }
}
