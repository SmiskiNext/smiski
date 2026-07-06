package io.github.smiskinext.shared.infrastructure.tenancy;

import java.util.Map;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;

/**
 * Hibernate tenant resolver that reports the current request tenant from {@link TenantContext}.
 *
 * <p>Placeholder resolver: it feeds the value Hibernate uses to populate {@code @TenantId} columns
 * on insert and to append {@code WHERE tenant_id = ?} to queries. The resolution always yields a
 * non-null value so Spring Boot AOT processing and startup (which run without an HTTP request) do
 * not fail.
 *
 * <p>It also implements {@link HibernatePropertiesCustomizer} to register itself under {@link
 * AvailableSettings#MULTI_TENANT_IDENTIFIER_RESOLVER} without any extra {@code
 * hibernate.multiTenancy} property.
 */
public class TenantIdentifierResolver
        implements CurrentTenantIdentifierResolver<String>, HibernatePropertiesCustomizer {

    @Override
    public String resolveCurrentTenantIdentifier() {
        return TenantContext.getCurrentTenant();
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return false;
    }

    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        hibernateProperties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, this);
    }
}
