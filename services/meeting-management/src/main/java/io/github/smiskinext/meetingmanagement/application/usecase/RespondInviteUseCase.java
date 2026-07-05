package io.github.smiskinext.meetingmanagement.application.usecase;

import io.github.smiskinext.meetingmanagement.application.command.InviteeResponseType;
import io.github.smiskinext.meetingmanagement.application.command.RespondInviteCommand;
import io.github.smiskinext.meetingmanagement.application.response.InviteeRespondResponse;
import io.github.smiskinext.meetingmanagement.domain.MeetingError;
import io.github.smiskinext.meetingmanagement.domain.model.InviteeStatus;
import io.github.smiskinext.meetingmanagement.domain.model.Meeting;
import io.github.smiskinext.meetingmanagement.domain.model.MeetingInvitee;
import io.github.smiskinext.meetingmanagement.domain.port.MeetingInviteeRepository;
import io.github.smiskinext.meetingmanagement.domain.port.MeetingRepository;
import io.github.smiskinext.shared.domain.Result;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RespondInviteUseCase {

    private final MeetingRepository meetingRepository;
    private final MeetingInviteeRepository meetingInviteeRepository;
    private final ApplicationEventPublisher eventPublisher;

    public RespondInviteUseCase(
            MeetingRepository meetingRepository,
            MeetingInviteeRepository meetingInviteeRepository,
            ApplicationEventPublisher eventPublisher) {
        this.meetingRepository = meetingRepository;
        this.meetingInviteeRepository = meetingInviteeRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Result<InviteeRespondResponse, MeetingError> execute(RespondInviteCommand command) {
        if (!command.requesterId().equals(command.userId())) {
            return Result.failure(
                    new MeetingError.NotOwner(command.requesterId(), command.userId()));
        }

        Meeting meeting = meetingRepository.findById(command.meetingId()).orElse(null);
        if (meeting == null) {
            return Result.failure(new MeetingError.MeetingNotFound(command.meetingId()));
        }

        MeetingInvitee invitee = meetingInviteeRepository
                .findByMeetingIdAndUserId(command.meetingId(), command.userId())
                .orElse(null);
        if (invitee == null) {
            return Result.failure(
                    new MeetingError.InviteeNotFound(command.userId().toString()));
        }

        InviteeStatus targetStatus = command.response() == InviteeResponseType.ACCEPTED
                ? InviteeStatus.ACCEPTED
                : InviteeStatus.DECLINED;
        Result<Void, MeetingError> transition =
                targetStatus == InviteeStatus.ACCEPTED ? invitee.accept() : invitee.decline();
        if (transition instanceof Result.Failure<Void, MeetingError> failure) {
            return Result.failure(failure.error());
        }

        meetingInviteeRepository.save(invitee);
        Instant respondedAt = invitee.getRespondedAt().orElseThrow();
        invitee.getDomainEvents().forEach(eventPublisher::publishEvent);
        invitee.clearDomainEvents();

        return Result.success(new InviteeRespondResponse(
                invitee.getMeetingId().value(),
                invitee.getUserId().orElseThrow().value(),
                invitee.getStatus(),
                respondedAt));
    }
}
