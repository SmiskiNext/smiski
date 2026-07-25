package io.github.smiskinext.meet.application.command;

import io.github.smiskinext.shared.application.Command;

/**
 * Command to join a meeting. The account and tenant are resolved from the request context, never
 * from the request body.
 */
public record RequestJoinCommand(
        String meetingId,
        String tenantId,
        String accountId,
        String displayName,
        String deviceId,
        String avatarUrl)
        implements Command {}
