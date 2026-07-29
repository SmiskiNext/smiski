package io.github.smiskinext.meet.application.command;

import io.github.smiskinext.shared.application.Command;

import java.util.UUID;

/**
 * Command for an invitee to decline their own meeting invitation.
 */
public record DeclineMeetingInviteeCommand(
        UUID meetingId, UUID inviteeId, String accountId, String tenantId) implements Command {}
