package io.github.smiskinext.usermanagement.application.response;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Output DTO for a user profile. Excludes security-sensitive fields
 * ({@code hashedPassword}, {@code googleUid}).
 */
public record UserResponse(
        UUID id,
        String email,
        String fullName,
        @Nullable String username,
        @Nullable String avatarUrl,
        String authProvider,
        UserPreferencesResponse preferences,
        Instant createdAt,
        Instant updatedAt) {}
