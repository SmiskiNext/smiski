package io.github.smiskinext.meetingmanagement.application.usecase;

import io.github.smiskinext.meetingmanagement.application.command.AddInviteeCommand;
import io.github.smiskinext.meetingmanagement.application.response.InviteeListResponse;
import io.github.smiskinext.meetingmanagement.application.service.InviteTokenService;
import io.github.smiskinext.meetingmanagement.domain.MeetingError;
import io.github.smiskinext.meetingmanagement.domain.event.MeetingInvitationsSentEvent;
import io.github.smiskinext.meetingmanagement.domain.event.MeetingInvitationsSentEvent.InviteeInfo;
import io.github.smiskinext.meetingmanagement.domain.model.InviteToken;
import io.github.smiskinext.meetingmanagement.domain.model.MeetingInvitee;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.InviteeDisplayName;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.InviterId;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.MeetingTitle;
import io.github.smiskinext.meetingmanagement.domain.port.InviteTokenRepository;
import io.github.smiskinext.meetingmanagement.domain.port.MeetingInviteeRepository;
import io.github.smiskinext.meetingmanagement.domain.port.MeetingRepository;
import io.github.smiskinext.meetingmanagement.domain.port.UserGrpcServicePort;
import io.github.smiskinext.meetingmanagement.domain.port.UserGrpcServicePort.ResolvedUser;
import io.github.smiskinext.shared.domain.Result;
import io.github.smiskinext.shared.domain.valueobject.Email;
import io.github.smiskinext.shared.domain.valueobject.MeetingId;
import io.github.smiskinext.shared.domain.valueobject.UserId;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AddInviteeUseCase {

    private final MeetingRepository meetingRepository;
    private final MeetingInviteeRepository meetingInviteeRepository;
    private final InviteTokenRepository inviteTokenRepository;
    private final InviteTokenService inviteTokenService;
    private final UserGrpcServicePort userGrpcServicePort;
    private final ApplicationEventPublisher eventPublisher;

    public AddInviteeUseCase(
            MeetingRepository meetingRepository,
            MeetingInviteeRepository meetingInviteeRepository,
            InviteTokenRepository inviteTokenRepository,
            InviteTokenService inviteTokenService,
            UserGrpcServicePort userGrpcServicePort,
            ApplicationEventPublisher eventPublisher) {
        this.meetingRepository = meetingRepository;
        this.meetingInviteeRepository = meetingInviteeRepository;
        this.inviteTokenRepository = inviteTokenRepository;
        this.inviteTokenService = inviteTokenService;
        this.userGrpcServicePort = userGrpcServicePort;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Result<InviteeListResponse, MeetingError> execute(AddInviteeCommand command) {
        var meetingOpt = meetingRepository.findByIdWithLock(command.meetingId());
        if (meetingOpt.isEmpty()) {
            return Result.failure(new MeetingError.MeetingNotFound(command.meetingId()));
        }
        var meeting = meetingOpt.get();

        if (!meeting.getHostId().value().equals(command.requesterId())) {
            return Result.failure(new MeetingError.NotAuthorized(
                    command.requesterId(), meeting.getHostId().value()));
        }

        ResolvedUser resolvedUser;
        try {
            Map<String, ResolvedUser> resolved =
                    userGrpcServicePort.resolveUsers(List.of(command.email()));
            resolvedUser = resolved.get(command.email());
        } catch (UserGrpcServicePort.UserServiceException e) {
            return Result.failure(e.getError());
        }
        if (resolvedUser == null) {
            return Result.failure(new MeetingError.InviteeNotFound(command.email()));
        }

        MeetingInvitee invitee = MeetingInvitee.create(
                MeetingId.of(command.meetingId()),
                InviterId.of(command.requesterId()),
                resolvedUser.userId() != null ? UserId.of(resolvedUser.userId()) : null,
                Email.of(resolvedUser.email()),
                command.displayName() != null
                        ? InviteeDisplayName.of(command.displayName())
                        : null);

        invitee = meetingInviteeRepository.save(invitee);

        String rawToken = inviteTokenService.generateToken(
                MeetingId.of(command.meetingId()), invitee.getId());
        String tokenHash = inviteTokenService.hashToken(rawToken);
        Instant expiresAt = inviteTokenService.extractExpiresAt(rawToken);

        Result<InviteToken, MeetingError> tokenResult = InviteToken.create(
                MeetingId.of(command.meetingId()), invitee.getId(), tokenHash, expiresAt);
        if (tokenResult instanceof Result.Failure<InviteToken, MeetingError>(MeetingError error)) {
            return Result.failure(error);
        }
        InviteToken token = ((Result.Success<InviteToken, MeetingError>) tokenResult).value();
        inviteTokenRepository.save(token);

        invitee.assignInviteToken(token.getId());
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
                null,
                token.getStatus().name()));
    }
}
