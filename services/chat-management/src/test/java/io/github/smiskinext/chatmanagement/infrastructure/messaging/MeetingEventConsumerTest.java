package io.github.smiskinext.chatmanagement.infrastructure.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.cloudevents.CloudEvent;
import io.cloudevents.CloudEventData;
import io.github.smiskinext.chatmanagement.application.usecase.CloseChatRoomUseCase;
import io.github.smiskinext.chatmanagement.application.usecase.OpenChatRoomUseCase;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tools.jackson.databind.ObjectMapper;

/**
 * Unit tests for MeetingEventConsumer.
 *
 * <p>ObjectMapper is mocked so the test has no dependency on Jackson version, JavaTimeModule
 * availability, or the specific ObjectMapper API. The deserialize() contract (returns null on
 * failure) is exercised by mocking deserialize failures.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MeetingEventConsumerTest {

    @Mock
    private OpenChatRoomUseCase openChatRoomUseCase;

    @Mock
    private CloseChatRoomUseCase closeChatRoomUseCase;

    @Mock
    private ObjectMapper objectMapper;

    private MeetingEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer =
                new MeetingEventConsumer(openChatRoomUseCase, closeChatRoomUseCase, objectMapper);
    }

    @Test
    void onMeetingStarted_withValidMessage_callsOpenChatRoomUseCase() throws Exception {
        UUID meetingId = UUID.randomUUID();
        MeetingEventConsumer.MeetingStartedMessage msg =
                new MeetingEventConsumer.MeetingStartedMessage(
                        UUID.randomUUID(),
                        meetingId,
                        UUID.randomUUID(),
                        "livekit-1",
                        Instant.parse("2024-01-01T10:00:00Z"));

        CloudEvent cloudEvent = mockCloudEventWithData(
                "io.github.phunguy65.zms.meeting.started.v1",
                "{}".getBytes(StandardCharsets.UTF_8));
        when(objectMapper.readValue(any(byte[].class), any(Class.class))).thenReturn(msg);

        consumer.onMeetingStarted(cloudEvent);

        verify(openChatRoomUseCase).execute(meetingId.toString());
        verify(closeChatRoomUseCase, never()).execute(anyString());
    }

    @Test
    void onMeetingEnded_withValidMessage_callsCloseChatRoomUseCase() throws Exception {
        UUID meetingId = UUID.randomUUID();
        MeetingEventConsumer.MeetingEndedMessage msg = new MeetingEventConsumer.MeetingEndedMessage(
                UUID.randomUUID(),
                meetingId,
                UUID.randomUUID(),
                Instant.parse("2024-01-01T11:00:00Z"));

        CloudEvent cloudEvent = mockCloudEventWithData(
                "io.github.phunguy65.zms.meeting.ended.v1", "{}".getBytes(StandardCharsets.UTF_8));
        when(objectMapper.readValue(any(byte[].class), any(Class.class))).thenReturn(msg);

        consumer.onMeetingEnded(cloudEvent);

        verify(closeChatRoomUseCase).execute(meetingId.toString());
        verify(openChatRoomUseCase, never()).execute(anyString());
    }

    @Test
    void onMeetingStarted_withNullData_doesNotCrash() {
        CloudEvent cloudEvent = mock(CloudEvent.class);
        when(cloudEvent.getData()).thenReturn(null);
        when(cloudEvent.getId()).thenReturn("evt-null-data");
        // Note: objectMapper is NOT stubbed — deserialize() returns early before calling it

        consumer.onMeetingStarted(cloudEvent);

        verify(openChatRoomUseCase, never()).execute(anyString());
    }

    @Test
    void onMeetingStarted_whenDeserializeThrows_doesNotCrash() {
        CloudEvent cloudEvent = mockCloudEventWithData(
                "io.github.phunguy65.zms.meeting.started.v1",
                "{}".getBytes(StandardCharsets.UTF_8));
        // Only stub objectMapper — getData() is stubbed in mockCloudEventWithData()
        when(objectMapper.readValue(any(byte[].class), any(Class.class)))
                .thenThrow(new RuntimeException("deserialize error"));

        consumer.onMeetingStarted(cloudEvent);

        verify(openChatRoomUseCase, never()).execute(anyString());
    }

    @Test
    void onMeetingEnded_withNullData_doesNotCrash() {
        CloudEvent cloudEvent = mock(CloudEvent.class);
        when(cloudEvent.getData()).thenReturn(null);
        when(cloudEvent.getId()).thenReturn("evt-null-ended");
        // Note: objectMapper is NOT stubbed — deserialize() returns early before calling it

        consumer.onMeetingEnded(cloudEvent);

        verify(closeChatRoomUseCase, never()).execute(anyString());
    }

    @Test
    void onMeetingEnded_whenDeserializeThrows_doesNotCrash() {
        CloudEvent cloudEvent = mockCloudEventWithData(
                "io.github.phunguy65.zms.meeting.ended.v1", "{}".getBytes(StandardCharsets.UTF_8));
        // Only stub objectMapper — getData() is stubbed in mockCloudEventWithData()
        when(objectMapper.readValue(any(byte[].class), any(Class.class)))
                .thenThrow(new RuntimeException("deserialize error"));

        consumer.onMeetingEnded(cloudEvent);

        verify(closeChatRoomUseCase, never()).execute(anyString());
    }

    @Test
    void onMeetingCancelled_withValidMessage_callsCloseChatRoomUseCase() throws Exception {
        UUID meetingId = UUID.randomUUID();
        MeetingEventConsumer.MeetingCancelledMessage msg =
                new MeetingEventConsumer.MeetingCancelledMessage(
                        UUID.randomUUID(),
                        meetingId,
                        UUID.randomUUID(),
                        "Planning Session",
                        "ABC1234567",
                        Instant.parse("2024-01-01T10:00:00Z"),
                        java.util.List.of(),
                        Instant.parse("2024-01-01T09:00:00Z"));

        CloudEvent cloudEvent = mockCloudEventWithData(
                "io.github.phunguy65.zms.meeting.cancelled.v1",
                "{}".getBytes(StandardCharsets.UTF_8));
        when(objectMapper.readValue(any(byte[].class), any(Class.class))).thenReturn(msg);

        consumer.onMeetingCancelled(cloudEvent);

        verify(closeChatRoomUseCase).execute(meetingId.toString());
        verify(openChatRoomUseCase, never()).execute(anyString());
    }

    @Test
    void onMeetingCancelled_withNullData_doesNotCrash() {
        CloudEvent cloudEvent = mock(CloudEvent.class);
        when(cloudEvent.getData()).thenReturn(null);
        when(cloudEvent.getId()).thenReturn("evt-null-cancelled");

        consumer.onMeetingCancelled(cloudEvent);

        verify(closeChatRoomUseCase, never()).execute(anyString());
    }

    @Test
    void onMeetingCancelled_whenDeserializeThrows_doesNotCrash() {
        CloudEvent cloudEvent = mockCloudEventWithData(
                "io.github.phunguy65.zms.meeting.cancelled.v1",
                "{}".getBytes(StandardCharsets.UTF_8));
        when(objectMapper.readValue(any(byte[].class), any(Class.class)))
                .thenThrow(new RuntimeException("deserialize error"));

        consumer.onMeetingCancelled(cloudEvent);

        verify(closeChatRoomUseCase, never()).execute(anyString());
    }

    private CloudEvent mockCloudEvent(String type) {
        CloudEvent event = mock(CloudEvent.class);
        lenient().when(event.getId()).thenReturn(UUID.randomUUID().toString());
        lenient().when(event.getType()).thenReturn(type);
        return event;
    }

    /**
     * Creates a CloudEvent with a real CloudEventData lambda.
     * Use this for the "valid path" tests where deserialize() is called.
     */
    private CloudEvent mockCloudEventWithData(String type, byte[] jsonBytes) {
        CloudEvent event = mockCloudEvent(type);
        CloudEventData data = mock(CloudEventData.class);
        when(data.toBytes()).thenReturn(jsonBytes);
        when(event.getData()).thenReturn(data);
        return event;
    }
}
