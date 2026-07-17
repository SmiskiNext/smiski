package io.github.smiskinext.meet.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParticipationLogJpaRepository
        extends JpaRepository<ParticipationLogJpaEntity, UUID> {}
