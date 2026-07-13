package io.github.smiskinext.shared.infrastructure.tenancy;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tenancy configuration bound from the {@code app.tenancy} prefix.
 *
 * <p>The header carries the tenant identifier injected per service by the infrastructure API
 * gateway.
 */
@ConfigurationProperties(prefix = "app.tenancy")
public class TenancyProperties {

    /**
     * HTTP header carrying the tenant identifier. Default: {@code X-Tenant-ID}.
     */
    private String header = "X-Tenant-ID";

    /**
     * Tenant identifier used when no tenant is present in the request. Default: {@code system}.
     */
    private String defaultTenant = TenantContext.DEFAULT_TENANT;

    public String getHeader() {
        return header;
    }

    public void setHeader(String header) {
        this.header = header;
    }

    public String getDefaultTenant() {
        return defaultTenant;
    }

    public void setDefaultTenant(String defaultTenant) {
        this.defaultTenant = defaultTenant;
    }
}
