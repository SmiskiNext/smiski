package io.github.smiskinext.meetingmanagement.domain.port;

import io.github.smiskinext.meetingmanagement.domain.MeetingError;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Outbound port for resolving users from the user-management service.
 *
 * <p>Implemented by the gRPC adapter in the infrastructure layer.
 */
public interface UserGrpcServicePort {

    /**
     * Resolves users by email.
     *
     * <p>Returns a map of email → {@link ResolvedUser} for all emails that matched an active user.
     * Missing emails are absent from the map.
     *
     * @param emails email addresses to resolve (may be empty)
     * @return map of email → resolved user
     * @throws UserServiceException if the user service is unreachable or times out
     */
    Map<String, ResolvedUser> resolveUsers(List<String> emails);

    /**
     * Resolves users by ID.
     *
     * <p>Returns a map of userId → {@link ResolvedUser} for all IDs that matched an active user.
     * Missing IDs are absent from the map.
     *
     * @param userIds user IDs to resolve (may be empty)
     * @return map of userId → resolved user
     * @throws UserServiceException if the user service is unreachable or times out
     */
    Map<UUID, ResolvedUser> batchGetUsersByIds(List<UUID> userIds);

    /**
     * Unchecked exception thrown when the user service is unavailable.
     * Carries the typed domain error for use case handling.
     */
    class UserServiceException extends RuntimeException {
        private final MeetingError.UserServiceUnavailable error;

        public UserServiceException(MeetingError.UserServiceUnavailable error) {
            super(error.message());
            this.error = error;
        }

        public MeetingError.UserServiceUnavailable getError() {
            return error;
        }
    }

    /**
     * A resolved user snapshot captured at invite time.
     *
     * @param userId       the user's UUID
     * @param email        the user's email address
     * @param displayName  the user's full name
     * @param username     the user's username (nullable)
     * @param avatarUrl    the user's avatar URL (nullable)
     * @param authProvider the auth provider string ("EMAIL", "GOOGLE", "BOTH")
     */
    record ResolvedUser(
            UUID userId,
            String email,
            String displayName,
            @Nullable String username,
            @Nullable String avatarUrl,
            String authProvider) {}
}
