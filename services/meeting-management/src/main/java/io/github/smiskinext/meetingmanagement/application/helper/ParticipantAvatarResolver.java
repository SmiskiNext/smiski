package io.github.smiskinext.meetingmanagement.application.helper;

import io.github.smiskinext.meetingmanagement.domain.port.UserGrpcServicePort;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ParticipantAvatarResolver {

    private static final Logger log = LoggerFactory.getLogger(ParticipantAvatarResolver.class);

    private final UserGrpcServicePort userGrpcServicePort;

    public ParticipantAvatarResolver(UserGrpcServicePort userGrpcServicePort) {
        this.userGrpcServicePort = userGrpcServicePort;
    }

    public @Nullable String resolveAvatar(@Nullable UUID userId) {
        if (userId == null) return null;
        return resolveAvatars(List.of(userId)).get(userId);
    }

    public Map<UUID, String> resolveAvatars(Collection<UUID> userIds) {
        var uniqueIds =
                userIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (uniqueIds.isEmpty()) {
            return Map.of();
        }

        try {
            return userGrpcServicePort.batchGetUsersByIds(uniqueIds).values().stream()
                    .filter(user ->
                            user.avatarUrl() != null && !user.avatarUrl().isBlank())
                    .collect(Collectors.toMap(
                            UserGrpcServicePort.ResolvedUser::userId,
                            UserGrpcServicePort.ResolvedUser::avatarUrl,
                            (left, right) -> left));
        } catch (UserGrpcServicePort.UserServiceException e) {
            log.warn("Failed to resolve participant avatars: {}", e.getMessage());
            return Map.of();
        }
    }
}
