package io.github.smiskinext.meet.application.service;

import io.github.smiskinext.meet.application.command.CancelMeetingCommand;
import io.github.smiskinext.meet.application.mapper.CanceledMeetingMapper;
import io.github.smiskinext.meet.application.result.CancelMeetingResult;
import io.github.smiskinext.meet.application.usecase.CancelMeetingUseCase;
import io.github.smiskinext.meet.domain.MeetingError;
import io.github.smiskinext.meet.domain.event.MeetingCanceledEvent;
import io.github.smiskinext.meet.domain.model.CancelReason;
import io.github.smiskinext.meet.domain.model.Meeting;
import io.github.smiskinext.meet.domain.model.MeetingInvitee;
import io.github.smiskinext.meet.domain.model.valueobject.AccountId;
import io.github.smiskinext.meet.domain.port.MeetingInviteeRepository;
import io.github.smiskinext.meet.domain.port.MeetingRepository;
import io.github.smiskinext.shared.domain.EventPublisher;
import io.github.smiskinext.shared.domain.Result;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CancelMeetingApplicationService implements CancelMeetingUseCase {

    private final MeetingRepository meetingRepository;
    private final MeetingInviteeRepository meetingInviteeRepository;
    private final EventPublisher eventPublisher;

    public CancelMeetingApplicationService(
            MeetingRepository meetingRepository,
            MeetingInviteeRepository meetingInviteeRepository,
            EventPublisher eventPublisher) {
        this.meetingRepository = meetingRepository;
        this.meetingInviteeRepository = meetingInviteeRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Result<CancelMeetingResult, MeetingError> execute(CancelMeetingCommand command) {
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

        List<MeetingInvitee> allInvitees =
                meetingInviteeRepository.findByMeetingId(command.meetingId());
        List<MeetingCanceledEvent.InviteeInfo> inviteeInfos = toInviteeInfos(allInvitees);

        Result<Void, MeetingError> cancelResult = meeting.cancel(
                CancelReason.HOST_CANCELED,
                meeting.getTitle().value(),
                meeting.getShortCode().value(),
                meeting.getStartTime().orElse(null),
                inviteeInfos);

        if (cancelResult.isFailure()) {
            return Result.failure(((Result.Failure<Void, MeetingError>) cancelResult).error());
        }

        meetingRepository.save(meeting);
        eventPublisher.publishEventsOf(meeting);

        return Result.success(CanceledMeetingMapper.toResult(meeting));
    }

    private List<MeetingCanceledEvent.InviteeInfo> toInviteeInfos(List<MeetingInvitee> invitees) {
        List<MeetingCanceledEvent.InviteeInfo> infos = new ArrayList<>();
        for (MeetingInvitee invitee : invitees) {
            if (invitee.getRemovedAt().isEmpty()) {
                infos.add(new MeetingCanceledEvent.InviteeInfo(
                        invitee.getAccountId().value(),
                        invitee.getEmail().value(),
                        invitee.getDisplayName().value(),
                        invitee.getStatus().name(),
                        invitee.getInvitedAt()));
            }
        }
        return infos;
    }
}
