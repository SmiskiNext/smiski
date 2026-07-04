package io.github.smiskinext.meetingmanagement.application.command;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Command issued by the host to add a new invitee to an existing meeting.
 *
 * @param meetingId    the target meeting
 * @param email        the invitee's email address
 * @param displayName  optional display name for the invitee
 * @param requesterId  the host issuing the command
 */
public record AddInviteeCommand(
        UUID meetingId, String email, @Nullable String displayName, UUID requesterId) {}
