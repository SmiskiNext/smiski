package io.github.smiskinext.meetingmanagement.application.usecase;

import io.github.smiskinext.meetingmanagement.application.command.ResendInviteCommand;
import io.github.smiskinext.meetingmanagement.application.response.InviteeListResponse;
import io.github.smiskinext.meetingmanagement.application.service.InviteTokenService;
import io.github.smiskinext.meetingmanagement.domain.MeetingError;
import io.github.smiskinext.meetingmanagement.domain.event.MeetingInvitationsSentEvent;
import io.github.smiskinext.meetingmanagement.domain.event.MeetingInvitationsSentEvent.InviteeInfo;
import io.github.smiskinext.meetingmanagement.domain.model.InviteToken;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.InviteeDisplayName;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.InviteeId;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.MeetingTitle;
import io.github.smiskinext.meetingmanagement.domain.port.InviteTokenRepository;
import io.github.smiskinext.meetingmanagement.domain.port.MeetingInviteeRepository;
import io.github.smiskinext.meetingmanagement.domain.port.MeetingRepository;
import io.github.phunguy65.zms.shared.domain.Result;
import io.github.phunguy65.zms.shared.domain.valueobject.MeetingId;
import io.github.phunguy65.zms.shared.domain.valueobject.UserId;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResendInviteUseCase {

    private final MeetingRepository meetingRepository;
    private final MeetingInviteeRepository meetingInviteeRepository;
    private final InviteTokenRepository inviteTokenRepository;
    private final InviteTokenService inviteTokenService;
    private final ApplicationEventPublisher eventPublisher;

    public ResendInviteUseCase(
            MeetingRepository meetingRepository,
            MeetingInviteeRepository meetingInviteeRepository,
            InviteTokenRepository inviteTokenRepository,
            InviteTokenService inviteTokenService,
            ApplicationEventPublisher eventPublisher) {
        this.meetingRepository = meetingRepository;
        this.meetingInviteeRepository = meetingInviteeRepository;
        this.inviteTokenRepository = inviteTokenRepository;
        this.inviteTokenService = inviteTokenService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Result<InviteeListResponse, MeetingError> execute(ResendInviteCommand command) {
        var meetingOpt = meetingRepository.findByIdWithLock(command.meetingId());
        if (meetingOpt.isEmpty()) {
            return Result.failure(new MeetingError.MeetingNotFound(command.meetingId()));
        }
        var meeting = meetingOpt.get();

        if (!meeting.getHostId().value().equals(command.requesterId())) {
            return Result.failure(new MeetingError.NotAuthorized(
                    command.requesterId(), meeting.getHostId().value()));
        }

        var inviteeOpt = meetingInviteeRepository.findById(InviteeId.of(command.inviteeId()));
        if (inviteeOpt.isEmpty()) {
            return Result.failure(
                    new MeetingError.InviteeNotFound(command.inviteeId().toString()));
        }
        var invitee = inviteeOpt.get();

        inviteTokenRepository.revokeAllPendingByInviteeId(command.inviteeId());

        String rawToken = inviteTokenService.generateToken(
                MeetingId.of(command.meetingId()), invitee.getId());
        String tokenHash = inviteTokenService.hashToken(rawToken);
        Instant expiresAt = inviteTokenService.extractExpiresAt(rawToken);

        Result<InviteToken, MeetingError> tokenResult = InviteToken.create(
                MeetingId.of(command.meetingId()), invitee.getId(), tokenHash, expiresAt);
        if (tokenResult instanceof Result.Failure<InviteToken, MeetingError>(MeetingError error)) {
            return Result.failure(error);
        }
        InviteToken newToken = ((Result.Success<InviteToken, MeetingError>) tokenResult).value();
        inviteTokenRepository.save(newToken);

        invitee.assignInviteToken(newToken.getId());
        meetingInviteeRepository.save(invitee);

        Map<UUID, String> inviteeTokens = new HashMap<>();
        invitee.getUserId().map(UserId::value).ifPresent(uid -> inviteeTokens.put(uid, rawToken));

        eventPublisher.publishEvent(new MeetingInvitationsSentEvent(
                UUID.randomUUID(),
                command.meetingId(),
                meeting.getTitle().map(MeetingTitle::value).orElse(null),
                meeting.getShortCode().value(),
                meeting.getStartTime().orElse(null),
                List.of(new InviteeInfo(
                        invitee.getUserId().map(UserId::value).orElse(null),
                        invitee.getEmail().value(),
                        invitee.getDisplayName().map(InviteeDisplayName::value).orElse(null))),
                inviteeTokens,
                Instant.now()));

        return Result.success(new InviteeListResponse(
                invitee.getId().value(),
                invitee.getUserId().map(u -> u.value()).orElse(null),
                invitee.getEmail().value(),
                invitee.getDisplayName().map(d -> d.value()).orElse(null),
                invitee.getStatus(),
                invitee.getInvitedAt(),
                invitee.getRespondedAt().orElse(null),
                newToken.getStatus().name()));
    }
}
