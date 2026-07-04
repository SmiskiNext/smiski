package io.github.smiskinext.notification.infrastructure.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.cloudevents.CloudEvent;
import io.cloudevents.CloudEventData;
import io.github.smiskinext.notification.application.usecase.SendInviteUpdatedEmailUseCase;
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
class MeetingInviteTokensInvalidatedConsumerTest {

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private SendInviteUpdatedEmailUseCase useCase;

    @Mock
    private CloudEvent cloudEvent;

    @Mock
    private CloudEventData cloudEventData;

    private MeetingInviteTokensInvalidatedConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new MeetingInviteTokensInvalidatedConsumer(objectMapper, useCase);
    }

    @Test
    void sendsOneEmailPerAffectedInvitee() {
        MeetingInviteTokensInvalidatedMessage message = new MeetingInviteTokensInvalidatedMessage(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Q2 Planning",
                "ABC1234567",
                List.of(
                        new MeetingInviteTokensInvalidatedMessage.AffectedInviteeInfo(
                                UUID.randomUUID(), UUID.randomUUID(), "alice@example.com", "Alice"),
                        new MeetingInviteTokensInvalidatedMessage.AffectedInviteeInfo(
                                UUID.randomUUID(), UUID.randomUUID(), "bob@example.com", "Bob")),
                Instant.parse("2026-04-28T10:00:00Z"));
        when(cloudEvent.getData()).thenReturn(cloudEventData);
        when(cloudEventData.toBytes()).thenReturn("{msg}".getBytes(StandardCharsets.UTF_8));
        when(objectMapper.readValue(any(byte[].class), any(Class.class))).thenReturn(message);

        consumer.onMeetingInviteTokensInvalidated(cloudEvent);

        verify(useCase).send(message, message.affectedInvitees().get(0));
        verify(useCase).send(message, message.affectedInvitees().get(1));
    }

    @Test
    void skipsEventWhenAffectedInviteesAreEmpty() {
        MeetingInviteTokensInvalidatedMessage message = new MeetingInviteTokensInvalidatedMessage(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Q2 Planning",
                "ABC1234567",
                List.of(),
                Instant.parse("2026-04-28T10:00:00Z"));
        when(cloudEvent.getData()).thenReturn(cloudEventData);
        when(cloudEventData.toBytes()).thenReturn("{msg}".getBytes(StandardCharsets.UTF_8));
        when(objectMapper.readValue(any(byte[].class), any(Class.class))).thenReturn(message);

        consumer.onMeetingInviteTokensInvalidated(cloudEvent);

        verifyNoInteractions(useCase);
    }

    @Test
    void skipsEventWhenCloudEventDataIsMissing() {
        when(cloudEvent.getId()).thenReturn("evt-invalidated-1");
        when(cloudEvent.getData()).thenReturn(null);

        consumer.onMeetingInviteTokensInvalidated(cloudEvent);

        verifyNoInteractions(useCase);
        verify(objectMapper, never()).readValue(any(byte[].class), any(Class.class));
    }

    @Test
    void skipsEventWhenDeserializationFails() {
        when(cloudEvent.getId()).thenReturn("evt-invalidated-2");
        when(cloudEvent.getData()).thenReturn(cloudEventData);
        when(cloudEventData.toBytes()).thenReturn("bad-json".getBytes(StandardCharsets.UTF_8));
        when(objectMapper.readValue(any(byte[].class), any(Class.class)))
                .thenThrow(new RuntimeException("boom"));

        consumer.onMeetingInviteTokensInvalidated(cloudEvent);

        verifyNoInteractions(useCase);
    }

    @Test
    void continuesSendingWhenOneInviteeFails() {
        MeetingInviteTokensInvalidatedMessage message = new MeetingInviteTokensInvalidatedMessage(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Q2 Planning",
                "ABC1234567",
                List.of(
                        new MeetingInviteTokensInvalidatedMessage.AffectedInviteeInfo(
                                UUID.randomUUID(), UUID.randomUUID(), "alice@example.com", "Alice"),
                        new MeetingInviteTokensInvalidatedMessage.AffectedInviteeInfo(
                                UUID.randomUUID(), UUID.randomUUID(), "bob@example.com", "Bob")),
                Instant.parse("2026-04-28T10:00:00Z"));
        when(cloudEvent.getData()).thenReturn(cloudEventData);
        when(cloudEventData.toBytes()).thenReturn("{msg}".getBytes(StandardCharsets.UTF_8));
        when(objectMapper.readValue(any(byte[].class), any(Class.class))).thenReturn(message);
        doThrow(new IllegalStateException("provider down"))
                .when(useCase)
                .send(message, message.affectedInvitees().get(0));

        consumer.onMeetingInviteTokensInvalidated(cloudEvent);

        verify(useCase).send(message, message.affectedInvitees().get(0));
        verify(useCase).send(message, message.affectedInvitees().get(1));
    }

    @Test
    void deduplicatesNotificationsForSameEmailInPayload() {
        UUID inviteeId1 = UUID.randomUUID();
        UUID inviteeId2 = UUID.randomUUID();
        MeetingInviteTokensInvalidatedMessage.AffectedInviteeInfo first =
                new MeetingInviteTokensInvalidatedMessage.AffectedInviteeInfo(
                        inviteeId1, UUID.randomUUID(), "alice@example.com", "Alice");
        MeetingInviteTokensInvalidatedMessage.AffectedInviteeInfo duplicate =
                new MeetingInviteTokensInvalidatedMessage.AffectedInviteeInfo(
                        inviteeId2, UUID.randomUUID(), "alice@example.com", "Alice");
        MeetingInviteTokensInvalidatedMessage message = new MeetingInviteTokensInvalidatedMessage(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Q2 Planning",
                "ABC1234567",
                List.of(first, duplicate),
                Instant.parse("2026-04-28T10:00:00Z"));
        when(cloudEvent.getData()).thenReturn(cloudEventData);
        when(cloudEventData.toBytes()).thenReturn("{msg}".getBytes(StandardCharsets.UTF_8));
        when(objectMapper.readValue(any(byte[].class), any(Class.class))).thenReturn(message);

        consumer.onMeetingInviteTokensInvalidated(cloudEvent);

        verify(useCase, times(1)).send(any(), any());
        verify(useCase).send(message, first);
        verify(useCase, never()).send(message, duplicate);
    }
}
