package io.github.smiskinext.shared.infrastructure.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Problem Details configuration bound from the {@code app.problem} prefix.
 *
 * <p>Externalizes the {@code type} URI namespace so the error-documentation host is not hardcoded
 * and can differ per deployment. When {@link #typeBaseUri} is blank, {@link ProblemDetailMapper}
 * emits the RFC 9457 default {@code type} of {@code about:blank}.
 */
@ConfigurationProperties(prefix = "app.problem")
public class ProblemProperties {

    /**
     * Base URI prepended to the kebab-cased error code to form the Problem {@code type} member
     * (e.g. {@code https://errors.example.com/} → {@code https://errors.example.com/not-found}).
     * Blank (the default) yields {@code about:blank}.
     */
    private String typeBaseUri = "";

    public String getTypeBaseUri() {
        return typeBaseUri;
    }

    public void setTypeBaseUri(String typeBaseUri) {
        this.typeBaseUri = typeBaseUri;
    }
}
