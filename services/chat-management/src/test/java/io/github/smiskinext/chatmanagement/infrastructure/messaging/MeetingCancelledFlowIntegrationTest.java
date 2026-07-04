package io.github.smiskinext.chatmanagement.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.github.smiskinext.chatmanagement.application.port.ChatRoomRepository;
import io.github.smiskinext.chatmanagement.application.usecase.CloseChatRoomUseCase;
import io.github.smiskinext.chatmanagement.application.usecase.OpenChatRoomUseCase;
import io.github.smiskinext.chatmanagement.domain.model.ChatRoom;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class MeetingCancelledFlowIntegrationTest {

    @Test
    void cancelledCloudEvent_archivesExistingChatRoom() {
        String meetingId = UUID.randomUUID().toString();
        InMemoryChatRoomRepository repository = new InMemoryChatRoomRepository();
        ChatRoom room = ChatRoom.create(meetingId);
        repository.save(room);

        MeetingEventConsumer consumer = new MeetingEventConsumer(
                new OpenChatRoomUseCase(repository),
                new CloseChatRoomUseCase(repository),
                new ObjectMapper());

        CloudEvent cloudEvent = CloudEventBuilder.v1()
                .withId(UUID.randomUUID().toString())
                .withType("io.github.phunguy65.zms.meeting.cancelled.v1")
                .withSource(URI.create("test://meeting-management"))
                .withDataContentType("application/json")
                .withData(("""
                        {
                          "eventId":"%s",
                          "aggregateId":"%s",
                          "hostId":"%s",
                          "meetingTitle":"Planning Session",
                          "meetingShortCode":"ABC1234567",
                          "startTime":"2026-04-03T10:00:00Z",
                          "invitees":[],
                          "cancelledAt":"2026-04-02T11:00:00Z"
                        }
                        """)
                        .formatted(UUID.randomUUID(), meetingId, UUID.randomUUID())
                        .getBytes(StandardCharsets.UTF_8))
                .build();

        consumer.onMeetingCancelled(cloudEvent);

        assertThat(repository.findByRoomId(meetingId))
                .isPresent()
                .get()
                .extracting(ChatRoom::getStatus)
                .isEqualTo(ChatRoom.RoomStatus.ARCHIVED);
    }

    private static final class InMemoryChatRoomRepository implements ChatRoomRepository {
        private final Map<String, ChatRoom> rooms = new HashMap<>();

        @Override
        public ChatRoom save(ChatRoom room) {
            room.setUpdatedAt(Instant.now());
            rooms.put(room.getRoomId(), room);
            return room;
        }

        @Override
        public Optional<ChatRoom> findByRoomId(String roomId) {
            return Optional.ofNullable(rooms.get(roomId));
        }

        @Override
        public boolean existsByRoomId(String roomId) {
            return rooms.containsKey(roomId);
        }
    }
}
