package io.github.smiskinext.meetingmanagement.application.command;

import java.util.UUID;

/**
 * Command for a participant to explicitly leave a meeting.
 *
 * <p>Note: In the webhook-driven flow, {@code participant_left} from LiveKit is the primary
 * path for recording departure. This command serves as a client-initiated fallback
 * (e.g., graceful disconnect via UI button).
 *
 * <p>{@code livekitParticipantSid} is the LiveKit session ID returned to the client
 * in the join response, used to identify the exact active session to close.
 */
public record LeaveMeetingCommand(UUID meetingId, String livekitParticipantSid) {}
