package io.github.smiskinext.meet.infrastructure.security;

import io.github.smiskinext.meet.domain.MeetingErrorCode;
import io.github.smiskinext.shared.infrastructure.identity.PermissionFilter;
import io.github.smiskinext.shared.infrastructure.identity.PermissionProperties;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;

/**
 * Stateless security configuration that delegates authentication to the upstream API gateway and
 * enforces project-level permissions via {@code @PreAuthorize} method security.
 *
 * <p>Authentication is handled upstream by the infrastructure API gateway, which validates the
 * caller and injects the tenant identifier through the {@code X-Tenant-Id} request header and
 * project permissions through the {@code X-Project-Permissions} header. Services trust this
 * boundary and perform no token verification of their own.
 *
 * <p>{@link PermissionFilter} is added directly to the Spring Security filter chain immediately
 * after {@link SecurityContextHolderFilter} so that the populated {@link
 * org.springframework.security.core.context.SecurityContextHolder} is visible to the
 * {@code @PreAuthorize} AOP interceptor.
 *
 * <p>{@link EnableMethodSecurity} activates AOP-based enforcement of {@code @PreAuthorize}
 * annotations on controller methods. {@code AccessDeniedException} from failing pre-authorize
 * checks is handled by the configured {@code accessDeniedHandler} which returns a Problem Details
 * body as {@code application/json}, consistent with the service error contract.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String ACCESS_DENIED_BODY =
            "{\"type\":\"about:blank\",\"title\":\"Forbidden\",\"status\":403,"
                    + "\"detail\":\"Insufficient project permissions\","
                    + "\"code\":\"" + MeetingErrorCode.NOT_AUTHORIZED.code() + "\"}";

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http, PermissionProperties permissionProperties) throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterAfter(
                        new PermissionFilter(
                                permissionProperties.getHeader(),
                                permissionProperties.isRequireHeader()),
                        SecurityContextHolderFilter.class)
                .authorizeHttpRequests(auth -> auth.dispatcherTypeMatchers(
                                DispatcherType.ASYNC, DispatcherType.ERROR)
                        .permitAll()
                        .anyRequest()
                        .permitAll())
                .exceptionHandling(ex -> ex.accessDeniedHandler((request, response, denied) -> {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.getWriter().write(ACCESS_DENIED_BODY);
                        })
                        .authenticationEntryPoint((request, response, authEx) -> {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.getWriter().write(ACCESS_DENIED_BODY);
                        }))
                .build();
    }
}
