package io.github.smiskinext.meet.application.service;

import io.github.smiskinext.meet.application.command.BatchDeleteMeetingsCommand;
import io.github.smiskinext.meet.application.mapper.DeletedMeetingMapper;
import io.github.smiskinext.meet.application.result.BatchDeleteMeetingsResult;
import io.github.smiskinext.meet.application.result.DeleteMeetingResult;
import io.github.smiskinext.meet.application.usecase.BatchDeleteMeetingsUseCase;
import io.github.smiskinext.meet.domain.MeetingError;
import io.github.smiskinext.meet.domain.model.Meeting;
import io.github.smiskinext.meet.domain.model.valueobject.AccountId;
import io.github.smiskinext.meet.domain.port.MeetingRepository;
import io.github.smiskinext.shared.domain.EventPublisher;
import io.github.smiskinext.shared.domain.Result;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BatchDeleteMeetingsApplicationService implements BatchDeleteMeetingsUseCase {

    private final MeetingRepository meetingRepository;
    private final EventPublisher eventPublisher;

    public BatchDeleteMeetingsApplicationService(
            MeetingRepository meetingRepository, EventPublisher eventPublisher) {
        this.meetingRepository = meetingRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public Result<BatchDeleteMeetingsResult, MeetingError> execute(
            BatchDeleteMeetingsCommand command) {
        AccountId actingAccount = AccountId.of(command.accountId());
        List<Meeting> meetings = new ArrayList<>(command.meetingIds().size());

        for (UUID meetingId : command.meetingIds()) {
            Meeting meeting =
                    meetingRepository.findActiveByIdWithLock(meetingId).orElse(null);
            if (meeting == null) {
                return Result.failure(new MeetingError.MeetingNotFound(meetingId));
            }
            Result<Void, MeetingError> delete = meeting.delete(actingAccount);
            if (delete.isFailure()) {
                return Result.failure(((Result.Failure<Void, MeetingError>) delete).error());
            }
            meetings.add(meeting);
        }

        List<DeleteMeetingResult> snapshots = new ArrayList<>(meetings.size());
        for (Meeting meeting : meetings) {
            meetingRepository.save(meeting);
            eventPublisher.publishEventsOf(meeting);
            snapshots.add(DeletedMeetingMapper.toResult(meeting));
        }

        return Result.success(new BatchDeleteMeetingsResult(snapshots));
    }
}
