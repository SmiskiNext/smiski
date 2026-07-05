package io.github.smiskinext.meetingmanagement.application.usecase;

import io.github.smiskinext.meetingmanagement.application.command.DenyJoinRequestCommand;
import io.github.smiskinext.meetingmanagement.domain.MeetingError;
import io.github.smiskinext.meetingmanagement.domain.event.JoinRequestDeniedEvent;
import io.github.smiskinext.meetingmanagement.domain.model.JoinRequestResult;
import io.github.smiskinext.meetingmanagement.domain.model.JoinRequestStatus;
import io.github.smiskinext.meetingmanagement.domain.port.JoinRequestRepository;
import io.github.smiskinext.meetingmanagement.domain.port.JoinRequestResultStore;
import io.github.smiskinext.meetingmanagement.domain.port.MeetingRepository;
import io.github.smiskinext.shared.domain.Result;
import io.github.smiskinext.shared.domain.valueobject.UserId;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DenyJoinRequestUseCase {

    private final MeetingRepository meetingRepository;
    private final JoinRequestRepository joinRequestRepository;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final JoinRequestResultStore joinRequestResultStore;

    public DenyJoinRequestUseCase(
            MeetingRepository meetingRepository,
            JoinRequestRepository joinRequestRepository,
            ApplicationEventPublisher applicationEventPublisher,
            JoinRequestResultStore joinRequestResultStore) {
        this.meetingRepository = meetingRepository;
        this.joinRequestRepository = joinRequestRepository;
        this.applicationEventPublisher = applicationEventPublisher;
        this.joinRequestResultStore = joinRequestResultStore;
    }

    @Transactional
    public Result<Void, MeetingError> execute(DenyJoinRequestCommand command) {
        var meetingOpt = meetingRepository.findById(command.meetingId());
        if (meetingOpt.isEmpty()) {
            return Result.failure(new MeetingError.MeetingNotFound(command.meetingId()));
        }
        var meeting = meetingOpt.get();

        if (!meeting.getHostId().equals(UserId.of(command.deniedBy()))) {
            return Result.failure(new MeetingError.NotAuthorized(
                    command.deniedBy(), meeting.getHostId().value()));
        }

        var requestOpt = joinRequestRepository.findById(command.requestId());
        if (requestOpt.isEmpty()) {
            return Result.failure(
                    new MeetingError.JoinRequestNotFound(command.meetingId(), command.requestId()));
        }
        var joinRequest = requestOpt.get();

        var denyResult = joinRequest.deny();
        if (denyResult instanceof Result.Failure<?, MeetingError>(MeetingError error)) {
            return Result.failure(error);
        }

        joinRequestRepository.updateStatus(command.requestId(), JoinRequestStatus.DENIED);

        var deniedEvent = new JoinRequestDeniedEvent(
                UUID.randomUUID(),
                command.meetingId(),
                command.requestId(),
                command.deniedBy(),
                Instant.now());
        applicationEventPublisher.publishEvent(deniedEvent);

        joinRequestResultStore.save(JoinRequestResult.denied(command.requestId(), null));

        joinRequestRepository.removeFromQueue(command.meetingId(), command.requestId());

        return Result.success();
    }
}
