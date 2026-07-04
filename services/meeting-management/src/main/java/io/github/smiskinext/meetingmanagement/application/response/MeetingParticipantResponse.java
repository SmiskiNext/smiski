package io.github.smiskinext.meetingmanagement.application.response;

import io.github.smiskinext.meetingmanagement.domain.model.ParticipantRole;
import io.github.smiskinext.meetingmanagement.domain.projection.ParticipantSummary;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record MeetingParticipantResponse(
        UUID meetingId,
        @Nullable UUID userId,
        String displayName,
        @Nullable String avatarUrl,
        ParticipantRole role,
        Instant joinedAt,
        @Nullable Instant leftAt) {

    public static MeetingParticipantResponse fromProjection(
            ParticipantSummary projection, @Nullable String avatarUrl) {
        return new MeetingParticipantResponse(
                projection.meetingId(),
                projection.userId(),
                projection.displayName(),
                avatarUrl,
                ParticipantRole.valueOf(projection.role()),
                projection.joinedAt(),
                projection.leftAt());
    }
}
