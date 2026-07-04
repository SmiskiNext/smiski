package io.github.smiskinext.meetingmanagement.application.helper;

import io.github.smiskinext.meetingmanagement.domain.port.ParticipationLogRepository;
import java.time.Instant;
import java.util.UUID;

import io.github.smiskinext.meetingmanagement.application.usecase.CloseStaleMeetingLogsUseCase;
import io.github.smiskinext.meetingmanagement.application.usecase.EndMeetingUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Bulk-closes all active participation logs for a meeting.
 *
 * <p>Shared helper used by two callers:
 * <ol>
 *   <li>{@link CloseStaleMeetingLogsUseCase}
 *       — invoked via the LiveKit {@code room_finished} webhook.</li>
 *   <li>{@link EndMeetingUseCase}
 *       — belt-and-suspenders call immediately after {@code deleteRoom()} to guarantee
 *       {@code countActive} returns 0 before async webhooks arrive.</li>
 * </ol>
 *
 * <p>Idempotent: rows that already have {@code left_at} set are excluded by the repository query
 * ({@code WHERE left_at IS NULL}), so multiple calls for the same meeting are safe.
 *
 * <p>Transaction: this helper carries no {@code @Transactional} annotation — it participates in
 * whatever transaction the caller established. Both callers are themselves {@code @Transactional},
 * so this method always runs inside an active transaction.
 */
@Service
public class ParticipationLogCloser {

    private static final Logger log = LoggerFactory.getLogger(ParticipationLogCloser.class);

    private final ParticipationLogRepository participationLogRepository;

    public ParticipationLogCloser(ParticipationLogRepository participationLogRepository) {
        this.participationLogRepository = participationLogRepository;
    }

    /**
     * Sets {@code left_at = now} on every active participation log for {@code meetingId}.
     * Returns immediately if no active logs exist.
     *
     * @param meetingId the meeting whose open sessions should be closed
     */
    public void closeAllActive(UUID meetingId) {
        var activeLogs = participationLogRepository.findActiveByMeetingId(meetingId);

        if (activeLogs.isEmpty()) {
            return;
        }

        Instant now = Instant.now();
        for (var entry : activeLogs) {
            entry.leave(now);
            participationLogRepository.save(entry);
        }

        log.info(
                "Closed {} stale participation log(s) for meeting '{}'",
                activeLogs.size(),
                meetingId);
    }
}
