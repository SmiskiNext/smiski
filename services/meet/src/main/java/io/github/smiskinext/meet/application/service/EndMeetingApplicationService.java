package io.github.smiskinext.meet.application.service;

import io.github.smiskinext.meet.application.command.EndMeetingCommand;
import io.github.smiskinext.meet.application.mapper.EndedMeetingMapper;
import io.github.smiskinext.meet.application.result.EndMeetingResult;
import io.github.smiskinext.meet.application.usecase.EndMeetingUseCase;
import io.github.smiskinext.meet.domain.MeetingError;
import io.github.smiskinext.meet.domain.model.Meeting;
import io.github.smiskinext.meet.domain.model.ParticipationLog;
import io.github.smiskinext.meet.domain.model.valueobject.AccountId;
import io.github.smiskinext.meet.domain.model.valueobject.LiveKitRoomName;
import io.github.smiskinext.meet.domain.model.valueobject.MeetingId;
import io.github.smiskinext.meet.domain.port.LiveKitPort;
import io.github.smiskinext.meet.domain.port.MeetingRepository;
import io.github.smiskinext.meet.domain.port.ParticipationLogRepository;
import io.github.smiskinext.shared.domain.EventPublisher;
import io.github.smiskinext.shared.domain.Result;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class EndMeetingApplicationService implements EndMeetingUseCase {

    private static final Logger log = LoggerFactory.getLogger(EndMeetingApplicationService.class);

    private final MeetingRepository meetingRepository;
    private final ParticipationLogRepository participationLogRepository;
    private final EventPublisher eventPublisher;
    private final LiveKitPort liveKitPort;

    public EndMeetingApplicationService(
            MeetingRepository meetingRepository,
            ParticipationLogRepository participationLogRepository,
            EventPublisher eventPublisher,
            LiveKitPort liveKitPort) {
        this.meetingRepository = meetingRepository;
        this.participationLogRepository = participationLogRepository;
        this.eventPublisher = eventPublisher;
        this.liveKitPort = liveKitPort;
    }

    @Override
    public Result<EndMeetingResult, MeetingError> execute(EndMeetingCommand command) {
        Meeting meeting =
                meetingRepository.findActiveByIdWithLock(command.meetingId()).orElse(null);
        if (meeting == null) {
            return Result.failure(new MeetingError.MeetingNotFound(command.meetingId()));
        }

        AccountId actingAccount = AccountId.of(command.accountId());
        if (!meeting.getHostId().equals(actingAccount)) {
            return Result.failure(new MeetingError.NotAuthorized(
                    actingAccount.value(), meeting.getHostId().value()));
        }

        Result<Void, MeetingError> completeResult = meeting.complete();
        if (completeResult.isFailure()) {
            return Result.failure(((Result.Failure<Void, MeetingError>) completeResult).error());
        }

        Instant now = meeting.getEndTime().orElse(Instant.now());
        List<ParticipationLog> activeLogs =
                participationLogRepository.findActiveByMeetingId(command.meetingId());
        for (ParticipationLog session : activeLogs) {
            session.leave(now);
            participationLogRepository.save(session);
        }

        meetingRepository.save(meeting);
        eventPublisher.publishEventsOf(meeting);

        LiveKitRoomName roomName = LiveKitRoomName.fromMeetingId(MeetingId.of(command.meetingId()));
        try {
            Result<Void, MeetingError> deleteResult = liveKitPort.deleteRoom(roomName);
            if (deleteResult.isFailure()) {
                log.warn(
                        "Best-effort LiveKit room deletion failed for meeting={} room={}",
                        command.meetingId(),
                        roomName.value());
            }
        } catch (RuntimeException e) {
            log.warn(
                    "Best-effort LiveKit room deletion threw for meeting={} room={}",
                    command.meetingId(),
                    roomName.value(),
                    e);
        }

        return Result.success(EndedMeetingMapper.toResult(meeting));
    }
}
