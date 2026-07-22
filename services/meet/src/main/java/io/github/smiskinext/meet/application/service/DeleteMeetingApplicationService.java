package io.github.smiskinext.meet.application.service;

import io.github.smiskinext.meet.application.command.DeleteMeetingCommand;
import io.github.smiskinext.meet.application.mapper.DeletedMeetingMapper;
import io.github.smiskinext.meet.application.result.DeleteMeetingResult;
import io.github.smiskinext.meet.application.usecase.DeleteMeetingUseCase;
import io.github.smiskinext.meet.domain.MeetingError;
import io.github.smiskinext.meet.domain.model.Meeting;
import io.github.smiskinext.meet.domain.model.valueobject.AccountId;
import io.github.smiskinext.meet.domain.port.MeetingRepository;
import io.github.smiskinext.shared.domain.EventPublisher;
import io.github.smiskinext.shared.domain.Result;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeleteMeetingApplicationService implements DeleteMeetingUseCase {

    private final MeetingRepository meetingRepository;
    private final EventPublisher eventPublisher;

    public DeleteMeetingApplicationService(
            MeetingRepository meetingRepository, EventPublisher eventPublisher) {
        this.meetingRepository = meetingRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public Result<DeleteMeetingResult, MeetingError> execute(DeleteMeetingCommand command) {
        Meeting meeting =
                meetingRepository.findActiveByIdWithLock(command.meetingId()).orElse(null);
        if (meeting == null) {
            return Result.failure(new MeetingError.MeetingNotFound(command.meetingId()));
        }

        Result<Void, MeetingError> delete = meeting.delete(AccountId.of(command.accountId()));
        if (delete.isFailure()) {
            return Result.failure(((Result.Failure<Void, MeetingError>) delete).error());
        }

        meetingRepository.save(meeting);
        eventPublisher.publishEventsOf(meeting);
        return Result.success(DeletedMeetingMapper.toResult(meeting));
    }
}
