package io.github.smiskinext.meet.application.service;

import io.github.smiskinext.meet.application.command.AddMeetingInviteesCommand;
import io.github.smiskinext.meet.application.result.AddMeetingInviteesResult;
import io.github.smiskinext.meet.application.usecase.AddMeetingInviteesUseCase;
import io.github.smiskinext.meet.domain.MeetingError;
import io.github.smiskinext.meet.domain.event.MeetingInvitationsCreatedEvent;
import io.github.smiskinext.meet.domain.model.InviteeRole;
import io.github.smiskinext.meet.domain.model.Meeting;
import io.github.smiskinext.meet.domain.model.MeetingInvitee;
import io.github.smiskinext.meet.domain.model.MeetingStatus;
import io.github.smiskinext.meet.domain.model.valueobject.AccountId;
import io.github.smiskinext.meet.domain.model.valueobject.Email;
import io.github.smiskinext.meet.domain.model.valueobject.InviteeDisplayName;
import io.github.smiskinext.meet.domain.model.valueobject.InviterId;
import io.github.smiskinext.meet.domain.port.MeetingInviteeRepository;
import io.github.smiskinext.meet.domain.port.MeetingRepository;
import io.github.smiskinext.shared.domain.EventPublisher;
import io.github.smiskinext.shared.domain.Result;
import io.github.smiskinext.shared.domain.valueobject.TenantId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AddMeetingInviteesApplicationService implements AddMeetingInviteesUseCase {

    private final MeetingRepository meetingRepository;
    private final MeetingInviteeRepository meetingInviteeRepository;
    private final EventPublisher eventPublisher;

    public AddMeetingInviteesApplicationService(
            MeetingRepository meetingRepository,
            MeetingInviteeRepository meetingInviteeRepository,
            EventPublisher eventPublisher) {
        this.meetingRepository = meetingRepository;
        this.meetingInviteeRepository = meetingInviteeRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public Result<AddMeetingInviteesResult, MeetingError> execute(
            AddMeetingInviteesCommand command) {
        Meeting meeting =
                meetingRepository.findByIdWithLock(command.meetingId()).orElse(null);
        if (meeting == null) {
            return Result.failure(new MeetingError.MeetingNotFound(command.meetingId()));
        }

        AccountId actingAccount = AccountId.of(command.accountId());
        if (!meeting.getHostId().equals(actingAccount)) {
            return Result.failure(new MeetingError.NotAuthorized(
                    actingAccount.value(), meeting.getHostId().value()));
        }
        if (meeting.getStatus() == MeetingStatus.COMPLETED
                || meeting.getStatus() == MeetingStatus.CANCELED) {
            return Result.failure(new MeetingError.InvalidStatusTransition(
                    meeting.getStatus(), MeetingStatus.SCHEDULED));
        }

        Set<String> activeAccountIds =
                meetingInviteeRepository.findByMeetingId(command.meetingId()).stream()
                        .map(invitee -> invitee.getAccountId().value())
                        .collect(Collectors.toSet());

        TenantId tenantId = TenantId.of(command.tenantId());
        InviterId inviterId = InviterId.of(actingAccount.value());

        List<MeetingInvitee> created = new ArrayList<>(command.invitees().size());
        for (AddMeetingInviteesCommand.Invitee invitee : command.invitees()) {
            if (activeAccountIds.contains(invitee.accountId())) {
                return Result.failure(new MeetingError.InviteeAlreadyExists(invitee.accountId()));
            }
            created.add(MeetingInvitee.create(
                    tenantId,
                    meeting.getId(),
                    inviterId,
                    AccountId.of(invitee.accountId()),
                    Email.of(invitee.email()),
                    InviteeDisplayName.of(invitee.displayName()),
                    InviteeRole.REQ_PARTICIPANT,
                    true));
        }

        meetingInviteeRepository.saveAll(created);
        meeting.recordInvitationsSent(toInviteeInfos(created));
        meetingRepository.save(meeting);
        eventPublisher.publishEventsOf(meeting);

        return Result.success(buildResult(created));
    }

    private List<MeetingInvitationsCreatedEvent.InviteeInfo> toInviteeInfos(
            List<MeetingInvitee> invitees) {
        List<MeetingInvitationsCreatedEvent.InviteeInfo> infos = new ArrayList<>(invitees.size());
        for (MeetingInvitee invitee : invitees) {
            infos.add(new MeetingInvitationsCreatedEvent.InviteeInfo(
                    invitee.getId().value(),
                    invitee.getAccountId().value(),
                    invitee.getEmail().value(),
                    invitee.getDisplayName().value(),
                    invitee.getStatus().name()));
        }
        return infos;
    }

    private AddMeetingInviteesResult buildResult(List<MeetingInvitee> invitees) {
        List<AddMeetingInviteesResult.Invitee> snapshots = new ArrayList<>(invitees.size());
        for (MeetingInvitee invitee : invitees) {
            snapshots.add(new AddMeetingInviteesResult.Invitee(
                    invitee.getId().value(),
                    invitee.getAccountId().value(),
                    invitee.getEmail().value(),
                    invitee.getDisplayName().value(),
                    invitee.getRole().name(),
                    invitee.getStatus().name(),
                    invitee.getInvitedAt(),
                    invitee.getRespondedAt().orElse(null)));
        }
        return new AddMeetingInviteesResult(snapshots);
    }
}
