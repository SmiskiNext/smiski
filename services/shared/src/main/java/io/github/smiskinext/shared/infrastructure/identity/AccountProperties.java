package io.github.smiskinext.shared.infrastructure.identity;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Account identity configuration bound from the {@code app.identity} prefix.
 */
@ConfigurationProperties(prefix = "app.identity")
public class AccountProperties {

    /**
     * HTTP header carrying the account identifier. Default: {@code X-Account-Id}.
     */
    private String header = "X-Account-Id";

    public String getHeader() {
        return header;
    }

    public void setHeader(String header) {
        this.header = header;
    }
}
