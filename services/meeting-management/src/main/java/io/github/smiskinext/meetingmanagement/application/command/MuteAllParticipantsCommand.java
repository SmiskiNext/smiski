package io.github.smiskinext.meetingmanagement.application.command;

import java.util.UUID;

/**
 * Command issued by the host to mute all active participant microphones in a meeting.
 *
 * @param meetingId   the target meeting
 * @param requesterId the host issuing the mute-all
 */
public record MuteAllParticipantsCommand(UUID meetingId, UUID requesterId) {}
