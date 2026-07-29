package io.github.smiskinext.meet.application.command;

import io.github.smiskinext.shared.application.Command;

import java.util.UUID;

/**
 * Command for an invitee to accept their own meeting invitation.
 */
public record AcceptMeetingInviteeCommand(
        UUID meetingId, UUID inviteeId, String accountId, String tenantId) implements Command {}
