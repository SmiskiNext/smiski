package io.github.smiskinext.chatmanagement.infrastructure.messaging;

import io.cloudevents.CloudEvent;
import io.github.smiskinext.chatmanagement.application.usecase.CreateSystemMessageUseCase;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Consumes participant lifecycle events (joined / left / kicked) from Kafka and creates
 * system chat messages in the corresponding room.
 */
@Component
public class ParticipantEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ParticipantEventConsumer.class);

    private final CreateSystemMessageUseCase createSystemMessageUseCase;
    private final ObjectMapper objectMapper;

    public ParticipantEventConsumer(
            CreateSystemMessageUseCase createSystemMessageUseCase, ObjectMapper objectMapper) {
        this.createSystemMessageUseCase = createSystemMessageUseCase;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "meeting-management.participant.joined",
            groupId = "chat-management-participant",
            containerFactory = "participantEventKafkaListenerContainerFactory")
    public void onParticipantJoined(CloudEvent cloudEvent) {
        ParticipantJoinedMessage event = deserialize(cloudEvent, ParticipantJoinedMessage.class);
        if (event == null) return;
        String content = formatJoinedMessage(event.displayName());
        log.debug(
                "Participant {} joined meeting {}, broadcasting system message",
                event.displayName(),
                event.meetingId());
        createSystemMessageUseCase.execute(event.meetingId().toString(), content);
    }

    @KafkaListener(
            topics = "meeting-management.participant.left",
            groupId = "chat-management-participant",
            containerFactory = "participantEventKafkaListenerContainerFactory")
    public void onParticipantLeft(CloudEvent cloudEvent) {
        ParticipantLeftMessage event = deserialize(cloudEvent, ParticipantLeftMessage.class);
        if (event == null) return;
        String content = formatLeftMessage(event.displayName());
        log.debug(
                "Participant {} left meeting {}, broadcasting system message",
                event.displayName(),
                event.meetingId());
        createSystemMessageUseCase.execute(event.meetingId().toString(), content);
    }

    @KafkaListener(
            topics = "meeting-management.participant.kicked",
            groupId = "chat-management-participant",
            containerFactory = "participantEventKafkaListenerContainerFactory")
    public void onParticipantKicked(CloudEvent cloudEvent) {
        ParticipantKickedMessage event = deserialize(cloudEvent, ParticipantKickedMessage.class);
        if (event == null) return;
        String displayName =
                event.kickedDisplayName() != null ? event.kickedDisplayName() : "Unknown";
        String content = formatKickedMessage(displayName);
        log.debug(
                "Participant {} kicked from meeting {}, broadcasting system message",
                displayName,
                event.meetingId());
        createSystemMessageUseCase.execute(event.meetingId().toString(), content);
    }

    private <T> T deserialize(CloudEvent cloudEvent, Class<T> type) {
        if (cloudEvent.getData() == null) {
            log.warn("Received CloudEvent with no data payload: {}", cloudEvent.getId());
            return null;
        }
        try {
            return objectMapper.readValue(cloudEvent.getData().toBytes(), type);
        } catch (Exception e) {
            log.error(
                    "Failed to deserialize CloudEvent {} to {}: {}",
                    cloudEvent.getId(),
                    type.getSimpleName(),
                    e.getMessage());
            return null;
        }
    }

    private static String formatJoinedMessage(String displayName) {
        return displayName + " đã tham gia cuộc họp";
    }

    private static String formatLeftMessage(String displayName) {
        return displayName + " đã rời cuộc họp";
    }

    private static String formatKickedMessage(String displayName) {
        return displayName + " đã bị xóa khỏi cuộc họp";
    }

    // DTOs for Kafka event payloads

    record ParticipantJoinedMessage(
            UUID eventId,
            UUID meetingId,
            @Nullable UUID userId,
            String displayName,
            Instant occurredAt) {}

    record ParticipantLeftMessage(
            UUID eventId,
            UUID meetingId,
            @Nullable UUID userId,
            String displayName,
            Instant occurredAt) {}

    record ParticipantKickedMessage(
            UUID eventId,
            UUID meetingId,
            UUID kickedBy,
            @Nullable UUID kickedUserId,
            @Nullable String kickedDisplayName,
            Instant occurredAt) {}
}
