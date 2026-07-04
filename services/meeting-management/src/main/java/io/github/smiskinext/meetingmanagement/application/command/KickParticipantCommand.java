package io.github.smiskinext.meetingmanagement.application.command;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Command issued by the host to forcibly remove an active participant from a live meeting.
 *
 * <p>Exactly one of {@code userId} (registered participant) or {@code displayName} (guest) must
 * be provided.
 *
 * @param meetingId  the target meeting
 * @param requesterId the host issuing the kick
 * @param userId     the target registered participant; exclusive with {@code displayName}
 * @param displayName the target guest display name; exclusive with {@code userId}
 */
public record KickParticipantCommand(
        UUID meetingId,
        UUID requesterId,
        @Nullable UUID userId,
        @Nullable String displayName) {}
