package io.github.smiskinext.tenant.infrastructure.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantJpaRepository extends JpaRepository<TenantJpaEntity, String> {

    Optional<TenantJpaEntity> findByTenantId(String tenantId);
}
