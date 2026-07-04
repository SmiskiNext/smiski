package io.github.smiskinext.chatmanagement.infrastructure.messaging;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.cloudevents.CloudEvent;
import io.cloudevents.CloudEventData;
import io.github.smiskinext.chatmanagement.application.usecase.CreateSystemMessageUseCase;
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
 * Unit tests for ParticipantEventConsumer.
 *
 * <p>Verifies that participant lifecycle events (joined/left/kicked) are transformed into
 * Vietnamese system messages and forwarded to CreateSystemMessageUseCase.
 *
 * <p>Uses a real {@link ObjectMapper} (Jackson 3.x) to serialize test payloads, avoiding
 * any mismatch between the test's mock argument matchers and the actual ObjectMapper API.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ParticipantEventConsumerTest {

    @Mock
    private CreateSystemMessageUseCase createSystemMessageUseCase;

    private ParticipantEventConsumer consumer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        consumer = new ParticipantEventConsumer(createSystemMessageUseCase, objectMapper);
    }

    // ─── onParticipantJoined ─────────────────────────────────────────────────

    @Test
    void onParticipantJoined_callsUseCaseWithJoinedMessage() throws Exception {
        String displayName = "Alice";
        UUID meetingId = UUID.randomUUID();
        ParticipantEventConsumer.ParticipantJoinedMessage msg =
                new ParticipantEventConsumer.ParticipantJoinedMessage(
                        UUID.randomUUID(),
                        meetingId,
                        UUID.randomUUID(),
                        displayName,
                        Instant.now());

        CloudEventData data = () -> objectMapper.writeValueAsBytes(msg);
        CloudEvent cloudEvent =
                mockCloudEvent("io.github.phunguy65.zms.meeting.participant.joined.v1", data);

        consumer.onParticipantJoined(cloudEvent);

        verify(createSystemMessageUseCase)
                .execute(meetingId.toString(), displayName + " đã tham gia cuộc họp");
    }

    @Test
    void onParticipantJoined_withNullData_doesNotCrash() {
        CloudEvent cloudEvent = mockCloudEvent();
        when(cloudEvent.getData()).thenReturn(null);
        when(cloudEvent.getId()).thenReturn("evt-null-data");

        consumer.onParticipantJoined(cloudEvent);

        verify(createSystemMessageUseCase, never()).execute(anyString(), anyString());
    }

    @Test
    void onParticipantJoined_whenDeserializeThrows_doesNotCrash() {
        CloudEventData badData = () -> "not valid json {{{".getBytes(StandardCharsets.UTF_8);
        CloudEvent cloudEvent =
                mockCloudEvent("io.github.phunguy65.zms.meeting.participant.joined.v1", badData);

        consumer.onParticipantJoined(cloudEvent);

        verify(createSystemMessageUseCase, never()).execute(anyString(), anyString());
    }

    // ─── onParticipantLeft ───────────────────────────────────────────────────

    @Test
    void onParticipantLeft_callsUseCaseWithLeftMessage() throws Exception {
        String displayName = "Bob";
        UUID meetingId = UUID.randomUUID();
        ParticipantEventConsumer.ParticipantLeftMessage msg =
                new ParticipantEventConsumer.ParticipantLeftMessage(
                        UUID.randomUUID(),
                        meetingId,
                        UUID.randomUUID(),
                        displayName,
                        Instant.now());

        CloudEventData data = () -> objectMapper.writeValueAsBytes(msg);
        CloudEvent cloudEvent =
                mockCloudEvent("io.github.phunguy65.zms.meeting.participant.left.v1", data);

        consumer.onParticipantLeft(cloudEvent);

        verify(createSystemMessageUseCase)
                .execute(meetingId.toString(), displayName + " đã rời cuộc họp");
    }

    @Test
    void onParticipantLeft_withNullData_doesNotCrash() {
        CloudEvent cloudEvent = mockCloudEvent();
        when(cloudEvent.getData()).thenReturn(null);
        when(cloudEvent.getId()).thenReturn("evt-null-left");

        consumer.onParticipantLeft(cloudEvent);

        verify(createSystemMessageUseCase, never()).execute(anyString(), anyString());
    }

    // ─── onParticipantKicked ─────────────────────────────────────────────────

    @Test
    void onParticipantKicked_callsUseCaseWithKickedMessage() throws Exception {
        String displayName = "Charlie";
        UUID meetingId = UUID.randomUUID();
        ParticipantEventConsumer.ParticipantKickedMessage msg =
                new ParticipantEventConsumer.ParticipantKickedMessage(
                        UUID.randomUUID(),
                        meetingId,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        displayName,
                        Instant.now());

        CloudEventData data = () -> objectMapper.writeValueAsBytes(msg);
        CloudEvent cloudEvent =
                mockCloudEvent("io.github.phunguy65.zms.meeting.participant.kicked.v1", data);

        consumer.onParticipantKicked(cloudEvent);

        verify(createSystemMessageUseCase)
                .execute(meetingId.toString(), displayName + " đã bị xóa khỏi cuộc họp");
    }

    @Test
    void onParticipantKicked_withNullDisplayName_usesUnknown() throws Exception {
        UUID meetingId = UUID.randomUUID();
        ParticipantEventConsumer.ParticipantKickedMessage msg =
                new ParticipantEventConsumer.ParticipantKickedMessage(
                        UUID.randomUUID(),
                        meetingId,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        null,
                        Instant.now());

        CloudEventData data = () -> objectMapper.writeValueAsBytes(msg);
        CloudEvent cloudEvent =
                mockCloudEvent("io.github.phunguy65.zms.meeting.participant.kicked.v1", data);

        consumer.onParticipantKicked(cloudEvent);

        verify(createSystemMessageUseCase)
                .execute(meetingId.toString(), "Unknown đã bị xóa khỏi cuộc họp");
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /** CloudEvent with no data. */
    private CloudEvent mockCloudEvent() {
        CloudEvent event = mock(CloudEvent.class);
        when(event.getId()).thenReturn(UUID.randomUUID().toString());
        // getData() → null (not stubbed)
        return event;
    }

    /** CloudEvent with typed data payload. */
    private CloudEvent mockCloudEvent(String type, CloudEventData data) {
        CloudEvent event = mock(CloudEvent.class);
        when(event.getId()).thenReturn(UUID.randomUUID().toString());
        when(event.getType()).thenReturn(type);
        when(event.getData()).thenReturn(data);
        return event;
    }
}
