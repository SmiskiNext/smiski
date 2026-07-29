package io.github.smiskinext.meet.application.service;

import io.github.smiskinext.meet.application.command.TentativeMeetingInviteeCommand;
import io.github.smiskinext.meet.application.helper.InviteeResponseSupport;
import io.github.smiskinext.meet.application.result.TentativeMeetingInviteeResult;
import io.github.smiskinext.meet.application.usecase.TentativeMeetingInviteeUseCase;
import io.github.smiskinext.meet.domain.MeetingError;
import io.github.smiskinext.meet.domain.model.Meeting;
import io.github.smiskinext.meet.domain.model.MeetingInvitee;
import io.github.smiskinext.meet.domain.model.valueobject.AccountId;
import io.github.smiskinext.meet.domain.port.MeetingInviteeRepository;
import io.github.smiskinext.meet.domain.port.MeetingRepository;
import io.github.smiskinext.shared.domain.EventPublisher;
import io.github.smiskinext.shared.domain.Result;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tentatively responds to a meeting invitation on behalf of its owning invitee.
 *
 * <p>Loads the meeting and the target invitee, authorizes the acting account as the invitee owner,
 * applies the domain transition, and publishes the enriched tentative event through the outbox.
 */
@Service
public class TentativeMeetingInviteeApplicationService implements TentativeMeetingInviteeUseCase {

    private final MeetingRepository meetingRepository;
    private final MeetingInviteeRepository meetingInviteeRepository;
    private final EventPublisher eventPublisher;

    public TentativeMeetingInviteeApplicationService(
            MeetingRepository meetingRepository,
            MeetingInviteeRepository meetingInviteeRepository,
            EventPublisher eventPublisher) {
        this.meetingRepository = meetingRepository;
        this.meetingInviteeRepository = meetingInviteeRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public Result<TentativeMeetingInviteeResult, MeetingError> execute(
            TentativeMeetingInviteeCommand command) {
        Meeting meeting = meetingRepository.findById(command.meetingId()).orElse(null);
        if (meeting == null) {
            return Result.failure(new MeetingError.MeetingNotFound(command.meetingId()));
        }

        MeetingInvitee invitee = InviteeResponseSupport.loadInviteeOfMeeting(
                meetingInviteeRepository, command.meetingId(), command.inviteeId());
        if (invitee == null) {
            return Result.failure(
                    new MeetingError.InviteeNotFound(command.inviteeId().toString()));
        }
        if (!invitee.getAccountId().equals(AccountId.of(command.accountId()))) {
            return Result.failure(new MeetingError.NotAuthorized(
                    command.accountId(), invitee.getAccountId().value()));
        }

        Result<Void, MeetingError> transition =
                invitee.tentative(InviteeResponseSupport.context(meeting));
        if (transition.isFailure()) {
            return Result.failure(((Result.Failure<Void, MeetingError>) transition).error());
        }

        meetingInviteeRepository.save(invitee);
        eventPublisher.publishEventsOf(invitee);

        return Result.success(new TentativeMeetingInviteeResult(
                invitee.getId().value(),
                invitee.getAccountId().value(),
                invitee.getEmail().value(),
                invitee.getDisplayName().value(),
                invitee.getRole().name(),
                invitee.getStatus().name(),
                invitee.getInvitedAt(),
                invitee.getRespondedAt().orElse(null)));
    }
}
