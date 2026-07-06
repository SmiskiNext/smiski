package io.github.smiskinext.shared.infrastructure.forge;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration bound from the {@code app.forge} prefix for validating Forge Invocation Tokens
 * (FIT) issued by Atlassian.
 *
 * <p>A FIT is a JWT signed by Atlassian and attached to every Forge Remote request. Services that
 * receive Forge traffic declare {@code app.forge.jwks-uri} to activate {@link
 * ForgeSecurityAutoConfiguration}, which wires a {@code JwtDecoder} that verifies the signature,
 * issuer, audience and expiry of the token.
 */
@ConfigurationProperties(prefix = "app.forge")
public class ForgeProperties {

    /**
     * Application identifier (ARI) declared in the Forge manifest. Validated against the {@code aud}
     * claim of the invocation token.
     */
    private String appId;

    /**
     * JWKS endpoint publishing the public keys used to verify the invocation token signature.
     * Presence of this value activates the Forge security auto-configuration.
     */
    private String jwksUri;

    /**
     * Forge environment type the app is deployed to (for example {@code DEVELOPMENT},
     * {@code STAGING} or {@code PRODUCTION}). Informational only.
     */
    private String environmentType;

    /**
     * Expected issuer of the invocation token. Defaults to the Forge invocation token issuer.
     */
    private String issuer = "forge/invocation-token";

    /**
     * Allowed clock skew, in seconds, when validating the token expiry. Compensates for clock drift
     * between the Atlassian signer and this service.
     */
    private long clockSkewSeconds = 10;

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getJwksUri() {
        return jwksUri;
    }

    public void setJwksUri(String jwksUri) {
        this.jwksUri = jwksUri;
    }

    public String getEnvironmentType() {
        return environmentType;
    }

    public void setEnvironmentType(String environmentType) {
        this.environmentType = environmentType;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public long getClockSkewSeconds() {
        return clockSkewSeconds;
    }

    public void setClockSkewSeconds(long clockSkewSeconds) {
        this.clockSkewSeconds = clockSkewSeconds;
    }
}
