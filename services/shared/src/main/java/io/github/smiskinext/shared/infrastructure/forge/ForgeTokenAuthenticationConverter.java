package io.github.smiskinext.shared.infrastructure.forge;

import io.github.smiskinext.shared.infrastructure.tenancy.TenantContext;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Converts a verified Forge Invocation Token into a {@link JwtAuthenticationToken} and binds the
 * originating Atlassian {@code cloudId} to the {@link TenantContext} for the duration of the
 * request.
 *
 * <p>The {@code cloudId} lives in the nested {@code context} claim of the token. It identifies the
 * Atlassian site the request originates from and is used as the tenant discriminator by the
 * persistence layer.
 */
public class ForgeTokenAuthenticationConverter
        implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final String CONTEXT_CLAIM = "context";
    private static final String CLOUD_ID_CLAIM = "cloudId";

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        String cloudId = extractCloudId(jwt);
        if (cloudId != null && !cloudId.isBlank()) {
            TenantContext.setCurrentTenant(cloudId);
        }
        return new JwtAuthenticationToken(jwt, java.util.Collections.emptyList(), jwt.getSubject());
    }

    private @Nullable String extractCloudId(Jwt jwt) {
        Object context = jwt.getClaim(CONTEXT_CLAIM);
        if (context instanceof Map<?, ?> contextClaims) {
            Object cloudId = contextClaims.get(CLOUD_ID_CLAIM);
            return cloudId != null ? cloudId.toString() : null;
        }
        return null;
    }
}
