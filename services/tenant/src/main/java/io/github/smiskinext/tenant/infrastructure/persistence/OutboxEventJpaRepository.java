package io.github.smiskinext.tenant.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventJpaEntity, UUID> {

    @Query(
            "SELECT e FROM OutboxEventJpaEntity e WHERE e.publishedAt IS NULL ORDER BY e.createdAt ASC")
    List<OutboxEventJpaEntity> findUnpublishedOrderByCreatedAt();

    @Query(
            value =
                    "SELECT * FROM outbox_event WHERE published_at IS NULL ORDER BY created_at ASC LIMIT :batchSize FOR UPDATE SKIP LOCKED",
            nativeQuery = true)
    List<OutboxEventJpaEntity> claimBatch(@Param("batchSize") int batchSize);

    @Modifying
    @Query(
            value = "UPDATE outbox_event SET published_at = NOW() WHERE id IN :ids",
            nativeQuery = true)
    void markPublished(@Param("ids") List<UUID> ids);

    @Query(value = "SELECT * FROM outbox_event WHERE id = :id", nativeQuery = true)
    Optional<OutboxEventJpaEntity> findByIdAcrossTenants(@Param("id") UUID id);
}
