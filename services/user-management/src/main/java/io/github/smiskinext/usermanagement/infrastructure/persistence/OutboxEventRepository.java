package io.github.smiskinext.usermanagement.infrastructure.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA repository for {@link OutboxEventJpaEntity}. */
public interface OutboxEventRepository extends JpaRepository<OutboxEventJpaEntity, Long> {

    /** Returns all outbox rows that have not yet been published to Kafka. */
    List<OutboxEventJpaEntity> findAllByPublishedAtIsNull();
}
