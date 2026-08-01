package io.github.smiskinext.notification.infrastructure.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import io.cloudevents.CloudEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

class InboundEmailReplyProcessorAdapterTest {

    private ResendReceivedEmailClient receivedEmailClient;
    private KafkaTemplate<String, CloudEvent> kafkaTemplate;
    private InboundEmailReplyProcessorAdapter service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        receivedEmailClient = mock(ResendReceivedEmailClient.class);
        kafkaTemplate = mock(KafkaTemplate.class);
        service = new InboundEmailReplyProcessorAdapter(receivedEmailClient, kafkaTemplate);
    }

    @Test
    void validSignedWebhookParsesAcceptedReplyAndPublishesEvent() {
        String payload = """
                {"data": {"email_id": "email-123"}}
                """;
        String calendarContent = buildReplyIcs("test-uid-123", "alice@example.com", "ACCEPTED");
        when(receivedEmailClient.fetchCalendarAttachment("email-123")).thenReturn(calendarContent);

        service.processInboundEmail(payload);

        ArgumentCaptor<CloudEvent> captor = ArgumentCaptor.forClass(CloudEvent.class);
        verify(kafkaTemplate)
                .send(
                        eq("meet.invitee.email-reply.received"),
                        eq("test-uid-123"),
                        captor.capture());

        CloudEvent event = captor.getValue();
        assertThat(event.getType())
                .isEqualTo("io.github.smiskinext.meet.invitee.email-reply.received.v1");
        String data = new String(event.getData().toBytes());
        assertThat(data).contains("test-uid-123");
        assertThat(data).contains("alice@example.com");
        assertThat(data).contains("ACCEPTED");
    }

    @Test
    void validTentativeReplyIsExtracted() {
        String payload = """
                {"data": {"email_id": "email-456"}}
                """;
        String calendarContent = buildReplyIcs("uid-456", "bob@example.com", "TENTATIVE");
        when(receivedEmailClient.fetchCalendarAttachment("email-456")).thenReturn(calendarContent);

        service.processInboundEmail(payload);

        ArgumentCaptor<CloudEvent> captor = ArgumentCaptor.forClass(CloudEvent.class);
        verify(kafkaTemplate)
                .send(eq("meet.invitee.email-reply.received"), eq("uid-456"), captor.capture());
        String data = new String(captor.getValue().getData().toBytes());
        assertThat(data).contains("TENTATIVE");
    }

    @Test
    void validDeclinedReplyIsExtracted() {
        String payload = """
                {"data": {"email_id": "email-789"}}
                """;
        String calendarContent = buildReplyIcs("uid-789", "carol@example.com", "DECLINED");
        when(receivedEmailClient.fetchCalendarAttachment("email-789")).thenReturn(calendarContent);

        service.processInboundEmail(payload);

        ArgumentCaptor<CloudEvent> captor = ArgumentCaptor.forClass(CloudEvent.class);
        verify(kafkaTemplate)
                .send(eq("meet.invitee.email-reply.received"), eq("uid-789"), captor.capture());
        String data = new String(captor.getValue().getData().toBytes());
        assertThat(data).contains("DECLINED");
    }

    @Test
    void noCalendarAttachmentPublishesNoEvent() {
        String payload = """
                {"data": {"email_id": "email-no-cal"}}
                """;
        when(receivedEmailClient.fetchCalendarAttachment("email-no-cal")).thenReturn(null);

        service.processInboundEmail(payload);

        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void unparseableCalendarPublishesNoEvent() {
        String payload = """
                {"data": {"email_id": "email-bad"}}
                """;
        when(receivedEmailClient.fetchCalendarAttachment("email-bad"))
                .thenReturn("not a valid icalendar");

        service.processInboundEmail(payload);

        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void unknownPartstatPublishesNoEvent() {
        String payload = """
                {"data": {"email_id": "email-unknown"}}
                """;
        String calendarContent = buildReplyIcs("uid-unknown", "alice@example.com", "DELEGATED");
        when(receivedEmailClient.fetchCalendarAttachment("email-unknown"))
                .thenReturn(calendarContent);

        service.processInboundEmail(payload);

        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void missingEmailIdPublishesNoEvent() {
        String payload = """
                {"data": {}}
                """;

        service.processInboundEmail(payload);

        verifyNoInteractions(kafkaTemplate);
        verifyNoInteractions(receivedEmailClient);
    }

    private String buildReplyIcs(String uid, String attendeeEmail, String partstat) {
        return """
                BEGIN:VCALENDAR
                VERSION:2.0
                METHOD:REPLY
                PRODID:-//Test//Test//EN
                BEGIN:VEVENT
                UID:%s
                DTSTART:20260101T100000Z
                DTEND:20260101T110000Z
                ATTENDEE;PARTSTAT=%s:mailto:%s
                END:VEVENT
                END:VCALENDAR
                """.formatted(uid, partstat, attendeeEmail);
    }
}
