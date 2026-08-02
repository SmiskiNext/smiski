package io.github.smiskinext.notification.infrastructure.persistence;

import io.github.smiskinext.notification.infrastructure.persistence.model.TenantProjectionJpaEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JPA repository for the tenant projection table.
 */
public interface TenantProjectionJpaRepository
        extends JpaRepository<TenantProjectionJpaEntity, String> {

    @Query("select tenant.siteUrl from TenantProjectionJpaEntity tenant"
            + " where tenant.tenantId = :tenantId")
    Optional<String> findSiteUrlByTenantId(@Param("tenantId") String tenantId);
}
