package io.github.smiskinext.meet.application.command;

import io.github.smiskinext.shared.application.Command;

import java.util.List;
import java.util.UUID;

/**
 * Command for a host to accept one or more pending join requests. The account and tenant are
 * resolved from the request context, never from the request body.
 */
public record AcceptJoinRequestsCommand(
        UUID meetingId, String tenantId, String accountId, List<UUID> requestIds)
        implements Command {}
