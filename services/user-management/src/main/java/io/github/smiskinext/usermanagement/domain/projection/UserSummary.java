package io.github.smiskinext.usermanagement.domain.projection;

import java.time.Instant;
import java.util.UUID;

import io.github.smiskinext.usermanagement.domain.model.User;
import org.jspecify.annotations.Nullable;

/**
 * Read-only projection of a user profile.
 *
 * <p>Used exclusively by read (GET) use cases. Does NOT include security-sensitive
 * fields ({@code hashedPassword}, {@code googleUid}) and is never reconstituted
 * into a full {@link User} aggregate.
 *
 * <p>Constructed directly from persistence data — no value-object wrapping.
 */
public record UserSummary(
        UUID id,
        String email,
        String fullName,
        @Nullable String username,
        @Nullable String avatarUrl,
        String authProvider,
        @Nullable String preferences,
        Instant createdAt,
        Instant updatedAt) {}
