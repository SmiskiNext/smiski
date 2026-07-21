package io.github.smiskinext.meet.application.service;

import io.github.smiskinext.meet.application.command.UpdateMeetingInviteesCommand;
import io.github.smiskinext.meet.application.result.UpdateMeetingInviteesResult;
import io.github.smiskinext.meet.application.usecase.UpdateMeetingInviteesUseCase;
import io.github.smiskinext.meet.domain.MeetingError;
import io.github.smiskinext.meet.domain.event.MeetingInvitationsCreatedEvent;
import io.github.smiskinext.meet.domain.event.MeetingInvitationsDeletedEvent;
import io.github.smiskinext.meet.domain.event.MeetingInvitationsUpdatedEvent;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateMeetingInviteesApplicationService implements UpdateMeetingInviteesUseCase {

    private final MeetingRepository meetingRepository;
    private final MeetingInviteeRepository meetingInviteeRepository;
    private final EventPublisher eventPublisher;

    public UpdateMeetingInviteesApplicationService(
            MeetingRepository meetingRepository,
            MeetingInviteeRepository meetingInviteeRepository,
            EventPublisher eventPublisher) {
        this.meetingRepository = meetingRepository;
        this.meetingInviteeRepository = meetingInviteeRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public Result<UpdateMeetingInviteesResult, MeetingError> execute(
            UpdateMeetingInviteesCommand command) {
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
        if (meeting.getStatus() != MeetingStatus.SCHEDULED) {
            return Result.failure(new MeetingError.InvalidStatusTransition(
                    meeting.getStatus(), MeetingStatus.SCHEDULED));
        }

        Map<String, UpdateMeetingInviteesCommand.Invitee> requested = new LinkedHashMap<>();
        for (UpdateMeetingInviteesCommand.Invitee invitee : command.invitees()) {
            if (requested.put(invitee.accountId(), invitee) != null) {
                return Result.failure(new MeetingError.InvalidSettings(
                        "Duplicate invitee accountId: " + invitee.accountId()));
            }
        }

        List<MeetingInvitee> activeInvitees =
                meetingInviteeRepository.findByMeetingId(command.meetingId());
        Map<String, MeetingInvitee> existingByAccountId = new LinkedHashMap<>();
        for (MeetingInvitee invitee : activeInvitees) {
            existingByAccountId.put(invitee.getAccountId().value(), invitee);
        }

        TenantId tenantId = TenantId.of(command.tenantId());
        InviterId inviterId = InviterId.of(actingAccount.value());

        List<MeetingInvitee> created = new ArrayList<>();
        List<MeetingInvitee> updated = new ArrayList<>();
        List<MeetingInvitee> removed = new ArrayList<>();
        List<MeetingInvitee> activeAfter = new ArrayList<>();

        for (Map.Entry<String, UpdateMeetingInviteesCommand.Invitee> entry : requested.entrySet()) {
            UpdateMeetingInviteesCommand.Invitee invitee = entry.getValue();
            MeetingInvitee existing = existingByAccountId.get(entry.getKey());
            if (existing == null) {
                MeetingInvitee newInvitee = MeetingInvitee.create(
                        tenantId,
                        meeting.getId(),
                        inviterId,
                        AccountId.of(invitee.accountId()),
                        Email.of(invitee.email()),
                        InviteeDisplayName.of(invitee.displayName()),
                        InviteeRole.REQ_PARTICIPANT,
                        true);
                created.add(newInvitee);
                activeAfter.add(newInvitee);
            } else {
                InviteeDisplayName newDisplayName = InviteeDisplayName.of(invitee.displayName());
                if (!existing.getDisplayName().equals(newDisplayName)) {
                    existing.updateDisplayName(newDisplayName);
                    updated.add(existing);
                }
                activeAfter.add(existing);
            }
        }

        for (MeetingInvitee existing : activeInvitees) {
            if (!requested.containsKey(existing.getAccountId().value())) {
                existing.remove();
                removed.add(existing);
            }
        }

        List<MeetingInvitee> toPersist =
                new ArrayList<>(created.size() + updated.size() + removed.size());
        toPersist.addAll(created);
        toPersist.addAll(updated);
        toPersist.addAll(removed);
        if (!toPersist.isEmpty()) {
            meetingInviteeRepository.saveAll(toPersist);
        }

        registerEvents(meeting, created, updated, removed);
        if (!meeting.getDomainEvents().isEmpty()) {
            meetingRepository.save(meeting);
            eventPublisher.publishEventsOf(meeting);
        }

        return Result.success(buildResult(activeAfter));
    }

    private void registerEvents(
            Meeting meeting,
            List<MeetingInvitee> created,
            List<MeetingInvitee> updated,
            List<MeetingInvitee> removed) {
        List<MeetingInvitationsCreatedEvent.InviteeInfo> createdInfos = new ArrayList<>();
        for (MeetingInvitee invitee : created) {
            createdInfos.add(new MeetingInvitationsCreatedEvent.InviteeInfo(
                    invitee.getId().value(),
                    invitee.getAccountId().value(),
                    invitee.getEmail().value(),
                    invitee.getDisplayName().value(),
                    invitee.getStatus().name()));
        }
        meeting.recordInvitationsSent(createdInfos);

        List<MeetingInvitationsUpdatedEvent.InviteeInfo> updatedInfos = new ArrayList<>();
        for (MeetingInvitee invitee : updated) {
            updatedInfos.add(new MeetingInvitationsUpdatedEvent.InviteeInfo(
                    invitee.getId().value(),
                    invitee.getAccountId().value(),
                    invitee.getEmail().value(),
                    invitee.getDisplayName().value(),
                    invitee.getStatus().name()));
        }
        meeting.recordInviteesUpdated(updatedInfos);

        List<MeetingInvitationsDeletedEvent.InviteeInfo> removedInfos = new ArrayList<>();
        for (MeetingInvitee invitee : removed) {
            removedInfos.add(new MeetingInvitationsDeletedEvent.InviteeInfo(
                    invitee.getId().value(),
                    invitee.getAccountId().value(),
                    invitee.getEmail().value(),
                    invitee.getDisplayName().value(),
                    invitee.getStatus().name()));
        }
        meeting.recordInviteesRemoved(removedInfos);
    }

    private UpdateMeetingInviteesResult buildResult(List<MeetingInvitee> activeInvitees) {
        List<UpdateMeetingInviteesResult.Invitee> invitees = new ArrayList<>(activeInvitees.size());
        Set<String> seen = new HashSet<>();
        for (MeetingInvitee invitee : activeInvitees) {
            if (!seen.add(invitee.getId().value().toString())) {
                continue;
            }
            invitees.add(new UpdateMeetingInviteesResult.Invitee(
                    invitee.getId().value(),
                    invitee.getAccountId().value(),
                    invitee.getEmail().value(),
                    invitee.getDisplayName().value(),
                    invitee.getRole().name(),
                    invitee.getStatus().name(),
                    invitee.getInvitedAt(),
                    invitee.getRespondedAt().orElse(null)));
        }
        return new UpdateMeetingInviteesResult(invitees);
    }
}
