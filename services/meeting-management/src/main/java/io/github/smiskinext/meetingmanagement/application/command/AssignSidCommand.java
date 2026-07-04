package io.github.smiskinext.meetingmanagement.application.command;

import java.util.UUID;

/**
 * Command for assigning a LiveKit participant SID to an existing participation log.
 *
 * <p>Triggered by the {@code participant_joined} webhook from LiveKit, after the participant's
 * media connection is established. The SID is required to later match the
 * {@code participant_left} webhook back to the correct participation log row.
 */
public record AssignSidCommand(
        UUID meetingId, String livekitIdentity, String livekitParticipantSid) {}
