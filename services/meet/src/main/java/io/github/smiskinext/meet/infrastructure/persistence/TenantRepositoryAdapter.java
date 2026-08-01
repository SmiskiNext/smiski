package io.github.smiskinext.meet.infrastructure.persistence;

import io.github.smiskinext.meet.domain.model.TenantRecord;
import io.github.smiskinext.meet.domain.port.TenantRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * JPA-backed adapter for the {@link TenantRepository} port.
 *
 * <p>Upserts the tenant projection row: updates mutable columns when the tenant already exists,
 * inserts a new row otherwise.
 */
@Repository
public class TenantRepositoryAdapter implements TenantRepository {

    private final TenantJpaRepository tenantJpaRepository;

    public TenantRepositoryAdapter(TenantJpaRepository tenantJpaRepository) {
        this.tenantJpaRepository = tenantJpaRepository;
    }

    @Override
    public void upsert(TenantRecord record) {
        Optional<TenantJpaEntity> existing = tenantJpaRepository.findById(record.tenantId());
        if (existing.isPresent()) {
            TenantJpaEntity entity = existing.get();
            entity.setCloudId(record.cloudId());
            entity.setStatus(record.status().name());
            entity.setUpdatedAt(record.updatedAt());
            entity.setUninstalledAt(record.uninstalledAt());
            entity.setPurgeAfter(record.purgeAfter());
            tenantJpaRepository.save(entity);
        } else {
            tenantJpaRepository.save(new TenantJpaEntity(
                    record.tenantId(),
                    record.cloudId(),
                    record.status().name(),
                    record.updatedAt(),
                    record.uninstalledAt(),
                    record.purgeAfter()));
        }
    }
}
