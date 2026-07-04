package io.github.smiskinext.meetingmanagement.application.usecase;

import io.github.smiskinext.meetingmanagement.application.command.ScheduleMeetingCommand;
import io.github.smiskinext.meetingmanagement.application.command.ScheduleMeetingCommand.InviteeInput;
import io.github.smiskinext.meetingmanagement.application.helper.MeetingSettingsPasswordResolver;
import io.github.smiskinext.meetingmanagement.application.helper.ShortCodeGenerator;
import io.github.smiskinext.meetingmanagement.application.response.MeetingResponse;
import io.github.smiskinext.meetingmanagement.application.response.MeetingSettingsResponse;
import io.github.smiskinext.meetingmanagement.application.service.InviteTokenService;
import io.github.smiskinext.meetingmanagement.domain.MeetingError;
import io.github.smiskinext.meetingmanagement.domain.PublishableEvent;
import io.github.smiskinext.meetingmanagement.domain.event.MeetingInvitationsSentEvent;
import io.github.smiskinext.meetingmanagement.domain.event.MeetingInvitationsSentEvent.InviteeInfo;
import io.github.smiskinext.meetingmanagement.domain.model.InviteToken;
import io.github.smiskinext.meetingmanagement.domain.model.Meeting;
import io.github.smiskinext.meetingmanagement.domain.model.MeetingInvitee;
import io.github.phunguy65.zms.meetingmanagement.domain.model.valueobject.*;
import io.github.phunguy65.zms.meetingmanagement.domain.port.*;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.*;
import io.github.smiskinext.meetingmanagement.domain.port.*;
import io.github.smiskinext.meetingmanagement.domain.port.UserGrpcServicePort.ResolvedUser;
import io.github.phunguy65.zms.shared.domain.Result;
import io.github.phunguy65.zms.shared.domain.valueobject.Email;
import io.github.phunguy65.zms.shared.domain.valueobject.MeetingId;
import io.github.phunguy65.zms.shared.domain.valueobject.UserId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScheduleMeetingUseCase {

    private final MeetingRepository meetingRepository;
    private final ShortCodeGenerator shortCodeGenerator;
    private final ApplicationEventPublisher eventPublisher;
    private final MeetingLimitsPort limitsConfig;
    private final UserGrpcServicePort userGrpcServicePort;
    private final MeetingInviteeRepository inviteeRepository;
    private final PasswordHasher passwordHasher;
    private final InviteTokenService inviteTokenService;
    private final InviteTokenRepository inviteTokenRepository;

    public ScheduleMeetingUseCase(
            MeetingRepository meetingRepository,
            ShortCodeGenerator shortCodeGenerator,
            ApplicationEventPublisher eventPublisher,
            MeetingLimitsPort limitsConfig,
            UserGrpcServicePort userGrpcServicePort,
            MeetingInviteeRepository inviteeRepository,
            PasswordHasher passwordHasher,
            InviteTokenService inviteTokenService,
            InviteTokenRepository inviteTokenRepository) {
        this.meetingRepository = meetingRepository;
        this.shortCodeGenerator = shortCodeGenerator;
        this.eventPublisher = eventPublisher;
        this.limitsConfig = limitsConfig;
        this.userGrpcServicePort = userGrpcServicePort;
        this.inviteeRepository = inviteeRepository;
        this.passwordHasher = passwordHasher;
        this.inviteTokenService = inviteTokenService;
        this.inviteTokenRepository = inviteTokenRepository;
    }

    @Transactional
    public Result<MeetingResponse, MeetingError> execute(ScheduleMeetingCommand command) {
        long durationMinutes = command.timeRange().duration().toMinutes();
        if (durationMinutes < limitsConfig.getMinDurationMinutes()
                || durationMinutes > limitsConfig.getMaxDurationMinutes()) {
            return Result.failure(new MeetingError.InvalidMeetingDuration(
                    durationMinutes,
                    limitsConfig.getMinDurationMinutes(),
                    limitsConfig.getMaxDurationMinutes()));
        }

        MeetingSettings settings = command.settings();
        if (settings.maxParticipants() > limitsConfig.getMaxParticipantsCeiling()) {
            return Result.failure(new MeetingError.InvalidSettings(
                    "maxParticipants " + settings.maxParticipants() + " exceeds system ceiling "
                            + limitsConfig.getMaxParticipantsCeiling()));
        }

        String rawPassword =
                MeetingSettingsPasswordResolver.normalizeRawPassword(command.rawPassword());
        if (rawPassword != null) {
            settings = MeetingSettingsPasswordResolver.withRawPassword(
                    settings, rawPassword, passwordHasher);
        }

        List<InviteeInput> inviteeInputs =
                command.invitees() != null ? command.invitees() : List.of();
        Map<String, ResolvedUser> resolvedUsers;
        if (!inviteeInputs.isEmpty()) {
            List<String> emails = inviteeInputs.stream()
                    .map(InviteeInput::email)
                    .filter(email -> email != null)
                    .toList();
            try {
                resolvedUsers = userGrpcServicePort.resolveUsers(emails);
            } catch (UserGrpcServicePort.UserServiceException e) {
                return Result.failure(e.getError());
            }

            for (InviteeInput input : inviteeInputs) {
                String identifier = input.email();
                if (identifier != null && !resolvedUsers.containsKey(identifier)) {
                    return Result.failure(new MeetingError.InviteeNotFound(identifier));
                }
            }
        } else {
            resolvedUsers = Map.of();
        }

        var shortCodeResult = shortCodeGenerator.generate();
        if (shortCodeResult instanceof Result.Failure<?, MeetingError>(MeetingError error)) {
            return Result.failure(error);
        }
        var shortCode = ((Result.Success<ShortCode, MeetingError>) shortCodeResult).value();

        Meeting meeting = Meeting.schedule(
                UserId.of(command.hostId()),
                command.title(),
                command.description(),
                command.timeRange(),
                settings,
                shortCode);

        Meeting saved = meetingRepository.save(meeting);

        if (!resolvedUsers.isEmpty()) {
            List<MeetingInvitee> invitees =
                    buildInvitees(saved.getId(), command.hostId(), inviteeInputs, resolvedUsers);
            inviteeRepository.saveAll(invitees);

            Map<UUID, String> inviteeTokens = new HashMap<>();
            for (MeetingInvitee invitee : invitees) {
                String rawToken = inviteTokenService.generateToken(saved.getId(), invitee.getId());
                String tokenHash = inviteTokenService.hashToken(rawToken);
                Instant expiresAt = inviteTokenService.extractExpiresAt(rawToken);
                Result<InviteToken, MeetingError> tokenResult =
                        InviteToken.create(saved.getId(), invitee.getId(), tokenHash, expiresAt);
                if (tokenResult
                        instanceof Result.Success<InviteToken, MeetingError>(InviteToken token)) {
                    inviteTokenRepository.save(token);
                    invitee.assignInviteToken(token.getId());
                    inviteeRepository.save(invitee);
                    invitee.getUserId()
                            .map(UserId::value)
                            .ifPresent(uid -> inviteeTokens.put(uid, rawToken));
                }
            }

            List<InviteeInfo> inviteeInfos = invitees.stream()
                    .map(i -> new InviteeInfo(
                            i.getUserId().map(UserId::value).orElse(null),
                            i.getEmail().value(),
                            i.getDisplayName().map(InviteeDisplayName::value).orElse(null)))
                    .toList();
            eventPublisher.publishEvent(new MeetingInvitationsSentEvent(
                    UUID.randomUUID(),
                    saved.getId().value(),
                    saved.getTitle().map(MeetingTitle::value).orElse(null),
                    saved.getShortCode().value(),
                    saved.getStartTime().orElse(null),
                    inviteeInfos,
                    inviteeTokens,
                    Instant.now()));
        }

        saved.getDomainEvents().stream()
                .filter(e -> e instanceof PublishableEvent)
                .map(e -> (PublishableEvent) e)
                .forEach(eventPublisher::publishEvent);
        saved.clearDomainEvents();

        return Result.success(toResponse(saved));
    }

    private List<MeetingInvitee> buildInvitees(
            MeetingId meetingId,
            UUID inviterId,
            List<InviteeInput> inputs,
            Map<String, ResolvedUser> resolved) {
        List<MeetingInvitee> result = new ArrayList<>();

        var addedEmails = new java.util.HashSet<String>();
        for (InviteeInput input : inputs) {
            String identifier = input.email();
            if (identifier == null) continue;
            ResolvedUser user = resolved.get(identifier);
            if (user == null) continue;
            if (inviterId.equals(user.userId())) continue;
            if (addedEmails.add(user.email())) {
                result.add(MeetingInvitee.create(
                        meetingId,
                        InviterId.of(inviterId),
                        user.userId() != null ? UserId.of(user.userId()) : null,
                        Email.of(user.email()),
                        user.displayName() != null
                                ? InviteeDisplayName.of(user.displayName())
                                : null));
            }
        }
        return result;
    }

    private MeetingResponse toResponse(Meeting m) {
        return new MeetingResponse(
                m.getId().value(),
                m.getHostId().value(),
                m.getShortCode().value(),
                m.getTitle().map(MeetingTitle::value).orElse(null),
                m.getDescription().orElse(null),
                m.getStartTime().orElse(null),
                m.getEndTime().orElse(null),
                m.getType(),
                m.getStatus(),
                MeetingSettingsResponse.from(m.getSettings()),
                m.getCreatedAt());
    }
}
