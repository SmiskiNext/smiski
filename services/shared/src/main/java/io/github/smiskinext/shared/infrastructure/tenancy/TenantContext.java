package io.github.smiskinext.shared.infrastructure.tenancy;

import org.jspecify.annotations.Nullable;

/**
 * ThreadLocal holder for the tenant identifier associated with the current request.
 *
 * <p>The tenant identifier is injected per service by the infrastructure API gateway and bound to
 * this context by {@link TenantFilter}. The context defaults to {@link #DEFAULT_TENANT} whenever no
 * tenant has been set.
 *
 * <p>The backing {@link ThreadLocal} is compatible with virtual threads (services enable {@code
 * spring.threads.virtual.enabled}); each virtual thread carries its own value and it is cleared per
 * request to avoid leakage across pooled carrier threads.
 */
public final class TenantContext {

    /**
     * Fallback tenant used when no tenant is present in the current context. Guarantees resolution
     * never returns {@code null}, which keeps Spring Boot AOT processing and startup healthy.
     */
    public static final String DEFAULT_TENANT = "system";

    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {}

    /**
     * Binds the given tenant identifier to the current thread. A {@code null} value removes any
     * previously bound tenant.
     *
     * @param tenantId the tenant identifier, or {@code null} to clear it
     */
    public static void setCurrentTenant(@Nullable String tenantId) {
        if (tenantId == null) {
            CURRENT_TENANT.remove();
        } else {
            CURRENT_TENANT.set(tenantId);
        }
    }

    /**
     * Returns the tenant identifier bound to the current thread, or {@link #DEFAULT_TENANT} when
     * none is bound.
     *
     * @return the current tenant identifier, never {@code null}
     */
    public static String getCurrentTenant() {
        String tenantId = CURRENT_TENANT.get();
        return tenantId != null ? tenantId : DEFAULT_TENANT;
    }

    /**
     * Removes the tenant identifier bound to the current thread.
     */
    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
