package io.github.smiskinext.meet.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MeetingJpaRepository
        extends JpaRepository<MeetingJpaEntity, UUID>, JpaSpecificationExecutor<MeetingJpaEntity> {

    Optional<MeetingJpaEntity> findByShortCode(String shortCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select meeting from MeetingJpaEntity meeting where meeting.id = :id")
    Optional<MeetingJpaEntity> findByIdWithLock(@Param("id") UUID id);
}
