package io.github.smiskinext.meet.application.service;

import io.github.smiskinext.meet.application.command.ApplyEmailInviteeResponseCommand;
import io.github.smiskinext.meet.application.helper.InviteeResponseSupport;
import io.github.smiskinext.meet.application.usecase.ApplyEmailInviteeResponseUseCase;
import io.github.smiskinext.meet.domain.MeetingError;
import io.github.smiskinext.meet.domain.model.Meeting;
import io.github.smiskinext.meet.domain.model.MeetingContext;
import io.github.smiskinext.meet.domain.model.MeetingInvitee;
import io.github.smiskinext.meet.domain.model.valueobject.Email;
import io.github.smiskinext.meet.domain.port.MeetingInviteeRepository;
import io.github.smiskinext.meet.domain.port.MeetingRepository;
import io.github.smiskinext.shared.domain.EventPublisher;
import io.github.smiskinext.shared.domain.Result;
import io.github.smiskinext.shared.infrastructure.tenancy.TenantContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies an invitee response received via inbound email reply (iMIP METHOD:REPLY).
 *
 * <p>Authorization is by email match only — no account identity header is required.
 * Unknown meetings, non-invitee emails, removed invitees, and disallowed transitions
 * are treated as no-ops: logged and silently ignored so the consumer partition is never blocked.
 */
@Service
public class ApplyEmailInviteeResponseApplicationService
        implements ApplyEmailInviteeResponseUseCase {

    private static final Logger log =
            LoggerFactory.getLogger(ApplyEmailInviteeResponseApplicationService.class);

    private final MeetingRepository meetingRepository;
    private final MeetingInviteeRepository meetingInviteeRepository;
    private final EventPublisher eventPublisher;

    public ApplyEmailInviteeResponseApplicationService(
            MeetingRepository meetingRepository,
            MeetingInviteeRepository meetingInviteeRepository,
            EventPublisher eventPublisher) {
        this.meetingRepository = meetingRepository;
        this.meetingInviteeRepository = meetingInviteeRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public void execute(ApplyEmailInviteeResponseCommand command) {
        Meeting meeting =
                meetingRepository.findByCalendarUid(command.calendarUid()).orElse(null);
        if (meeting == null) {
            log.debug(
                    "Email reply ignored: no meeting found for calendarUid={}",
                    command.calendarUid());
            return;
        }

        try {
            TenantContext.setCurrentTenant(meeting.getTenantId().value());

            Email email = Email.of(command.inviteeEmail());
            MeetingInvitee invitee = meetingInviteeRepository
                    .findByMeetingIdAndEmail(meeting.getId().value(), email)
                    .orElse(null);
            if (invitee == null) {
                log.debug(
                        "Email reply ignored: no active invitee with email={} for meeting={}",
                        command.inviteeEmail(),
                        meeting.getId().value());
                return;
            }

            MeetingContext context = InviteeResponseSupport.context(meeting);
            Result<Void, MeetingError> transition =
                    applyTransition(invitee, command.status(), context);

            if (transition.isFailure()) {
                log.debug(
                        "Email reply no-op: transition to {} not allowed for invitee={} (current={})",
                        command.status(),
                        invitee.getId().value(),
                        invitee.getStatus());
                return;
            }

            meetingInviteeRepository.save(invitee);
            eventPublisher.publishEventsOf(invitee);
        } finally {
            TenantContext.clear();
        }
    }

    private Result<Void, MeetingError> applyTransition(
            MeetingInvitee invitee, String status, MeetingContext context) {
        return switch (status.toUpperCase()) {
            case "ACCEPTED" -> invitee.accept(context);
            case "DECLINED" -> invitee.decline(context);
            case "TENTATIVE" -> invitee.tentative(context);
            default ->
                Result.failure(new MeetingError.InvalidInviteeTransition(
                        invitee.getStatus(), invitee.getStatus()));
        };
    }
}
