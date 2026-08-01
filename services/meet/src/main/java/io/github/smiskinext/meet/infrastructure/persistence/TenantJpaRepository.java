package io.github.smiskinext.meet.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantJpaRepository extends JpaRepository<TenantJpaEntity, String> {}
