package io.github.smiskinext.meet.application.service;

import io.github.smiskinext.meet.application.command.EndMeetingCommand;
import io.github.smiskinext.meet.application.mapper.EndedMeetingMapper;
import io.github.smiskinext.meet.application.result.EndMeetingResult;
import io.github.smiskinext.meet.application.usecase.EndMeetingUseCase;
import io.github.smiskinext.meet.domain.MeetingError;
import io.github.smiskinext.meet.domain.model.Meeting;
import io.github.smiskinext.meet.domain.model.MeetingStatus;
import io.github.smiskinext.meet.domain.model.valueobject.LiveKitRoomName;
import io.github.smiskinext.meet.domain.model.valueobject.MeetingId;
import io.github.smiskinext.meet.domain.port.LiveKitPort;
import io.github.smiskinext.meet.domain.port.MeetingRepository;
import io.github.smiskinext.shared.domain.Result;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ends a {@code RUNNING} meeting as its host, right now, instead of waiting for participants to
 * disconnect and LiveKit to fire the {@code room_finished} webhook.
 *
 * <p>The meeting is completed synchronously via {@link MeetingCompletionApplicationService} — the same logic
 * the webhook handler uses — so the host gets an immediate, consistent {@code COMPLETED} response.
 * Tearing down the LiveKit room is best-effort and dispatched after the transaction commits: the
 * persisted {@code COMPLETED} status is the durable source of truth, and a LiveKit room that
 * outlives it briefly is a cleanup concern, not a correctness one.
 */
@Service
public class EndMeetingApplicationService implements EndMeetingUseCase {

    private static final Logger log = LoggerFactory.getLogger(EndMeetingApplicationService.class);

    private final MeetingRepository meetingRepository;
    private final MeetingCompletionApplicationService meetingCompletionService;
    private final LiveKitPort liveKitPort;
    private final Executor taskExecutor;

    public EndMeetingApplicationService(
            MeetingRepository meetingRepository,
            MeetingCompletionApplicationService meetingCompletionService,
            LiveKitPort liveKitPort,
            @Qualifier("applicationTaskExecutor") Executor taskExecutor) {
        this.meetingRepository = meetingRepository;
        this.meetingCompletionService = meetingCompletionService;
        this.liveKitPort = liveKitPort;
        this.taskExecutor = taskExecutor;
    }

    @Override
    @Transactional
    public Result<EndMeetingResult, MeetingError> execute(EndMeetingCommand command) {
        Meeting meeting =
                meetingRepository.findActiveByIdWithLock(command.meetingId()).orElse(null);
        if (meeting == null) {
            return Result.failure(new MeetingError.MeetingNotFound(command.meetingId()));
        }

        if (!meeting.getHostId().value().equals(command.accountId())) {
            return Result.failure(new MeetingError.NotOwner(
                    command.accountId(), meeting.getHostId().value()));
        }

        if (meeting.getStatus() != MeetingStatus.RUNNING) {
            return Result.failure(new MeetingError.MeetingNotRunning(command.meetingId()));
        }

        Result<Void, MeetingError> completed =
                meetingCompletionService.completeAndCloseParticipation(meeting, Instant.now());
        if (completed.isFailure()) {
            return Result.failure(((Result.Failure<Void, MeetingError>) completed).error());
        }

        requestRoomTeardown(meeting.getId().value());
        return Result.success(EndedMeetingMapper.toResult(meeting));
    }

    private void requestRoomTeardown(UUID meetingId) {
        LiveKitRoomName roomName = LiveKitRoomName.fromMeetingId(MeetingId.of(meetingId));
        try {
            taskExecutor.execute(() -> {
                Result<Void, MeetingError> deleted = liveKitPort.deleteRoom(roomName);
                if (deleted.isFailure()) {
                    log.warn(
                            "Failed to tear down LiveKit room {} after host-initiated end: {}",
                            roomName.value(),
                            ((Result.Failure<Void, MeetingError>) deleted).error());
                }
            });
        } catch (RuntimeException e) {
            log.warn(
                    "Failed to dispatch LiveKit room teardown for {}: {}",
                    roomName.value(),
                    e.getMessage());
        }
    }
}
