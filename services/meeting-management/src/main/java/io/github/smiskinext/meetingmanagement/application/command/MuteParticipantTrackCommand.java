package io.github.smiskinext.meetingmanagement.application.command;

import java.util.UUID;

/**
 * Command issued by the host to mute a specific participant's track (microphone or camera).
 *
 * @param meetingId       the target meeting
 * @param requesterId     the host issuing the mute
 * @param targetIdentity  the LiveKit identity of the participant to mute
 * @param source          the track source type ({@code "microphone"} or {@code "camera"})
 */
public record MuteParticipantTrackCommand(
        UUID meetingId, UUID requesterId, String targetIdentity, String source) {}
