package io.github.smiskinext.notification.infrastructure.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.cloudevents.CloudEvent;
import io.cloudevents.CloudEventData;
import io.github.smiskinext.notification.application.usecase.SendMeetingCancelledEmailUseCase;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class MeetingCancelledNotificationConsumerTest {

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private SendMeetingCancelledEmailUseCase useCase;

    @Mock
    private CloudEvent cloudEvent;

    @Mock
    private CloudEventData cloudEventData;

    private MeetingCancelledNotificationConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new MeetingCancelledNotificationConsumer(objectMapper, useCase);
    }

    @Test
    void sendsOneEmailPerInvitee() {
        MeetingCancelledMessage message = new MeetingCancelledMessage(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Planning Session",
                "ABC1234567",
                Instant.parse("2026-04-03T10:00:00Z"),
                List.of(
                        new MeetingCancelledMessage.InviteeInfo(
                                UUID.randomUUID(),
                                "alice@example.com",
                                "Alice",
                                "PENDING",
                                Instant.parse("2026-04-02T09:00:00Z")),
                        new MeetingCancelledMessage.InviteeInfo(
                                UUID.randomUUID(),
                                "bob@example.com",
                                "Bob",
                                "ACCEPTED",
                                Instant.parse("2026-04-02T09:05:00Z"))),
                Instant.parse("2026-04-02T11:00:00Z"));
        when(cloudEvent.getData()).thenReturn(cloudEventData);
        when(cloudEventData.toBytes()).thenReturn("{}".getBytes(StandardCharsets.UTF_8));
        when(objectMapper.readValue(any(byte[].class), any(Class.class))).thenReturn(message);

        consumer.onMeetingCancelled(cloudEvent);

        verify(useCase).send(message, message.invitees().get(0));
        verify(useCase).send(message, message.invitees().get(1));
    }

    @Test
    void skipsEventWhenInviteesAreEmpty() {
        MeetingCancelledMessage message = new MeetingCancelledMessage(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Planning Session",
                "ABC1234567",
                Instant.parse("2026-04-03T10:00:00Z"),
                List.of(),
                Instant.parse("2026-04-02T11:00:00Z"));
        when(cloudEvent.getData()).thenReturn(cloudEventData);
        when(cloudEventData.toBytes()).thenReturn("{}".getBytes(StandardCharsets.UTF_8));
        when(objectMapper.readValue(any(byte[].class), any(Class.class))).thenReturn(message);

        consumer.onMeetingCancelled(cloudEvent);

        verifyNoInteractions(useCase);
    }

    @Test
    void skipsEventWhenCloudEventDataIsMissing() {
        when(cloudEvent.getId()).thenReturn("evt-cancelled-1");
        when(cloudEvent.getData()).thenReturn(null);

        consumer.onMeetingCancelled(cloudEvent);

        verifyNoInteractions(useCase);
        verify(objectMapper, never()).readValue(any(byte[].class), any(Class.class));
    }

    @Test
    void skipsEventWhenDeserializationFails() {
        when(cloudEvent.getId()).thenReturn("evt-cancelled-2");
        when(cloudEvent.getData()).thenReturn(cloudEventData);
        when(cloudEventData.toBytes()).thenReturn("bad-json".getBytes(StandardCharsets.UTF_8));
        when(objectMapper.readValue(any(byte[].class), any(Class.class)))
                .thenThrow(new RuntimeException("boom"));

        consumer.onMeetingCancelled(cloudEvent);

        verifyNoInteractions(useCase);
    }

    @Test
    void continuesSendingWhenOneInviteeFails() {
        MeetingCancelledMessage message = new MeetingCancelledMessage(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Planning Session",
                "ABC1234567",
                Instant.parse("2026-04-03T10:00:00Z"),
                List.of(
                        new MeetingCancelledMessage.InviteeInfo(
                                UUID.randomUUID(),
                                "alice@example.com",
                                "Alice",
                                "PENDING",
                                Instant.parse("2026-04-02T09:00:00Z")),
                        new MeetingCancelledMessage.InviteeInfo(
                                UUID.randomUUID(),
                                "bob@example.com",
                                "Bob",
                                "ACCEPTED",
                                Instant.parse("2026-04-02T09:05:00Z"))),
                Instant.parse("2026-04-02T11:00:00Z"));
        when(cloudEvent.getData()).thenReturn(cloudEventData);
        when(cloudEventData.toBytes()).thenReturn("{}".getBytes(StandardCharsets.UTF_8));
        when(objectMapper.readValue(any(byte[].class), any(Class.class))).thenReturn(message);
        doThrow(new IllegalStateException("provider down"))
                .when(useCase)
                .send(message, message.invitees().get(0));

        consumer.onMeetingCancelled(cloudEvent);

        verify(useCase).send(message, message.invitees().get(0));
        verify(useCase).send(message, message.invitees().get(1));
    }

    @Test
    void skipsInviteesWithUnexpectedStatus() {
        MeetingCancelledMessage message = new MeetingCancelledMessage(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Planning Session",
                "ABC1234567",
                Instant.parse("2026-04-03T10:00:00Z"),
                List.of(new MeetingCancelledMessage.InviteeInfo(
                        UUID.randomUUID(),
                        "carol@example.com",
                        "Carol",
                        "DECLINED",
                        Instant.parse("2026-04-02T09:05:00Z"))),
                Instant.parse("2026-04-02T11:00:00Z"));
        when(cloudEvent.getData()).thenReturn(cloudEventData);
        when(cloudEventData.toBytes()).thenReturn("{}".getBytes(StandardCharsets.UTF_8));
        when(objectMapper.readValue(any(byte[].class), any(Class.class))).thenReturn(message);

        consumer.onMeetingCancelled(cloudEvent);

        verifyNoInteractions(useCase);
    }

    @Test
    void sendsForDuplicateInviteesWhenPayloadContainsDuplicates() {
        MeetingCancelledMessage message = new MeetingCancelledMessage(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Planning Session",
                "ABC1234567",
                Instant.parse("2026-04-03T10:00:00Z"),
                List.of(
                        new MeetingCancelledMessage.InviteeInfo(
                                UUID.randomUUID(),
                                "alice@example.com",
                                "Alice",
                                "PENDING",
                                Instant.parse("2026-04-02T09:00:00Z")),
                        new MeetingCancelledMessage.InviteeInfo(
                                UUID.randomUUID(),
                                "alice@example.com",
                                "Alice",
                                "ACCEPTED",
                                Instant.parse("2026-04-02T09:00:01Z"))),
                Instant.parse("2026-04-02T11:00:00Z"));
        when(cloudEvent.getData()).thenReturn(cloudEventData);
        when(cloudEventData.toBytes()).thenReturn("{}".getBytes(StandardCharsets.UTF_8));
        when(objectMapper.readValue(any(byte[].class), any(Class.class))).thenReturn(message);

        consumer.onMeetingCancelled(cloudEvent);

        verify(useCase).send(message, message.invitees().get(0));
        verify(useCase).send(message, message.invitees().get(1));
    }
}
