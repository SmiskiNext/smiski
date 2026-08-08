package io.github.smiskinext.meet.application.service;

import io.github.smiskinext.meet.application.command.RemoveMeetingInviteesCommand;
import io.github.smiskinext.meet.application.result.RemoveMeetingInviteesResult;
import io.github.smiskinext.meet.application.usecase.RemoveMeetingInviteesUseCase;
import io.github.smiskinext.meet.domain.MeetingError;
import io.github.smiskinext.meet.domain.event.MeetingInvitationsDeletedEvent;
import io.github.smiskinext.meet.domain.model.Meeting;
import io.github.smiskinext.meet.domain.model.MeetingInvitee;
import io.github.smiskinext.meet.domain.model.MeetingStatus;
import io.github.smiskinext.meet.domain.model.valueobject.AccountId;
import io.github.smiskinext.meet.domain.port.MeetingInviteeRepository;
import io.github.smiskinext.meet.domain.port.MeetingRepository;
import io.github.smiskinext.shared.domain.EventPublisher;
import io.github.smiskinext.shared.domain.Result;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RemoveMeetingInviteesApplicationService implements RemoveMeetingInviteesUseCase {

    private final MeetingRepository meetingRepository;
    private final MeetingInviteeRepository meetingInviteeRepository;
    private final EventPublisher eventPublisher;

    public RemoveMeetingInviteesApplicationService(
            MeetingRepository meetingRepository,
            MeetingInviteeRepository meetingInviteeRepository,
            EventPublisher eventPublisher) {
        this.meetingRepository = meetingRepository;
        this.meetingInviteeRepository = meetingInviteeRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public Result<RemoveMeetingInviteesResult, MeetingError> execute(
            RemoveMeetingInviteesCommand command) {
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

        Map<UUID, MeetingInvitee> activeById = new LinkedHashMap<>();
        for (MeetingInvitee invitee :
                meetingInviteeRepository.findByMeetingId(command.meetingId())) {
            activeById.put(invitee.getId().value(), invitee);
        }

        Map<UUID, MeetingInvitee> toRemove = new LinkedHashMap<>();
        for (UUID inviteeId : command.inviteeIds()) {
            MeetingInvitee invitee = activeById.get(inviteeId);
            if (invitee == null) {
                return Result.failure(new MeetingError.InviteeNotFound(inviteeId.toString()));
            }
            toRemove.putIfAbsent(inviteeId, invitee);
        }

        List<MeetingInvitee> removed = new ArrayList<>(toRemove.values());
        for (MeetingInvitee invitee : removed) {
            invitee.remove();
        }

        meetingInviteeRepository.saveAll(removed);
        meeting.recordInviteesRemoved(toInviteeInfos(removed));
        meetingRepository.save(meeting);
        eventPublisher.publishEventsOf(meeting);

        return Result.success(buildResult(removed));
    }

    private List<MeetingInvitationsDeletedEvent.InviteeInfo> toInviteeInfos(
            List<MeetingInvitee> invitees) {
        List<MeetingInvitationsDeletedEvent.InviteeInfo> infos = new ArrayList<>(invitees.size());
        for (MeetingInvitee invitee : invitees) {
            infos.add(new MeetingInvitationsDeletedEvent.InviteeInfo(
                    invitee.getId().value(),
                    invitee.getAccountId().value(),
                    invitee.getEmail().value(),
                    invitee.getDisplayName().value(),
                    invitee.getStatus().name()));
        }
        return infos;
    }

    private RemoveMeetingInviteesResult buildResult(List<MeetingInvitee> invitees) {
        List<RemoveMeetingInviteesResult.Invitee> snapshots = new ArrayList<>(invitees.size());
        for (MeetingInvitee invitee : invitees) {
            snapshots.add(new RemoveMeetingInviteesResult.Invitee(
                    invitee.getId().value(),
                    invitee.getAccountId().value(),
                    invitee.getEmail().value(),
                    invitee.getDisplayName().value(),
                    invitee.getRole().name(),
                    invitee.getStatus().name(),
                    invitee.getInvitedAt(),
                    invitee.getRespondedAt().orElse(null)));
        }
        return new RemoveMeetingInviteesResult(snapshots);
    }
}
