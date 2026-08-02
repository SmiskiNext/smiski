package io.github.smiskinext.notification.domain.port;

import io.github.smiskinext.notification.domain.model.TenantProjection;

import java.util.Optional;

/**
 * Outbound port for persisting and querying the local tenant projection.
 */
public interface TenantProjectionRepository {

    void upsert(TenantProjection projection);

    Optional<String> findSiteUrl(String tenantId);
}
