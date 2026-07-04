package io.github.smiskinext.meetingmanagement.application.usecase;

import io.github.smiskinext.meetingmanagement.application.command.CloseStaleMeetingLogsCommand;
import io.github.smiskinext.meetingmanagement.application.helper.ParticipationLogCloser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Entry point for bulk-closing all active participation logs for a meeting.
 *
 * <p>This is the safety-net for sessions whose {@code participant_left} webhook was never
 * delivered (e.g. LiveKit server crash, network drop, or rapid reconnect edge-cases).
 *
 * <p>Triggered by two paths:
 * <ol>
 *   <li>{@code room_finished} LiveKit webhook — LiveKit confirms the room is fully closed.</li>
 *   <li>{@link EndMeetingUseCase} — belt-and-suspenders call after {@code deleteRoom()}.</li>
 * </ol>
 *
 * <p>Idempotent: logs that already have {@code left_at} set are skipped by the repository query
 * ({@code WHERE left_at IS NULL}), so calling this multiple times is safe.
 *
 * <p>Actual close logic lives in {@link ParticipationLogCloser} so that
 * {@link EndMeetingUseCase} can also reuse it without creating a UseCase-to-UseCase dependency.
 */
@Service
public class CloseStaleMeetingLogsUseCase {

    private final ParticipationLogCloser participationLogCloser;

    public CloseStaleMeetingLogsUseCase(ParticipationLogCloser participationLogCloser) {
        this.participationLogCloser = participationLogCloser;
    }

    @Transactional
    public void execute(CloseStaleMeetingLogsCommand command) {
        participationLogCloser.closeAllActive(command.meetingId());
    }
}
