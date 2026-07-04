package io.github.smiskinext.meetingmanagement.application.response;

import io.github.smiskinext.meetingmanagement.domain.model.JoinRequestStatus;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Response for a join request submission.
 *
 * <p>For {@code ALLOW_ALL} policy: returns {@code APPROVED} status with token immediately.
 * For {@code MANUAL_APPROVAL} policy: returns {@code PENDING} status with requestId for polling.
 */
public record RequestJoinResponse(
        UUID requestId,
        JoinRequestStatus status,
        @Nullable String token,
        @Nullable String roomName) {}
