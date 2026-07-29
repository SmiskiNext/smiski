package io.github.smiskinext.meet.application.command;

import io.github.smiskinext.shared.application.Command;

import java.util.UUID;

/**
 * Command for an invitee to tentatively respond to their own meeting invitation.
 */
public record TentativeMeetingInviteeCommand(
        UUID meetingId, UUID inviteeId, String accountId, String tenantId) implements Command {}
