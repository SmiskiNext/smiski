package io.github.smiskinext.meetingmanagement.application.response;

import io.github.smiskinext.meetingmanagement.domain.model.ParticipantRole;
import io.github.smiskinext.meetingmanagement.domain.projection.ParticipantSummary;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record ParticipantListItemResponse(
        Long id,
        UUID meetingId,
        @Nullable UUID userId,
        String displayName,
        @Nullable String avatarUrl,
        ParticipantRole role,
        Instant joinedAt,
        @Nullable Instant leftAt) {

    public static ParticipantListItemResponse fromProjection(
            ParticipantSummary projection, @Nullable String avatarUrl) {
        return new ParticipantListItemResponse(
                projection.id(),
                projection.meetingId(),
                projection.userId(),
                projection.displayName(),
                avatarUrl,
                ParticipantRole.valueOf(projection.role()),
                projection.joinedAt(),
                projection.leftAt());
    }
}
