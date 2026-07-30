package io.github.smiskinext.meet.application.service;

import io.github.smiskinext.meet.domain.MeetingError;
import io.github.smiskinext.meet.domain.model.Meeting;
import io.github.smiskinext.meet.domain.model.ParticipationLog;
import io.github.smiskinext.meet.domain.port.MeetingRepository;
import io.github.smiskinext.meet.domain.port.ParticipationLogRepository;
import io.github.smiskinext.shared.domain.EventPublisher;
import io.github.smiskinext.shared.domain.Result;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Shared "what happens when a meeting completes" logic, used by both the LiveKit {@code
 * room_finished} webhook handler and any host-initiated end-meeting flow.
 *
 * <p>Completes the aggregate, closes every active {@link ParticipationLog} for the meeting, and
 * persists both. Idempotent: if the meeting cannot transition to {@code COMPLETED} (e.g. it is
 * already completed), this is a no-op that returns the same failure {@code Meeting.complete()}
 * would have returned, and no participation logs are touched.
 */
@Service
public class MeetingCompletionApplicationService {

    private final MeetingRepository meetingRepository;
    private final ParticipationLogRepository participationLogRepository;
    private final EventPublisher eventPublisher;

    public MeetingCompletionApplicationService(
            MeetingRepository meetingRepository,
            ParticipationLogRepository participationLogRepository,
            EventPublisher eventPublisher) {
        this.meetingRepository = meetingRepository;
        this.participationLogRepository = participationLogRepository;
        this.eventPublisher = eventPublisher;
    }

    public Result<Void, MeetingError> completeAndCloseParticipation(
            Meeting meeting, Instant occurredAt) {
        Result<Void, MeetingError> completed = meeting.complete();
        if (completed.isFailure()) {
            return completed;
        }

        List<ParticipationLog> active =
                participationLogRepository.findActiveByMeetingId(meeting.getId().value());
        for (ParticipationLog session : active) {
            session.leave(occurredAt);
            participationLogRepository.save(session);
        }

        meetingRepository.save(meeting);
        eventPublisher.publishEventsOf(meeting);
        return Result.success();
    }
}
