package io.github.smiskinext.notification.infrastructure.persistence;

import io.github.smiskinext.notification.domain.model.TenantProjection;
import io.github.smiskinext.notification.domain.port.TenantProjectionRepository;
import io.github.smiskinext.notification.infrastructure.persistence.model.TenantProjectionJpaEntity;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * JPA-backed adapter for the {@link TenantProjectionRepository} port.
 *
 * <p>Upserts the tenant projection row and exposes a targeted {@code site_url} lookup
 * so the email content builder can resolve Jira deep-links without loading the full entity.
 */
@Repository
public class TenantProjectionRepositoryAdapter implements TenantProjectionRepository {

    private final TenantProjectionJpaRepository jpaRepository;

    public TenantProjectionRepositoryAdapter(TenantProjectionJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void upsert(TenantProjection projection) {
        Optional<TenantProjectionJpaEntity> existing =
                jpaRepository.findById(projection.tenantId());
        if (existing.isPresent()) {
            TenantProjectionJpaEntity entity = existing.get();
            entity.setCloudId(projection.cloudId());
            entity.setSiteUrl(projection.siteUrl());
            entity.setStatus(projection.status().name());
            entity.setUpdatedAt(projection.updatedAt());
            entity.setUninstalledAt(projection.uninstalledAt());
            entity.setPurgeAfter(projection.purgeAfter());
            jpaRepository.save(entity);
        } else {
            jpaRepository.save(new TenantProjectionJpaEntity(
                    projection.tenantId(),
                    projection.cloudId(),
                    projection.siteUrl(),
                    projection.status().name(),
                    projection.updatedAt(),
                    projection.uninstalledAt(),
                    projection.purgeAfter()));
        }
    }

    @Override
    public Optional<String> findSiteUrl(String tenantId) {
        return jpaRepository.findSiteUrlByTenantId(tenantId);
    }
}
