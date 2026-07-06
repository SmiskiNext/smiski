package io.github.smiskinext.shared.infrastructure.forge;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Auto-configuration that validates Forge Invocation Tokens (FIT) using Spring Security's OAuth2
 * Resource Server support.
 *
 * <p>Exposes a {@link JwtDecoder} that verifies the token signature against the Forge JWKS endpoint
 * and validates the issuer, audience and expiry, plus a {@link ForgeTokenAuthenticationConverter}
 * that binds the originating {@code cloudId} to the tenant context. Services opt in by declaring
 * {@code app.forge.jwks-uri} and wire these beans into their own {@code SecurityFilterChain}, which
 * keeps route-specific authorization rules under each service's control.
 *
 * <p>Only active in a SERVLET web application when the OAuth2 Resource Server support is on the
 * classpath.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = Type.SERVLET)
@ConditionalOnClass(NimbusJwtDecoder.class)
@ConditionalOnProperty(prefix = "app.forge", name = "jwks-uri")
@EnableConfigurationProperties(ForgeProperties.class)
public class ForgeSecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public JwtDecoder forgeJwtDecoder(ForgeProperties properties) {
        NimbusJwtDecoder decoder =
                NimbusJwtDecoder.withJwkSetUri(properties.getJwksUri()).build();
        decoder.setJwtValidator(forgeTokenValidator(properties));
        return decoder;
    }

    @Bean
    @ConditionalOnMissingBean
    public ForgeTokenAuthenticationConverter forgeTokenAuthenticationConverter() {
        return new ForgeTokenAuthenticationConverter();
    }

    private OAuth2TokenValidator<Jwt> forgeTokenValidator(ForgeProperties properties) {
        return new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(Duration.ofSeconds(properties.getClockSkewSeconds())),
                new JwtIssuerValidator(properties.getIssuer()),
                audienceValidator(properties.getAppId()));
    }

    private JwtClaimValidator<List<String>> audienceValidator(String appId) {
        return new JwtClaimValidator<>(
                JwtClaimNames.AUD, audience -> audience != null && audience.contains(appId));
    }
}
