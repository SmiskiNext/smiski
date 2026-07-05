package io.github.smiskinext.meetingmanagement.application.usecase;

import io.github.smiskinext.meetingmanagement.application.command.CancelMeetingCommand;
import io.github.smiskinext.meetingmanagement.domain.MeetingError;
import io.github.smiskinext.meetingmanagement.domain.PublishableEvent;
import io.github.smiskinext.meetingmanagement.domain.event.MeetingCancelledEvent;
import io.github.smiskinext.meetingmanagement.domain.model.InviteeStatus;
import io.github.smiskinext.meetingmanagement.domain.port.MeetingInviteeRepository;
import io.github.smiskinext.meetingmanagement.domain.port.MeetingRepository;
import io.github.smiskinext.shared.domain.Result;
import io.github.smiskinext.shared.domain.valueobject.UserId;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CancelMeetingUseCase {

    private final MeetingRepository meetingRepository;
    private final MeetingInviteeRepository meetingInviteeRepository;
    private final ApplicationEventPublisher eventPublisher;

    public CancelMeetingUseCase(
            MeetingRepository meetingRepository,
            MeetingInviteeRepository meetingInviteeRepository,
            ApplicationEventPublisher eventPublisher) {
        this.meetingRepository = meetingRepository;
        this.meetingInviteeRepository = meetingInviteeRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Result<Void, MeetingError> execute(CancelMeetingCommand command) {
        var meeting = meetingRepository.findById(command.meetingId());
        if (meeting.isEmpty()) {
            return Result.failure(new MeetingError.MeetingNotFound(command.meetingId()));
        }
        var m = meeting.get();

        if (!m.getHostId().equals(UserId.of(command.requesterId()))) {
            return Result.failure(new MeetingError.NotAuthorized(
                    command.requesterId(), m.getHostId().value()));
        }

        List<MeetingCancelledEvent.InviteeInfo> invitees =
                meetingInviteeRepository.findByMeetingId(command.meetingId()).stream()
                        .filter(invitee -> invitee.getStatus() == InviteeStatus.PENDING
                                || invitee.getStatus() == InviteeStatus.ACCEPTED)
                        .map(invitee -> new MeetingCancelledEvent.InviteeInfo(
                                invitee.getUserId().map(UserId::value).orElse(null),
                                invitee.getEmail().value(),
                                invitee.getDisplayName().map(d -> d.value()).orElse(null),
                                invitee.getStatus().name(),
                                invitee.getInvitedAt()))
                        .toList();

        var cancelResult = m.cancel(
                m.getTitle().map(title -> title.value()).orElse(null),
                m.getShortCode().value(),
                m.getStartTime().orElse(null),
                invitees);
        if (cancelResult instanceof Result.Failure<?, MeetingError> failure) {
            return Result.failure(failure.error());
        }

        var saved = meetingRepository.save(m);
        saved.getDomainEvents().stream()
                .filter(e -> e instanceof PublishableEvent)
                .map(e -> (PublishableEvent) e)
                .forEach(eventPublisher::publishEvent);
        saved.clearDomainEvents();

        return Result.success();
    }
}
