package io.github.smiskinext.usermanagement.infrastructure.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "users")
public class UserJpaEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", length = 255)
    private @Nullable String passwordHash;

    @Column(name = "full_name", nullable = false, length = 255)
    private String fullName;

    @Column(length = 30)
    private @Nullable String username;

    @Column(name = "avatar_url", length = 2048)
    private @Nullable String avatarUrl;

    @Column(name = "google_uid", unique = true, length = 128)
    private @Nullable String googleUid;

    @Column(name = "auth_provider", nullable = false, length = 20)
    private String authProvider;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private @Nullable String preferences;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private @Nullable Instant deletedAt;

    protected UserJpaEntity() {}

    public UserJpaEntity(
            UUID id,
            String email,
            @Nullable String passwordHash,
            String fullName,
            @Nullable String username,
            @Nullable String avatarUrl,
            @Nullable String googleUid,
            String authProvider,
            @Nullable String preferences,
            Instant createdAt,
            Instant updatedAt,
            @Nullable Instant deletedAt) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.username = username;
        this.avatarUrl = avatarUrl;
        this.googleUid = googleUid;
        this.authProvider = authProvider;
        this.preferences = preferences;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.deletedAt = deletedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public @Nullable String getPasswordHash() {
        return passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public @Nullable String getUsername() {
        return username;
    }

    public @Nullable String getAvatarUrl() {
        return avatarUrl;
    }

    public @Nullable String getGoogleUid() {
        return googleUid;
    }

    public String getAuthProvider() {
        return authProvider;
    }

    public @Nullable String getPreferences() {
        return preferences;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public @Nullable Instant getDeletedAt() {
        return deletedAt;
    }
}
