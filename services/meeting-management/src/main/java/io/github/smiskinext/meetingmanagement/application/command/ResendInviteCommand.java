package io.github.smiskinext.meetingmanagement.application.command;

import java.util.UUID;

/**
 * Command issued by the host to resend an invite to an existing invitee.
 *
 * <p>Revokes the existing pending token, generates a new one, updates the invitee record,
 * and publishes a re-invite event.
 *
 * @param meetingId   the target meeting
 * @param inviteeId   the invitee to resend to
 * @param requesterId the host issuing the resend
 */
public record ResendInviteCommand(UUID meetingId, UUID inviteeId, UUID requesterId) {}
