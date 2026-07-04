package io.github.smiskinext.meetingmanagement.application.usecase;

import io.github.smiskinext.meetingmanagement.application.command.PutMeetingSettingsCommand;
import io.github.smiskinext.meetingmanagement.application.helper.MeetingSettingsPasswordResolver;
import io.github.smiskinext.meetingmanagement.application.helper.PendingJoinRequestApprover;
import io.github.smiskinext.meetingmanagement.application.response.MeetingSettingsResponse;
import io.github.smiskinext.meetingmanagement.domain.MeetingError;
import io.github.smiskinext.meetingmanagement.domain.PublishableEvent;
import io.github.smiskinext.meetingmanagement.domain.event.MeetingInviteTokensInvalidatedEvent;
import io.github.smiskinext.meetingmanagement.domain.event.MeetingInviteTokensInvalidatedEvent.AffectedInviteeInfo;
import io.github.smiskinext.meetingmanagement.domain.model.AdmissionPolicy;
import io.github.smiskinext.meetingmanagement.domain.model.MeetingInvitee;
import io.github.smiskinext.meetingmanagement.domain.model.MeetingStatus;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.InviteeDisplayName;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.MeetingSettings;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.MeetingTitle;
import io.github.smiskinext.meetingmanagement.domain.port.InviteTokenRepository;
import io.github.smiskinext.meetingmanagement.domain.port.MeetingInviteeRepository;
import io.github.smiskinext.meetingmanagement.domain.port.MeetingLimitsPort;
import io.github.smiskinext.meetingmanagement.domain.port.MeetingRepository;
import io.github.smiskinext.meetingmanagement.domain.port.ParticipationLogRepository;
import io.github.smiskinext.meetingmanagement.domain.port.PasswordHasher;
import io.github.phunguy65.zms.shared.domain.Result;
import io.github.phunguy65.zms.shared.domain.valueobject.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PutMeetingSettingsUseCase {

    private final MeetingRepository meetingRepository;
    private final MeetingLimitsPort limitsConfig;
    private final PendingJoinRequestApprover pendingJoinRequestApprover;
    private final ApplicationEventPublisher eventPublisher;
    private final PasswordHasher passwordHasher;
    private final InviteTokenRepository inviteTokenRepository;
    private final MeetingInviteeRepository meetingInviteeRepository;
    private final ParticipationLogRepository participationLogRepository;

    public PutMeetingSettingsUseCase(
            MeetingRepository meetingRepository,
            MeetingLimitsPort limitsConfig,
            PendingJoinRequestApprover pendingJoinRequestApprover,
            ApplicationEventPublisher eventPublisher,
            PasswordHasher passwordHasher,
            InviteTokenRepository inviteTokenRepository,
            MeetingInviteeRepository meetingInviteeRepository,
            ParticipationLogRepository participationLogRepository) {
        this.meetingRepository = meetingRepository;
        this.limitsConfig = limitsConfig;
        this.pendingJoinRequestApprover = pendingJoinRequestApprover;
        this.eventPublisher = eventPublisher;
        this.passwordHasher = passwordHasher;
        this.inviteTokenRepository = inviteTokenRepository;
        this.meetingInviteeRepository = meetingInviteeRepository;
        this.participationLogRepository = participationLogRepository;
    }

    @Transactional
    public Result<MeetingSettingsResponse, MeetingError> execute(
            PutMeetingSettingsCommand command) {
        var meetingOpt = meetingRepository.findByIdWithLock(command.meetingId());
        if (meetingOpt.isEmpty()) {
            return Result.failure(new MeetingError.MeetingNotFound(command.meetingId()));
        }
        var meeting = meetingOpt.get();

        if (!meeting.getHostId().equals(UserId.of(command.requesterId()))) {
            return Result.failure(new MeetingError.NotAuthorized(
                    command.requesterId(), meeting.getHostId().value()));
        }

        if (meeting.getStatus() != MeetingStatus.SCHEDULED
                && meeting.getStatus() != MeetingStatus.LIVE) {
            return Result.failure(new MeetingError.InvalidStatusTransition(
                    meeting.getStatus(), MeetingStatus.SCHEDULED));
        }

        MeetingSettings existing = meeting.getSettings();
        MeetingSettings requested = command.settings();

        if (requested.maxParticipants() > limitsConfig.getMaxParticipantsCeiling()) {
            return Result.failure(new MeetingError.InvalidSettings(
                    "maxParticipants " + requested.maxParticipants() + " exceeds system ceiling "
                            + limitsConfig.getMaxParticipantsCeiling()));
        }

        if (requested.maxParticipants() > 0) {
            long activeCount =
                    participationLogRepository.countActiveByMeetingId(command.meetingId());
            if (requested.maxParticipants() < activeCount) {
                return Result.failure(new MeetingError.MeetingFull(
                        command.meetingId(), requested.maxParticipants()));
            }
        }

        MeetingSettings replacement = MeetingSettingsPasswordResolver.withRawPassword(
                requested,
                MeetingSettingsPasswordResolver.normalizeRawPassword(command.rawPassword()),
                passwordHasher);

        var updateResult = meeting.updateSettings(replacement, command.requesterId());
        if (updateResult instanceof Result.Failure<?, MeetingError>(MeetingError error)) {
            return Result.failure(error);
        }

        if (meeting.getStatus() == MeetingStatus.LIVE) {
            boolean policyOpenedUp = replacement.admissionPolicy() == AdmissionPolicy.ALLOW_ALL
                    && existing.admissionPolicy() != AdmissionPolicy.ALLOW_ALL;
            boolean guestOpenedUp = replacement.allowGuest() && !existing.allowGuest();
            if (policyOpenedUp || guestOpenedUp) {
                var approvalResult =
                        pendingJoinRequestApprover.approveAll(meeting, command.requesterId());
                if (approvalResult
                        instanceof Result.Failure<Integer, MeetingError>(MeetingError error)) {
                    return Result.failure(error);
                }
            }
        }

        var saved = meetingRepository.save(meeting);

        int invalidatedInviteCount = 0;
        if (meeting.getStatus() == MeetingStatus.SCHEDULED
                && passwordChanged(existing, replacement)) {
            invalidatedInviteCount = revokeTokensAndPublishEvent(
                    saved.getId().value(),
                    saved.getHostId().value(),
                    saved.getTitle().map(MeetingTitle::value).orElse(null),
                    saved.getShortCode().value());
        }

        saved.getDomainEvents().stream()
                .filter(e -> e instanceof PublishableEvent)
                .map(e -> (PublishableEvent) e)
                .forEach(eventPublisher::publishEvent);
        saved.clearDomainEvents();

        return Result.success(
                MeetingSettingsResponse.from(saved.getSettings(), invalidatedInviteCount));
    }

    private boolean passwordChanged(MeetingSettings existing, MeetingSettings replacement) {
        return !Objects.equals(existing.password(), replacement.password());
    }

    private int revokeTokensAndPublishEvent(
            UUID meetingId, UUID hostId, String meetingTitle, String shortCode) {
        int revokedCount = inviteTokenRepository.revokeAllPendingByMeetingId(meetingId);

        List<MeetingInvitee> invitees = meetingInviteeRepository.findByMeetingId(meetingId);
        List<AffectedInviteeInfo> affectedInvitees = invitees.stream()
                .map(invitee -> new AffectedInviteeInfo(
                        invitee.getId().value(),
                        invitee.getUserId().map(UserId::value).orElse(null),
                        invitee.getEmail().value(),
                        invitee.getDisplayName().map(InviteeDisplayName::value).orElse(null)))
                .toList();

        eventPublisher.publishEvent(new MeetingInviteTokensInvalidatedEvent(
                UUID.randomUUID(),
                meetingId,
                hostId,
                meetingTitle,
                shortCode,
                affectedInvitees,
                Instant.now()));

        return revokedCount;
    }
}
