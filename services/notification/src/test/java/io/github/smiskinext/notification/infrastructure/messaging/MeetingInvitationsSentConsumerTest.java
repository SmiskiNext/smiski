package io.github.smiskinext.notification.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.cloudevents.CloudEvent;
import io.cloudevents.CloudEventData;
import io.github.smiskinext.notification.application.usecase.SendMeetingInvitationEmailUseCase;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class MeetingInvitationsSentConsumerTest {

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private SendMeetingInvitationEmailUseCase useCase;

    @Mock
    private CloudEvent cloudEvent;

    @Mock
    private CloudEventData cloudEventData;

    private MeetingInvitationsSentConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new MeetingInvitationsSentConsumer(objectMapper, useCase);
    }

    @Test
    void sendsOneEmailPerInvitee() {
        MeetingInvitationsSentMessage message = new MeetingInvitationsSentMessage(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Planning Session",
                "ABC1234567",
                Instant.parse("2026-04-03T10:00:00Z"),
                List.of(
                        new MeetingInvitationsSentMessage.InviteeInfo(
                                UUID.randomUUID(), "alice@example.com", "Alice"),
                        new MeetingInvitationsSentMessage.InviteeInfo(
                                UUID.randomUUID(), "bob@example.com", "Bob")),
                Map.of(),
                Instant.parse("2026-04-02T09:00:00Z"));
        when(cloudEvent.getData()).thenReturn(cloudEventData);
        when(cloudEventData.toBytes()).thenReturn("{}".getBytes(StandardCharsets.UTF_8));
        when(objectMapper.readValue(any(byte[].class), any(Class.class))).thenReturn(message);

        consumer.onMeetingInvitationsSent(cloudEvent);

        verify(useCase).send(message, message.invitees().get(0));
        verify(useCase).send(message, message.invitees().get(1));
    }

    @Test
    void skipsEventWhenInviteesAreEmpty() {
        MeetingInvitationsSentMessage message = new MeetingInvitationsSentMessage(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Planning Session",
                "ABC1234567",
                Instant.parse("2026-04-03T10:00:00Z"),
                List.of(),
                null,
                Instant.parse("2026-04-02T09:00:00Z"));
        when(cloudEvent.getData()).thenReturn(cloudEventData);
        when(cloudEventData.toBytes()).thenReturn("{}".getBytes(StandardCharsets.UTF_8));
        when(objectMapper.readValue(any(byte[].class), any(Class.class))).thenReturn(message);

        consumer.onMeetingInvitationsSent(cloudEvent);

        verifyNoInteractions(useCase);
    }

    @Test
    void skipsEventWhenCloudEventDataIsMissing() {
        when(cloudEvent.getId()).thenReturn("evt-3");
        when(cloudEvent.getData()).thenReturn(null);

        consumer.onMeetingInvitationsSent(cloudEvent);

        verifyNoInteractions(useCase);
        verify(objectMapper, never()).readValue(any(byte[].class), any(Class.class));
    }

    @Test
    void skipsEventWhenDeserializationFails() {
        when(cloudEvent.getId()).thenReturn("evt-4");
        when(cloudEvent.getData()).thenReturn(cloudEventData);
        when(cloudEventData.toBytes()).thenReturn("bad-json".getBytes(StandardCharsets.UTF_8));
        when(objectMapper.readValue(any(byte[].class), any(Class.class)))
                .thenThrow(new RuntimeException("boom"));

        consumer.onMeetingInvitationsSent(cloudEvent);

        verifyNoInteractions(useCase);
    }

    @Test
    void continuesSendingWhenOneInviteeFails() {
        MeetingInvitationsSentMessage message = new MeetingInvitationsSentMessage(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Planning Session",
                "ABC1234567",
                Instant.parse("2026-04-03T10:00:00Z"),
                List.of(
                        new MeetingInvitationsSentMessage.InviteeInfo(
                                UUID.randomUUID(), "alice@example.com", "Alice"),
                        new MeetingInvitationsSentMessage.InviteeInfo(
                                UUID.randomUUID(), "bob@example.com", "Bob")),
                Map.of(),
                Instant.parse("2026-04-02T09:00:00Z"));
        when(cloudEvent.getData()).thenReturn(cloudEventData);
        when(cloudEventData.toBytes()).thenReturn("{}".getBytes(StandardCharsets.UTF_8));
        when(objectMapper.readValue(any(byte[].class), any(Class.class))).thenReturn(message);
        doThrow(new IllegalStateException("provider down"))
                .when(useCase)
                .send(message, message.invitees().get(0));

        consumer.onMeetingInvitationsSent(cloudEvent);

        verify(useCase).send(message, message.invitees().get(0));
        verify(useCase).send(message, message.invitees().get(1));
    }

    @Test
    void logsEventIdWithoutExposingInviteTokens() {
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        List<String> capturedMessages = new ArrayList<>();
        AbstractAppender capturingAppender =
                new AbstractAppender("test-capture", null, null, true, Property.EMPTY_ARRAY) {
                    @Override
                    public void append(LogEvent event) {
                        capturedMessages.add(event.getMessage().getFormattedMessage());
                    }
                };
        capturingAppender.start();
        org.apache.logging.log4j.core.Logger log4jLogger =
                context.getLogger(MeetingInvitationsSentConsumer.class.getName());
        log4jLogger.addAppender(capturingAppender);
        log4jLogger.setLevel(Level.INFO);
        log4jLogger.setAdditive(true);

        UUID inviteeRecordId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        assertThat(inviteeRecordId).isNotEqualTo(userId);
        MeetingInvitationsSentMessage message = new MeetingInvitationsSentMessage(
                UUID.fromString("00000000-0000-0000-0000-000000000111"),
                UUID.randomUUID(),
                "Planning Session",
                "ABC1234567",
                Instant.parse("2026-04-03T10:00:00Z"),
                List.of(new MeetingInvitationsSentMessage.InviteeInfo(
                        userId, "alice@example.com", "Alice")),
                Map.of(userId, "secret-raw-token"),
                Instant.parse("2026-04-02T09:00:00Z"));
        when(cloudEvent.getData()).thenReturn(cloudEventData);
        when(cloudEventData.toBytes()).thenReturn("{}".getBytes(StandardCharsets.UTF_8));
        when(objectMapper.readValue(any(byte[].class), any(Class.class))).thenReturn(message);

        consumer.onMeetingInvitationsSent(cloudEvent);

        log4jLogger.removeAppender(capturingAppender);

        String allLogMessages = String.join(" ", capturedMessages);
        assertThat(allLogMessages)
                .contains("00000000-0000-0000-0000-000000000111")
                .doesNotContain("password=")
                .doesNotContain("secret-raw-token");
    }
}
