package io.github.smiskinext.meet.application.service;

import io.github.smiskinext.meet.application.command.UpdateMeetingCommand;
import io.github.smiskinext.meet.application.result.UpdateMeetingResult;
import io.github.smiskinext.meet.application.usecase.UpdateMeetingUseCase;
import io.github.smiskinext.meet.domain.MeetingError;
import io.github.smiskinext.meet.domain.model.Meeting;
import io.github.smiskinext.meet.domain.model.valueobject.*;
import io.github.smiskinext.meet.domain.port.MeetingRepository;
import io.github.smiskinext.shared.domain.EventPublisher;
import io.github.smiskinext.shared.domain.Result;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateMeetingApplicationService implements UpdateMeetingUseCase {

    private final MeetingRepository meetingRepository;
    private final EventPublisher eventPublisher;

    public UpdateMeetingApplicationService(
            MeetingRepository meetingRepository, EventPublisher eventPublisher) {
        this.meetingRepository = meetingRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public Result<UpdateMeetingResult, MeetingError> execute(UpdateMeetingCommand command) {
        Meeting meeting =
                meetingRepository.findByIdWithLock(command.meetingId()).orElse(null);
        if (meeting == null) {
            return Result.failure(new MeetingError.MeetingNotFound(command.meetingId()));
        }

        try {
            MeetingTimeRange timeRange = command.timeRange() == null
                    ? null
                    : MeetingTimeRange.of(
                            command.timeRange().startTime(), command.timeRange().endTime());
            Result<Void, MeetingError> update = meeting.updateInfo(
                    AccountId.of(command.accountId()),
                    MeetingTitle.of(command.title()),
                    command.description(),
                    JiraIssueLink.of(
                            command.issueLink().issueId(),
                            command.issueLink().issueKey(),
                            command.issueLink().projectKey()),
                    MeetingTimeZone.of(command.zoneId()),
                    timeRange);
            if (update.isFailure()) {
                return Result.failure(((Result.Failure<Void, MeetingError>) update).error());
            }
            if (!meeting.getDomainEvents().isEmpty()) {
                meetingRepository.save(meeting);
                eventPublisher.publishEventsOf(meeting);
            }
            return Result.success(toResult(meeting));
        } catch (IllegalArgumentException | NullPointerException exception) {
            return Result.failure(new MeetingError.InvalidSettings(exception.getMessage()));
        }
    }

    private UpdateMeetingResult toResult(Meeting meeting) {
        JiraIssueLink link = meeting.getIssueLink();
        return new UpdateMeetingResult(
                meeting.getId().value(),
                meeting.getHostId().value(),
                meeting.getShortCode().value(),
                meeting.getType().name(),
                meeting.getStatus().name(),
                meeting.getTitle().value(),
                meeting.getDescription(),
                new UpdateMeetingResult.IssueLink(
                        link.issueId(), link.issueKey(), link.projectKey()),
                meeting.getTimeRange().map(MeetingTimeRange::start).orElse(null),
                meeting.getTimeRange().map(MeetingTimeRange::end).orElse(null),
                meeting.getTimeZone().value(),
                meeting.getOrganizerEmail().value(),
                meeting.getOrganizerDisplayName().value(),
                meeting.getCalendarUid(),
                meeting.getCalendarSequence(),
                meeting.getCreatedAt());
    }
}
