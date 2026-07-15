package io.github.smiskinext.shared.infrastructure.web;

import io.swagger.v3.oas.models.OpenAPI;

import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration that registers the {@link ApiVersionParameterOpenApiCustomizer} so the {@code
 * {version}} path parameter is defined for every versioned operation in every service's generated
 * OpenAPI document.
 *
 * <p>Active only when the OpenAPI model is on the classpath and the application is a servlet web
 * application. Mirrors the conditional pattern of {@link OpenApiProblemDetailAutoConfiguration}.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = Type.SERVLET)
@ConditionalOnClass(OpenAPI.class)
public class OpenApiApiVersionAutoConfiguration {

    @Bean
    public GlobalOpenApiCustomizer apiVersionParameterOpenApiCustomizer() {
        return new ApiVersionParameterOpenApiCustomizer();
    }
}
