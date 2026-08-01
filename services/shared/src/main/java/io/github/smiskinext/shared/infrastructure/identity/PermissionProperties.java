package io.github.smiskinext.shared.infrastructure.identity;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Permission enforcement configuration bound from the {@code app.security.permissions} prefix.
 */
@ConfigurationProperties(prefix = "app.security.permissions")
public class PermissionProperties {

    /**
     * When {@code true}, requests without the permissions header are rejected with {@code 403}.
     * Default: {@code false} (dev-friendly: missing header binds an empty permission set).
     */
    private boolean requireHeader = false;

    /**
     * HTTP header carrying the comma-separated project permission keys.
     * Default: {@code X-Project-Permissions}.
     */
    private String header = "X-Project-Permissions";

    public boolean isRequireHeader() {
        return requireHeader;
    }

    public void setRequireHeader(boolean requireHeader) {
        this.requireHeader = requireHeader;
    }

    public String getHeader() {
        return header;
    }

    public void setHeader(String header) {
        this.header = header;
    }
}
