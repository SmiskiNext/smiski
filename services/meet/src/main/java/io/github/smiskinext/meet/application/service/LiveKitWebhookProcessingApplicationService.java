package io.github.smiskinext.meet.application.service;

import io.github.smiskinext.meet.domain.model.Meeting;
import io.github.smiskinext.meet.domain.model.ParticipantRole;
import io.github.smiskinext.meet.domain.model.ParticipationLog;
import io.github.smiskinext.meet.domain.model.valueobject.AccountId;
import io.github.smiskinext.meet.domain.model.valueobject.LiveKitIdentity;
import io.github.smiskinext.meet.domain.model.valueobject.LiveKitParticipantSid;
import io.github.smiskinext.meet.domain.model.valueobject.LiveKitWebhookEvent;
import io.github.smiskinext.meet.domain.model.valueobject.MeetingId;
import io.github.smiskinext.meet.domain.port.MeetingRepository;
import io.github.smiskinext.meet.domain.port.ParticipationLogRepository;
import io.github.smiskinext.meet.domain.port.ScreenShareStateRepository;
import io.github.smiskinext.shared.domain.EventPublisher;
import io.github.smiskinext.shared.domain.Result;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Performs the idempotent database work for a verified LiveKit webhook event under an
 * already-bound tenant context.
 *
 * <p>Each handler checks current state before mutating so redelivery and multi-instance processing
 * do not double-apply. Domain events are drained through the transactional outbox only when a real
 * transition or mutation occurs.
 */
@Service
public class LiveKitWebhookProcessingApplicationService {

    private static final Logger log =
            LoggerFactory.getLogger(LiveKitWebhookProcessingApplicationService.class);

    private static final String ROOM_NAME_PREFIX = "meeting-";
    private static final String ROLE_ATTRIBUTE = "role";
    private static final String SCREEN_SHARE_SOURCE = "SCREEN_SHARE";

    private final MeetingRepository meetingRepository;
    private final ParticipationLogRepository participationLogRepository;
    private final ScreenShareStateRepository screenShareStateRepository;
    private final EventPublisher eventPublisher;
    private final MeetingCompletionApplicationService meetingCompletionService;

    public LiveKitWebhookProcessingApplicationService(
            MeetingRepository meetingRepository,
            ParticipationLogRepository participationLogRepository,
            ScreenShareStateRepository screenShareStateRepository,
            EventPublisher eventPublisher,
            MeetingCompletionApplicationService meetingCompletionService) {
        this.meetingRepository = meetingRepository;
        this.participationLogRepository = participationLogRepository;
        this.screenShareStateRepository = screenShareStateRepository;
        this.eventPublisher = eventPublisher;
        this.meetingCompletionService = meetingCompletionService;
    }

    @Transactional
    public void process(LiveKitWebhookEvent event) {
        switch (event.eventType()) {
            case "room_started" -> handleRoomStarted(event);
            case "room_finished" -> handleRoomFinished(event);
            case "participant_joined" -> handleParticipantJoined(event);
            case "participant_left" -> handleParticipantLeft(event);
            case "track_published" -> handleTrackPublished(event);
            case "track_unpublished" -> handleTrackUnpublished(event);
            default ->
                log.debug("Ignoring non-handled LiveKit webhook event: {}", event.eventType());
        }
    }

    private void handleRoomStarted(LiveKitWebhookEvent event) {
        Optional<MeetingId> meetingId = parseMeetingId(event);
        if (meetingId.isEmpty()) {
            return;
        }
        Optional<Meeting> lookup = meetingRepository.findById(meetingId.get().value());
        if (lookup.isEmpty()) {
            return;
        }
        Meeting meeting = lookup.get();
        Result<Void, ?> startResult = meeting.start();
        if (startResult.isFailure()) {
            return;
        }
        meetingRepository.save(meeting);
        eventPublisher.publishEventsOf(meeting);
    }

    private void handleRoomFinished(LiveKitWebhookEvent event) {
        Optional<MeetingId> meetingId = parseMeetingId(event);
        if (meetingId.isEmpty()) {
            return;
        }
        Optional<Meeting> lookup = meetingRepository.findById(meetingId.get().value());
        if (lookup.isEmpty()) {
            return;
        }
        meetingCompletionService.completeAndCloseParticipation(lookup.get(), event.occurredAt());
    }

    private void handleParticipantJoined(LiveKitWebhookEvent event) {
        Optional<MeetingId> meetingId = parseMeetingId(event);
        if (meetingId.isEmpty()
                || event.participantIdentity() == null
                || event.participantSid() == null) {
            return;
        }
        LiveKitParticipantSid sid = LiveKitParticipantSid.of(event.participantSid());
        if (participationLogRepository.findActiveBySid(sid).isPresent()) {
            return;
        }
        LiveKitIdentity identity = LiveKitIdentity.of(event.participantIdentity());
        Optional<ParticipationLog> orphan =
                participationLogRepository.findActiveByMeetingIdAndIdentity(
                        meetingId.get().value(), identity);
        if (orphan.isPresent()) {
            ParticipationLog orphaned = orphan.get();
            orphaned.supersede(event.occurredAt());
            participationLogRepository.save(orphaned);
        }
        Optional<TenantAccountDevice> parsed = parseIdentity(event.participantIdentity());
        Optional<String> tenant = event.tenantMetadata();
        if (parsed.isEmpty() || tenant.isEmpty()) {
            return;
        }
        ParticipationLog joined = ParticipationLog.join(
                io.github.smiskinext.shared.domain.valueobject.TenantId.of(tenant.get()),
                meetingId.get(),
                parsed.get().accountId(),
                resolveRole(event),
                identity);
        joined.confirmConnected(sid, event.occurredAt());
        participationLogRepository.save(joined);
        eventPublisher.publishEventsOf(joined);
    }

    private void handleParticipantLeft(LiveKitWebhookEvent event) {
        if (event.participantSid() == null) {
            return;
        }
        LiveKitParticipantSid sid = LiveKitParticipantSid.of(event.participantSid());
        Optional<ParticipationLog> lookup = participationLogRepository.findActiveBySid(sid);
        if (lookup.isEmpty()) {
            return;
        }
        ParticipationLog session = lookup.get();
        session.recordLeft(event.occurredAt());
        participationLogRepository.save(session);
        eventPublisher.publishEventsOf(session);

        UUID meetingId = session.getMeetingId().value();
        String accountId = session.getAccountId().value();
        if (screenShareStateRepository.isSharing(meetingId, accountId)) {
            screenShareStateRepository.clearSharing(meetingId, accountId);
            session.stopScreenShare(event.occurredAt());
            eventPublisher.publishEventsOf(session);
        }
    }

    private void handleTrackPublished(LiveKitWebhookEvent event) {
        Optional<ParticipationLog> lookup = activeScreenShareSession(event);
        if (lookup.isEmpty()) {
            return;
        }
        ParticipationLog session = lookup.get();
        UUID meetingId = session.getMeetingId().value();
        String accountId = session.getAccountId().value();
        if (screenShareStateRepository.isSharing(meetingId, accountId)) {
            return;
        }
        session.startScreenShare(event.occurredAt());
        screenShareStateRepository.markSharing(meetingId, accountId, event.occurredAt());
        eventPublisher.publishEventsOf(session);
    }

    private void handleTrackUnpublished(LiveKitWebhookEvent event) {
        Optional<ParticipationLog> lookup = activeScreenShareSession(event);
        if (lookup.isEmpty()) {
            return;
        }
        ParticipationLog session = lookup.get();
        UUID meetingId = session.getMeetingId().value();
        String accountId = session.getAccountId().value();
        if (!screenShareStateRepository.isSharing(meetingId, accountId)) {
            return;
        }
        session.stopScreenShare(event.occurredAt());
        screenShareStateRepository.clearSharing(meetingId, accountId);
        eventPublisher.publishEventsOf(session);
    }

    private Optional<ParticipationLog> activeScreenShareSession(LiveKitWebhookEvent event) {
        if (!SCREEN_SHARE_SOURCE.equals(event.trackSource()) || event.participantSid() == null) {
            return Optional.empty();
        }
        return participationLogRepository.findActiveBySid(
                LiveKitParticipantSid.of(event.participantSid()));
    }

    private Optional<MeetingId> parseMeetingId(LiveKitWebhookEvent event) {
        if (event.roomName() == null || !event.roomName().startsWith(ROOM_NAME_PREFIX)) {
            return Optional.empty();
        }
        String raw = event.roomName().substring(ROOM_NAME_PREFIX.length());
        try {
            return Optional.of(MeetingId.of(UUID.fromString(raw)));
        } catch (IllegalArgumentException e) {
            log.warn("Unparseable LiveKit room name: {}", event.roomName());
            return Optional.empty();
        }
    }

    private Optional<TenantAccountDevice> parseIdentity(String identity) {
        int separator = identity.indexOf(':');
        if (separator <= 0 || separator == identity.length() - 1) {
            log.warn("Unparseable LiveKit participant identity: {}", identity);
            return Optional.empty();
        }
        String accountId = identity.substring(0, separator);
        String deviceId = identity.substring(separator + 1);
        return Optional.of(new TenantAccountDevice(AccountId.of(accountId), deviceId));
    }

    private ParticipantRole resolveRole(LiveKitWebhookEvent event) {
        String role = event.participantAttributes().get(ROLE_ATTRIBUTE);
        if (role == null) {
            return ParticipantRole.PARTICIPANT;
        }
        try {
            return ParticipantRole.valueOf(role);
        } catch (IllegalArgumentException e) {
            return ParticipantRole.PARTICIPANT;
        }
    }

    private record TenantAccountDevice(AccountId accountId, String deviceId) {}
}
