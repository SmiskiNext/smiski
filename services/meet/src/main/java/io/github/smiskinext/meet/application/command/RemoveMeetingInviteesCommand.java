package io.github.smiskinext.meet.application.command;

import io.github.smiskinext.shared.application.Command;

import java.util.List;
import java.util.UUID;

/**
 * Command to remove one or more invitees from a scheduled meeting by invitee id.
 */
public record RemoveMeetingInviteesCommand(
        UUID meetingId, String accountId, String tenantId, List<UUID> inviteeIds)
        implements Command {}
