package io.github.smiskinext.meetingmanagement.infrastructure.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventJpaEntity, Long> {

    @Query(
            "SELECT o FROM OutboxEventJpaEntity o WHERE o.publishedAt IS NULL ORDER BY o.createdAt ASC")
    List<OutboxEventJpaEntity> findAllUnpublished();
}
