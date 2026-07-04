package io.github.smiskinext.meetingmanagement.application.command;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Command to request joining a meeting (with manual approval if required).
 */
public record RequestJoinCommand(
        UUID meetingId,
        @Nullable UUID userId,
        String displayName,
        String deviceId,
        @Nullable String password) {}
