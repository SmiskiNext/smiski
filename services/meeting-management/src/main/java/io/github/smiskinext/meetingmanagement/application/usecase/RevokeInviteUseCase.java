package io.github.smiskinext.meetingmanagement.application.usecase;

import io.github.smiskinext.meetingmanagement.application.response.InviteeListResponse;
import io.github.smiskinext.meetingmanagement.domain.MeetingError;
import io.github.smiskinext.meetingmanagement.domain.model.MeetingInvitee;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.InviteeId;
import io.github.smiskinext.meetingmanagement.domain.port.InviteTokenRepository;
import io.github.smiskinext.meetingmanagement.domain.port.MeetingInviteeRepository;
import io.github.smiskinext.meetingmanagement.domain.port.MeetingRepository;
import io.github.smiskinext.shared.domain.Result;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RevokeInviteUseCase {

    private final MeetingRepository meetingRepository;
    private final MeetingInviteeRepository meetingInviteeRepository;
    private final InviteTokenRepository inviteTokenRepository;

    public RevokeInviteUseCase(
            MeetingRepository meetingRepository,
            MeetingInviteeRepository meetingInviteeRepository,
            InviteTokenRepository inviteTokenRepository) {
        this.meetingRepository = meetingRepository;
        this.meetingInviteeRepository = meetingInviteeRepository;
        this.inviteTokenRepository = inviteTokenRepository;
    }

    @Transactional
    public Result<InviteeListResponse, MeetingError> execute(
            UUID meetingId, UUID inviteeId, UUID requesterId) {
        var meetingOpt = meetingRepository.findByIdWithLock(meetingId);
        if (meetingOpt.isEmpty()) {
            return Result.failure(new MeetingError.MeetingNotFound(meetingId));
        }
        var meeting = meetingOpt.get();

        if (!meeting.getHostId().value().equals(requesterId)) {
            return Result.failure(new MeetingError.NotAuthorized(
                    requesterId, meeting.getHostId().value()));
        }

        var inviteeOpt = meetingInviteeRepository.findById(InviteeId.of(inviteeId));
        if (inviteeOpt.isEmpty()) {
            return Result.failure(new MeetingError.InviteeNotFound(inviteeId.toString()));
        }
        MeetingInvitee invitee = inviteeOpt.get();

        inviteTokenRepository.revokeAllPendingByInviteeId(inviteeId);

        return Result.success(new InviteeListResponse(
                invitee.getId().value(),
                invitee.getUserId().map(u -> u.value()).orElse(null),
                invitee.getEmail().value(),
                invitee.getDisplayName().map(d -> d.value()).orElse(null),
                invitee.getStatus(),
                invitee.getInvitedAt(),
                invitee.getRespondedAt().orElse(null),
                "REVOKED"));
    }
}
