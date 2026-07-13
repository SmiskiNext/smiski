package io.github.smiskinext.tenant.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventJpaEntity, UUID> {

    @Query(
            "SELECT e FROM OutboxEventJpaEntity e WHERE e.publishedAt IS NULL ORDER BY e.createdAt ASC")
    List<OutboxEventJpaEntity> findUnpublishedOrderByCreatedAt();
}
