package io.github.smiskinext.meet.application.command;

import io.github.smiskinext.shared.application.Command;

import java.util.List;
import java.util.UUID;

/**
 * Command to replace the full invitee list of a scheduled meeting.
 */
public record UpdateMeetingInviteesCommand(
        UUID meetingId, String accountId, String tenantId, List<Invitee> invitees)
        implements Command {

    public record Invitee(String email, String accountId, String displayName) {}
}
