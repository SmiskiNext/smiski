package io.github.smiskinext.chatmanagement.infrastructure.messaging;

import io.cloudevents.CloudEvent;
import io.github.smiskinext.chatmanagement.application.usecase.CloseChatRoomUseCase;
import io.github.smiskinext.chatmanagement.application.usecase.OpenChatRoomUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Consumes meeting lifecycle events (started / ended / cancelled) from Kafka and opens/closes
 * chat rooms.
 */
@Component
public class MeetingEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(MeetingEventConsumer.class);

    private final OpenChatRoomUseCase openChatRoomUseCase;
    private final CloseChatRoomUseCase closeChatRoomUseCase;
    private final ObjectMapper objectMapper;

    public MeetingEventConsumer(
            OpenChatRoomUseCase openChatRoomUseCase,
            CloseChatRoomUseCase closeChatRoomUseCase,
            ObjectMapper objectMapper) {
        this.openChatRoomUseCase = openChatRoomUseCase;
        this.closeChatRoomUseCase = closeChatRoomUseCase;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "meeting-management.meeting.started",
            groupId = "chat-management-meeting",
            containerFactory = "cloudEventKafkaListenerContainerFactory")
    public void onMeetingStarted(CloudEvent cloudEvent) {
        MeetingStartedMessage event = deserialize(cloudEvent, MeetingStartedMessage.class);
        if (event == null) return;
        log.debug("Received meeting.started event for meeting {}", event.aggregateId());
        openChatRoomUseCase.execute(event.aggregateId().toString());
    }

    @KafkaListener(
            topics = "meeting-management.meeting.ended",
            groupId = "chat-management-meeting",
            containerFactory = "cloudEventKafkaListenerContainerFactory")
    public void onMeetingEnded(CloudEvent cloudEvent) {
        MeetingEndedMessage event = deserialize(cloudEvent, MeetingEndedMessage.class);
        if (event == null) return;
        log.debug("Received meeting.ended event for meeting {}", event.aggregateId());
        closeChatRoomUseCase.execute(event.aggregateId().toString());
    }

    @KafkaListener(
            topics = "meeting-management.meeting.cancelled",
            groupId = "chat-management-meeting",
            containerFactory = "cloudEventKafkaListenerContainerFactory")
    public void onMeetingCancelled(CloudEvent cloudEvent) {
        MeetingCancelledMessage event = deserialize(cloudEvent, MeetingCancelledMessage.class);
        if (event == null) return;
        log.debug("Received meeting.cancelled event for meeting {}", event.aggregateId());
        closeChatRoomUseCase.execute(event.aggregateId().toString());
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

    /** DTO for {@code meeting-management.meeting.started}. */
    record MeetingStartedMessage(
            java.util.UUID eventId,
            java.util.UUID aggregateId,
            java.util.UUID hostId,
            String liveKitRoomName,
            java.time.Instant startedAt) {}

    /** DTO for {@code meeting-management.meeting.ended}. */
    record MeetingEndedMessage(
            java.util.UUID eventId,
            java.util.UUID aggregateId,
            java.util.UUID hostId,
            java.time.Instant endedAt) {}

    /** DTO for {@code meeting-management.meeting.cancelled}. */
    record MeetingCancelledMessage(
            java.util.UUID eventId,
            java.util.UUID aggregateId,
            java.util.UUID hostId,
            String meetingTitle,
            String meetingShortCode,
            java.time.Instant startTime,
            java.util.List<?> invitees,
            java.time.Instant cancelledAt) {}
}
