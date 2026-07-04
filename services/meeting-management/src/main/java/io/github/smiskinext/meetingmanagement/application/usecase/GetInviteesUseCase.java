package io.github.smiskinext.meetingmanagement.application.usecase;

import io.github.smiskinext.meetingmanagement.application.response.InviteeListResponse;
import io.github.smiskinext.meetingmanagement.domain.MeetingError;
import io.github.smiskinext.meetingmanagement.domain.model.MeetingInvitee;
import io.github.smiskinext.meetingmanagement.domain.port.InviteTokenRepository;
import io.github.smiskinext.meetingmanagement.domain.port.MeetingInviteeRepository;
import io.github.smiskinext.meetingmanagement.domain.port.MeetingRepository;
import io.github.phunguy65.zms.shared.domain.Result;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetInviteesUseCase {

    private final MeetingRepository meetingRepository;
    private final MeetingInviteeRepository meetingInviteeRepository;
    private final InviteTokenRepository inviteTokenRepository;

    public GetInviteesUseCase(
            MeetingRepository meetingRepository,
            MeetingInviteeRepository meetingInviteeRepository,
            InviteTokenRepository inviteTokenRepository) {
        this.meetingRepository = meetingRepository;
        this.meetingInviteeRepository = meetingInviteeRepository;
        this.inviteTokenRepository = inviteTokenRepository;
    }

    @Transactional(readOnly = true)
    public Result<List<InviteeListResponse>, MeetingError> execute(UUID meetingId) {
        if (meetingRepository.findById(meetingId).isEmpty()) {
            return Result.failure(new MeetingError.MeetingNotFound(meetingId));
        }

        List<MeetingInvitee> invitees = meetingInviteeRepository.findByMeetingId(meetingId);
        var tokens = inviteTokenRepository.findByMeetingId(meetingId);

        List<InviteeListResponse> responses = invitees.stream()
                .map(invitee -> {
                    String tokenStatus = tokens.stream()
                            .filter(t -> t.getInviteeId()
                                    .value()
                                    .equals(invitee.getId().value()))
                            .findFirst()
                            .map(t -> t.getStatus().name())
                            .orElse(null);
                    return new InviteeListResponse(
                            invitee.getId().value(),
                            invitee.getUserId().map(u -> u.value()).orElse(null),
                            invitee.getEmail().value(),
                            invitee.getDisplayName().map(d -> d.value()).orElse(null),
                            invitee.getStatus(),
                            invitee.getInvitedAt(),
                            invitee.getRespondedAt().orElse(null),
                            tokenStatus);
                })
                .toList();

        return Result.success(responses);
    }
}
