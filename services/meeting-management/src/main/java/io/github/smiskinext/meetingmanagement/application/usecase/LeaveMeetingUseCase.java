package io.github.smiskinext.meetingmanagement.application.usecase;

import io.github.smiskinext.meetingmanagement.application.command.LeaveMeetingCommand;
import io.github.smiskinext.meetingmanagement.domain.MeetingError;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.LiveKitParticipantSid;
import io.github.smiskinext.meetingmanagement.domain.port.ParticipationLogRepository;
import io.github.phunguy65.zms.shared.domain.Result;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LeaveMeetingUseCase {

    private static final Logger log = LoggerFactory.getLogger(LeaveMeetingUseCase.class);

    private final ParticipationLogRepository participationLogRepository;

    public LeaveMeetingUseCase(ParticipationLogRepository participationLogRepository) {
        this.participationLogRepository = participationLogRepository;
    }

    /**
     * Records a participant's departure from a meeting.
     *
     * <p>Called exclusively from the LiveKit {@code participant_left} webhook. If the log is
     * not found (out-of-order event, or SID never assigned because {@code participant_joined}
     * was lost), the call is silently ignored with a warning — returning success so the
     * webhook controller always responds 200 OK to LiveKit.
     *
     * @param command the leave command
     */
    @Transactional
    public Result<Void, MeetingError> execute(LeaveMeetingCommand command) {
        var sid = LiveKitParticipantSid.of(command.livekitParticipantSid());
        var found = participationLogRepository.findActiveBySid(sid);

        if (found.isEmpty()) {
            log.warn(
                    "participant_left: no active participation log found for SID '{}' in"
                            + " meeting '{}' — out-of-order event or SID never assigned;"
                            + " ignoring",
                    command.livekitParticipantSid(),
                    command.meetingId());
            return Result.success();
        }

        var entry = found.get();
        entry.leave(Instant.now());
        participationLogRepository.save(entry);
        return Result.success();
    }
}
