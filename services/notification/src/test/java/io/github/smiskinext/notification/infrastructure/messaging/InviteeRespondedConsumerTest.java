package io.github.smiskinext.notification.infrastructure.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.cloudevents.CloudEvent;
import io.cloudevents.CloudEventData;
import io.github.smiskinext.notification.application.usecase.SendInviteeRespondedEmailUseCase;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class InviteeRespondedConsumerTest {

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private SendInviteeRespondedEmailUseCase useCase;

    @Mock
    private CloudEvent cloudEvent;

    @Mock
    private CloudEventData cloudEventData;

    private InviteeRespondedConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new InviteeRespondedConsumer(objectMapper, useCase);
    }

    @Test
    void processesAcceptedEvent() {
        InviteeRespondedMessage message = acceptedMessage();
        when(cloudEvent.getData()).thenReturn(cloudEventData);
        when(cloudEventData.toBytes()).thenReturn("{}".getBytes(StandardCharsets.UTF_8));
        when(objectMapper.readValue(any(byte[].class), any(Class.class))).thenReturn(message);

        consumer.onInviteeResponded(cloudEvent);

        verify(useCase).send(message);
    }

    @Test
    void processesDeclinedEvent() {
        InviteeRespondedMessage message = declinedMessage();
        when(cloudEvent.getData()).thenReturn(cloudEventData);
        when(cloudEventData.toBytes()).thenReturn("{}".getBytes(StandardCharsets.UTF_8));
        when(objectMapper.readValue(any(byte[].class), any(Class.class))).thenReturn(message);

        consumer.onInviteeResponded(cloudEvent);

        verify(useCase).send(message);
    }

    @Test
    void skipsEventWhenCloudEventDataIsMissing() {
        when(cloudEvent.getId()).thenReturn("evt-1");
        when(cloudEvent.getData()).thenReturn(null);

        consumer.onInviteeResponded(cloudEvent);

        verifyNoInteractions(useCase);
    }

    @Test
    void skipsEventWhenDeserializationFails() {
        when(cloudEvent.getId()).thenReturn("evt-2");
        when(cloudEvent.getData()).thenReturn(cloudEventData);
        when(cloudEventData.toBytes()).thenReturn("bad-json".getBytes(StandardCharsets.UTF_8));
        when(objectMapper.readValue(any(byte[].class), any(Class.class)))
                .thenThrow(new RuntimeException("boom"));

        consumer.onInviteeResponded(cloudEvent);

        verifyNoInteractions(useCase);
    }

    @Test
    void isolatesUseCaseFailure() {
        InviteeRespondedMessage message = acceptedMessage();
        when(cloudEvent.getData()).thenReturn(cloudEventData);
        when(cloudEventData.toBytes()).thenReturn("{}".getBytes(StandardCharsets.UTF_8));
        when(objectMapper.readValue(any(byte[].class), any(Class.class))).thenReturn(message);
        doThrow(new IllegalStateException("provider down")).when(useCase).send(message);

        consumer.onInviteeResponded(cloudEvent);

        verify(useCase).send(message);
    }

    private InviteeRespondedMessage acceptedMessage() {
        return new InviteeRespondedMessage(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Planning Session",
                null,
                null,
                Instant.parse("2026-04-02T10:00:00Z"),
                Instant.parse("2026-04-02T10:00:00Z"),
                null,
                null);
    }

    private InviteeRespondedMessage declinedMessage() {
        return new InviteeRespondedMessage(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Planning Session",
                null,
                null,
                Instant.parse("2026-04-02T10:00:00Z"),
                null,
                Instant.parse("2026-04-02T10:00:00Z"),
                null);
    }
}
