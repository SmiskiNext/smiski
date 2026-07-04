package io.github.smiskinext.meetingmanagement.application.usecase;

import io.github.smiskinext.meetingmanagement.application.command.AssignSidCommand;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.LiveKitIdentity;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.LiveKitParticipantSid;
import io.github.smiskinext.meetingmanagement.domain.port.ParticipationLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assigns a LiveKit participant SID to an existing participation log.
 *
 * <p>Called by the {@code participant_joined} webhook handler once LiveKit confirms the
 * participant's media connection is established. Links the server-generated SID to the
 * pending log row created at token issuance time, enabling {@code participant_left}
 * webhook to look up the row by SID.
 *
 * <p>Idempotent: if the log already has a SID assigned (duplicate webhook delivery),
 * the operation is silently skipped.
 */
@Service
public class AssignSidUseCase {

    private static final Logger log = LoggerFactory.getLogger(AssignSidUseCase.class);

    private final ParticipationLogRepository participationLogRepository;

    public AssignSidUseCase(ParticipationLogRepository participationLogRepository) {
        this.participationLogRepository = participationLogRepository;
    }

    @Transactional
    public void execute(AssignSidCommand command) {
        var identity = LiveKitIdentity.of(command.livekitIdentity());
        var sid = LiveKitParticipantSid.of(command.livekitParticipantSid());

        var logOpt = participationLogRepository.findActiveByMeetingIdAndIdentity(
                command.meetingId(), identity);

        if (logOpt.isEmpty()) {
            log.warn(
                    "participant_joined: no pending participation log found for identity '{}' in"
                            + " meeting '{}' — token may have expired or log was not created",
                    command.livekitIdentity(),
                    command.meetingId());
            return;
        }

        var entry = logOpt.get();

        if (entry.getLivekitParticipantSid().isPresent()) {
            log.warn(
                    "participant_joined: SID already assigned for identity '{}' in meeting '{}'"
                            + " (duplicate webhook?) — skipping",
                    command.livekitIdentity(),
                    command.meetingId());
            return;
        }

        entry.assignSid(sid);
        participationLogRepository.save(entry);
        log.debug(
                "Assigned SID '{}' to participation log for identity '{}' in meeting '{}'",
                command.livekitParticipantSid(),
                command.livekitIdentity(),
                command.meetingId());
    }
}
