package io.github.smiskinext.notification.infrastructure.config;

import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS configuration for the browser-direct SSE event streams.
 *
 * <p>The meeting host stream ({@code /meetings/{id}/events}) and the participant join-request
 * decision stream ({@code /meetings/{id}/join-requests/{requestId}/events}) are fetched directly
 * by the browser from a Forge Custom UI iframe hosted on an Atlassian domain. Browser-direct
 * fetches are mandatory for these routes: Forge Remote buffers response bodies, which defeats
 * Server-Sent Events, so the streams cannot travel through it and the browser enforces CORS.
 *
 * <p>Both routes are unauthenticated by design — the gateway bypasses authentication for them and
 * no tenant identity is required — therefore credentials are disabled and every origin is allowed
 * via origin patterns.
 *
 * <p>The path patterns include the versioned API prefix: the shared {@code
 * ApiPathPrefixAutoConfiguration} bakes {@code /api/{version}} into the registered handler
 * mappings, and CORS registrations match against the full request path including that prefix.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private static final String MEETING_EVENTS_PATTERN = "/api/*/meetings/*/events";
    private static final String JOIN_REQUEST_EVENTS_PATTERN =
            "/api/*/meetings/*/join-requests/*/events";

    @Override
    public void addCorsMappings(@NonNull CorsRegistry registry) {
        registry.addMapping(MEETING_EVENTS_PATTERN)
                .allowedOriginPatterns("*")
                .allowedMethods(HttpMethod.GET.name())
                .allowedHeaders("*")
                .allowCredentials(false);
        registry.addMapping(JOIN_REQUEST_EVENTS_PATTERN)
                .allowedOriginPatterns("*")
                .allowedMethods(HttpMethod.GET.name())
                .allowedHeaders("*")
                .allowCredentials(false);
    }
}
