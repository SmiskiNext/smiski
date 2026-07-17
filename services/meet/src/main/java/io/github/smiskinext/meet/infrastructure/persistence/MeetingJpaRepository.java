package io.github.smiskinext.meet.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MeetingJpaRepository extends JpaRepository<MeetingJpaEntity, UUID> {

    Optional<MeetingJpaEntity> findByShortCode(String shortCode);
}
