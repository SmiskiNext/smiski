package io.github.smiskinext.usermanagement.infrastructure.persistence;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserJpaRepository extends JpaRepository<UserJpaEntity, UUID> {

    Optional<UserJpaEntity> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<UserJpaEntity> findByEmailAndDeletedAtIsNull(String email);

    boolean existsByEmailAndDeletedAtIsNull(String email);

    Optional<UserJpaEntity> findByIdAndDeletedAtIsNull(UUID id);

    Optional<UserJpaEntity> findByGoogleUidAndDeletedAtIsNull(String googleUid);

    boolean existsByUsernameAndDeletedAtIsNull(String username);

    Optional<UserJpaEntity> findByUsernameAndDeletedAtIsNull(String username);

    @Query("SELECT u FROM UserJpaEntity u WHERE u.email IN :emails AND u.deletedAt IS NULL")
    List<UserJpaEntity> findActiveByEmailIn(@Param("emails") Collection<String> emails);

    @Query("SELECT u FROM UserJpaEntity u WHERE u.id IN :ids AND u.deletedAt IS NULL")
    List<UserJpaEntity> findActiveByIdIn(@Param("ids") Collection<UUID> ids);

    /**
     * Keyset-scroll query for active users with optional cursor and optional ILIKE search.
     *
     * <p>Fetches {@code limit} rows ordered by {@code (created_at DESC, id DESC)}. The caller
     * should request {@code size + 1} rows to detect whether a next page exists.
     *
     * <p>Cursor clause: {@code (created_at, id) < (cursorCreatedAt, cursorId)} using row-value
     * comparison for correct keyset semantics with DESC ordering.
     *
     * <p>Search clause: when {@code query} is non-null, performs OR ILIKE match on both
     * {@code email} and {@code username}.
     */
    @Query(
            value = "SELECT * FROM users u WHERE u.deleted_at IS NULL "
                    + "AND (CAST(:cursorCreatedAt AS timestamptz) IS NULL OR (u.created_at, u.id) < (CAST(:cursorCreatedAt AS timestamptz), CAST(:cursorId AS uuid))) "
                    + "AND (CAST(:query AS text) IS NULL OR u.email ILIKE CONCAT('%', CAST(:query AS text), '%') OR u.username ILIKE CONCAT('%', CAST(:query AS text), '%')) "
                    + "ORDER BY u.created_at DESC, u.id DESC "
                    + "LIMIT :limit",
            nativeQuery = true)
    List<UserJpaEntity> findActiveKeyset(
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorId") String cursorId,
            @Param("query") String query,
            @Param("limit") int limit);
}
