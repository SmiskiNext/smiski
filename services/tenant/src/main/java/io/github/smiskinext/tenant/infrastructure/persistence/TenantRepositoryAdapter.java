package io.github.smiskinext.tenant.infrastructure.persistence;

import io.github.smiskinext.tenant.domain.model.Tenant;
import io.github.smiskinext.tenant.domain.port.TenantRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class TenantRepositoryAdapter implements TenantRepository {

    private final TenantJpaRepository jpaRepository;

    public TenantRepositoryAdapter(TenantJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<Tenant> findById(String cloudId) {
        return jpaRepository.findById(cloudId).map(TenantPersistenceMapper::toDomain);
    }

    @Override
    public Tenant save(Tenant tenant) {
        Optional<TenantJpaEntity> existing = jpaRepository.findById(tenant.getCloudId());
        if (existing.isPresent()) {
            TenantJpaEntity entity = existing.get();
            TenantPersistenceMapper.updateEntity(entity, tenant);
            jpaRepository.save(entity);
        } else {
            jpaRepository.save(TenantPersistenceMapper.toEntity(tenant));
        }
        return tenant;
    }
}
