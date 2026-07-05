package io.github.smiskinext.usermanagement.infrastructure.persistence;

import io.github.smiskinext.shared.domain.CursorPageResponse;
import io.github.smiskinext.shared.domain.ScrollCursor;
import io.github.smiskinext.shared.domain.valueobject.Email;
import io.github.smiskinext.shared.domain.valueobject.UserId;
import io.github.smiskinext.usermanagement.domain.model.User;
import io.github.smiskinext.usermanagement.domain.model.valueobject.FullName;
import io.github.smiskinext.usermanagement.domain.model.valueobject.HashedPassword;
import io.github.smiskinext.usermanagement.domain.model.valueobject.Username;
import io.github.smiskinext.usermanagement.domain.port.UserRepository;
import io.github.smiskinext.usermanagement.domain.port.UserScrollFilter;
import io.github.smiskinext.usermanagement.domain.projection.UserSummary;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepositoryAdapter implements UserRepository {

    private final UserJpaRepository jpa;

    public UserRepositoryAdapter(UserJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<User> findByEmail(Email email) {
        return jpa.findByEmail(email.value()).map(this::toDomain);
    }

    @Override
    public Optional<User> findById(UserId id) {
        return jpa.findById(id.value()).map(this::toDomain);
    }

    @Override
    public Optional<User> findActiveById(UserId id) {
        return jpa.findByIdAndDeletedAtIsNull(id.value()).map(this::toDomain);
    }

    @Override
    public Optional<User> findActiveByEmail(Email email) {
        return jpa.findByEmailAndDeletedAtIsNull(email.value()).map(this::toDomain);
    }

    @Override
    public Optional<User> findActiveByGoogleUid(String googleUid) {
        return jpa.findByGoogleUidAndDeletedAtIsNull(googleUid).map(this::toDomain);
    }

    @Override
    public User save(User user) {
        UserJpaEntity entity = toEntity(user);
        UserJpaEntity saved = jpa.save(entity);
        return toDomain(saved);
    }

    @Override
    public boolean existsByEmail(Email email) {
        return jpa.existsByEmail(email.value());
    }

    @Override
    public boolean existsActiveByEmail(Email email) {
        return jpa.existsByEmailAndDeletedAtIsNull(email.value());
    }

    @Override
    public boolean existsActiveByUsername(Username username) {
        return jpa.existsByUsernameAndDeletedAtIsNull(username.value());
    }

    @Override
    public Optional<User> findActiveByUsername(Username username) {
        return jpa.findByUsernameAndDeletedAtIsNull(username.value()).map(this::toDomain);
    }

    @Override
    public List<User> findActiveByEmails(Collection<String> emails) {
        if (emails.isEmpty()) return List.of();
        return jpa.findActiveByEmailIn(emails).stream().map(this::toDomain).toList();
    }

    @Override
    public CursorPageResponse<User> searchUsers(
            @Nullable ScrollCursor cursor, int size, UserScrollFilter filter) {
        int fetchLimit = size + 1;

        var cursorCreatedAt = cursor != null ? cursor.createdAt() : null;
        var cursorId = cursor != null ? cursor.id().toString() : null;

        var query = filter.hasQuery() ? escapeLike(filter.query()) : null;

        List<UserJpaEntity> rows =
                jpa.findActiveKeyset(cursorCreatedAt, cursorId, query, fetchLimit);

        boolean hasNext = rows.size() > size;
        List<User> items = rows.stream().limit(size).map(this::toDomain).toList();

        return CursorPageResponse.of(items, size, hasNext);
    }

    private String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    // ── Read-only projection methods ─────────────────────────────────────

    @Override
    public Optional<UserSummary> findSummaryActiveById(UserId id) {
        return jpa.findByIdAndDeletedAtIsNull(id.value()).map(this::toSummary);
    }

    @Override
    public List<UserSummary> findSummariesByEmails(Collection<String> emails) {
        if (emails.isEmpty()) return List.of();
        return jpa.findActiveByEmailIn(emails).stream().map(this::toSummary).toList();
    }

    @Override
    public List<UserSummary> findSummariesByIds(Collection<UserId> userIds) {
        if (userIds.isEmpty()) return List.of();
        List<UUID> ids = userIds.stream().map(UserId::value).toList();
        return jpa.findActiveByIdIn(ids).stream().map(this::toSummary).toList();
    }

    @Override
    public CursorPageResponse<UserSummary> searchSummaries(
            @Nullable ScrollCursor cursor, int size, UserScrollFilter filter) {
        int fetchLimit = size + 1;

        var cursorCreatedAt = cursor != null ? cursor.createdAt() : null;
        var cursorId = cursor != null ? cursor.id().toString() : null;

        var query = filter.hasQuery() ? escapeLike(filter.query()) : null;

        List<UserJpaEntity> rows =
                jpa.findActiveKeyset(cursorCreatedAt, cursorId, query, fetchLimit);

        boolean hasNext = rows.size() > size;
        List<UserSummary> items = rows.stream().limit(size).map(this::toSummary).toList();

        return CursorPageResponse.of(items, size, hasNext);
    }

    private UserSummary toSummary(UserJpaEntity e) {
        return new UserSummary(
                e.getId(),
                e.getEmail(),
                e.getFullName(),
                e.getUsername(),
                e.getAvatarUrl(),
                e.getAuthProvider(),
                e.getPreferences(),
                e.getCreatedAt(),
                e.getUpdatedAt());
    }

    private User toDomain(UserJpaEntity e) {
        String hash = e.getPasswordHash();
        return User.reconstitute(
                UserId.of(e.getId()),
                Email.of(e.getEmail()),
                hash != null ? HashedPassword.of(hash) : null,
                FullName.of(e.getFullName()),
                e.getUsername() != null ? Username.of(e.getUsername()) : null,
                e.getAvatarUrl(),
                e.getGoogleUid(),
                e.getAuthProvider(),
                e.getPreferences(),
                e.getCreatedAt(),
                e.getUpdatedAt(),
                e.getDeletedAt());
    }

    private UserJpaEntity toEntity(User u) {
        return new UserJpaEntity(
                u.getId().value(),
                u.getEmail().value(),
                u.getHashedPassword().map(HashedPassword::value).orElse(null),
                u.getFullName().value(),
                u.getUsername().map(Username::value).orElse(null),
                u.getAvatarUrl().orElse(null),
                u.getGoogleUid().orElse(null),
                u.getAuthProvider(),
                u.getPreferences().orElse(null),
                u.getCreatedAt(),
                u.getUpdatedAt(),
                u.getDeletedAt().orElse(null));
    }
}
