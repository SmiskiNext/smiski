package io.github.smiskinext.meetingmanagement.infrastructure.messaging;

import io.cloudevents.CloudEvent;
import io.github.smiskinext.meetingmanagement.domain.MeetingError;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.LiveKitRoomName;
import io.github.smiskinext.meetingmanagement.domain.port.LiveKitPort;
import io.github.smiskinext.meetingmanagement.domain.port.ParticipationLogRepository;
import java.util.UUID;

import io.github.smiskinext.shared.domain.Result;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class UserProfileUpdatedConsumer {

    private static final Logger log = LoggerFactory.getLogger(UserProfileUpdatedConsumer.class);

    private final ParticipationLogRepository participationLogRepository;
    private final LiveKitPort liveKitPort;
    private final ObjectMapper objectMapper;

    public UserProfileUpdatedConsumer(
            ParticipationLogRepository participationLogRepository,
            LiveKitPort liveKitPort,
            ObjectMapper objectMapper) {
        this.participationLogRepository = participationLogRepository;
        this.liveKitPort = liveKitPort;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "user-management.user.updated",
            groupId = "meeting-user-profile",
            containerFactory = "cloudEventKafkaListenerContainerFactory")
    public void onUserUpdated(CloudEvent cloudEvent) {
        UserUpdatedEvent event = deserialize(cloudEvent);
        if (event == null) return;

        var activeLogs = participationLogRepository.findActiveByUserId(event.userId());
        if (activeLogs.isEmpty()) {
            log.debug("No active meeting sessions found for updated user {}", event.userId());
            return;
        }

        for (var logEntry : activeLogs) {
            var result = liveKitPort.updateParticipantProfile(
                    LiveKitRoomName.fromMeetingId(logEntry.getMeetingId()),
                    logEntry.getLivekitIdentity().value(),
                    logEntry.getRole(),
                    event.fullName(),
                    event.avatarUrl());

            if (result
                    instanceof
                    Result.Failure<Void, MeetingError>
                            failure) {
                if (failure.error() instanceof MeetingError.LiveKitParticipantNotFound notFound) {
                    log.debug(
                            "Skipping stale LiveKit participant '{}' in room '{}': {}",
                            logEntry.getLivekitIdentity().value(),
                            logEntry.getMeetingId().value(),
                            notFound.message());
                } else {
                    log.warn(
                            "Failed to sync user profile for participant '{}' in room '{}': {}",
                            logEntry.getLivekitIdentity().value(),
                            logEntry.getMeetingId().value(),
                            failure.error().message());
                }
            }
        }
    }

    private @Nullable UserUpdatedEvent deserialize(CloudEvent cloudEvent) {
        if (cloudEvent.getData() == null) {
            log.warn(
                    "Received user.updated CloudEvent with no data payload: {}",
                    cloudEvent.getId());
            return null;
        }
        try {
            return objectMapper.readValue(cloudEvent.getData().toBytes(), UserUpdatedEvent.class);
        } catch (Exception e) {
            log.error(
                    "Failed to deserialize user.updated CloudEvent {}: {}",
                    cloudEvent.getId(),
                    e.getMessage());
            return null;
        }
    }

    private record UserUpdatedEvent(
            UUID userId, String fullName, @Nullable String avatarUrl) {}
}
