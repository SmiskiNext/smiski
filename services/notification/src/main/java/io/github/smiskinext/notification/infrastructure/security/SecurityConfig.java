package io.github.smiskinext.notification.infrastructure.security;

import jakarta.servlet.DispatcherType;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Stateless security configuration that permits all requests.
 *
 * <p>Authentication is handled upstream by the infrastructure API gateway, which is the trust
 * boundary. The notification service has no database and cannot verify host identity, so SSE
 * streams are scoped by meeting id only, matching the established trust-boundary model.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.dispatcherTypeMatchers(
                                DispatcherType.ASYNC, DispatcherType.ERROR)
                        .permitAll()
                        .anyRequest()
                        .permitAll())
                .build();
    }
}
