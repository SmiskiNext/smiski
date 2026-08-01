package io.github.smiskinext.notification.infrastructure.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.core.data.BytesCloudEventData;
import io.github.smiskinext.notification.domain.model.CalendarEmail;
import io.github.smiskinext.notification.domain.port.EmailSender;
import io.github.smiskinext.notification.infrastructure.email.IcsGenerator;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MeetingInfoUpdatedEmailConsumerTest {

    private static final String TYPE = "io.github.smiskinext.meet.meeting.info.updated.v1";

    private final IcsGenerator icsGenerator = mock(IcsGenerator.class);
    private final EmailSender emailSender = mock(EmailSender.class);

    private final MeetingInfoUpdatedEmailConsumer consumer =
            new MeetingInfoUpdatedEmailConsumer(icsGenerator, emailSender);

    @Test
    void timeChangeWithInviteesSendsOneEmailPerInvitee() {
        when(icsGenerator.buildRequest(any())).thenReturn("BEGIN:VCALENDAR...");

        consumer.onMessage(event(timeChangeWithInviteesJson()));

        ArgumentCaptor<CalendarEmail> captor = ArgumentCaptor.forClass(CalendarEmail.class);
        verify(emailSender, times(2)).send(captor.capture());
        var emails = captor.getAllValues();
        assert emails.stream().anyMatch(e -> e.recipient().equals("bob@test.com"));
        assert emails.stream().anyMatch(e -> e.recipient().equals("carol@test.com"));
        assert emails.stream().allMatch(e -> e.calendarMethod().equals("REQUEST"));
        assert emails.stream().allMatch(e -> e.attachmentName().equals("invite.ics"));
    }

    @Test
    void nonTimeChangeProducesNoEmail() {
        consumer.onMessage(event(nonTimeChangeJson()));

        verifyNoInteractions(icsGenerator);
        verifyNoInteractions(emailSender);
    }

    @Test
    void timeChangeWithNoInviteesProducesNoEmail() {
        consumer.onMessage(event(timeChangeNoInviteesJson()));

        verifyNoInteractions(icsGenerator);
        verifyNoInteractions(emailSender);
    }

    @Test
    void malformedEventIsSkippedWithoutCrash() {
        consumer.onMessage(event("{this is not valid json}"));

        verifyNoInteractions(icsGenerator);
        verifyNoInteractions(emailSender);
    }

    @Test
    void missingDataIsSkippedWithoutCrash() {
        CloudEvent event = CloudEventBuilder.v1()
                .withId(UUID.randomUUID().toString())
                .withType(TYPE)
                .withSource(URI.create("/meet"))
                .withTime(OffsetDateTime.now())
                .build();

        consumer.onMessage(event);

        verifyNoInteractions(icsGenerator);
        verifyNoInteractions(emailSender);
    }

    private static String timeChangeWithInviteesJson() {
        return """
                {"meetingId":"%s","tenantId":"tenant-1","hostId":"host-1","updatedBy":"host-1",\
                "status":"SCHEDULED",\
                "oldInfo":{"title":"Sprint","description":"desc","zoneId":"UTC",\
                "startTime":"2026-01-01T10:00:00Z","endTime":"2026-01-01T11:00:00Z",\
                "organizerEmail":"host@example.com","organizerDisplayName":"Host",\
                "calendarUid":"uid-123","calendarSequence":1,\
                "issueLink":{"issueId":"1","issueKey":"PROJ-1","projectKey":"PROJ"}},\
                "newInfo":{"title":"Sprint","description":"desc","zoneId":"UTC",\
                "startTime":"2026-01-01T12:00:00Z","endTime":"2026-01-01T13:00:00Z",\
                "organizerEmail":"host@example.com","organizerDisplayName":"Host",\
                "calendarUid":"uid-123","calendarSequence":2,\
                "issueLink":{"issueId":"1","issueKey":"PROJ-1","projectKey":"PROJ"}},\
                "updatedAt":"2026-01-01T09:00:00Z",\
                "invitees":[{"accountId":"a1","email":"bob@test.com","displayName":"Bob","inviteeId":"%s","status":"NEEDS_ACTION"},\
                {"accountId":"a2","email":"carol@test.com","displayName":"Carol","inviteeId":"%s","status":"ACCEPTED"}]}""".formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    }

    private static String nonTimeChangeJson() {
        return """
                {"meetingId":"%s","tenantId":"tenant-1","hostId":"host-1","updatedBy":"host-1",\
                "status":"SCHEDULED",\
                "oldInfo":{"title":"Sprint","description":"old desc","zoneId":"UTC",\
                "startTime":"2026-01-01T10:00:00Z","endTime":"2026-01-01T11:00:00Z",\
                "organizerEmail":"host@example.com","organizerDisplayName":"Host",\
                "calendarUid":"uid-123","calendarSequence":1,\
                "issueLink":{"issueId":"1","issueKey":"PROJ-1","projectKey":"PROJ"}},\
                "newInfo":{"title":"Sprint Planning","description":"new desc","zoneId":"UTC",\
                "startTime":"2026-01-01T10:00:00Z","endTime":"2026-01-01T11:00:00Z",\
                "organizerEmail":"host@example.com","organizerDisplayName":"Host",\
                "calendarUid":"uid-123","calendarSequence":2,\
                "issueLink":{"issueId":"1","issueKey":"PROJ-1","projectKey":"PROJ"}},\
                "updatedAt":"2026-01-01T09:00:00Z",\
                "invitees":[{"accountId":"a1","email":"bob@test.com","displayName":"Bob","inviteeId":"%s","status":"NEEDS_ACTION"}]}""".formatted(UUID.randomUUID(), UUID.randomUUID());
    }

    private static String timeChangeNoInviteesJson() {
        return """
                {"meetingId":"%s","tenantId":"tenant-1","hostId":"host-1","updatedBy":"host-1",\
                "status":"SCHEDULED",\
                "oldInfo":{"title":"Sprint","description":"desc","zoneId":"UTC",\
                "startTime":"2026-01-01T10:00:00Z","endTime":"2026-01-01T11:00:00Z",\
                "organizerEmail":"host@example.com","organizerDisplayName":"Host",\
                "calendarUid":"uid-123","calendarSequence":1,\
                "issueLink":{"issueId":"1","issueKey":"PROJ-1","projectKey":"PROJ"}},\
                "newInfo":{"title":"Sprint","description":"desc","zoneId":"UTC",\
                "startTime":"2026-01-01T14:00:00Z","endTime":"2026-01-01T15:00:00Z",\
                "organizerEmail":"host@example.com","organizerDisplayName":"Host",\
                "calendarUid":"uid-123","calendarSequence":2,\
                "issueLink":{"issueId":"1","issueKey":"PROJ-1","projectKey":"PROJ"}},\
                "updatedAt":"2026-01-01T09:00:00Z",\
                "invitees":[]}""".formatted(UUID.randomUUID());
    }

    private static CloudEvent event(String json) {
        return CloudEventBuilder.v1()
                .withId(UUID.randomUUID().toString())
                .withType(TYPE)
                .withSource(URI.create("/meet"))
                .withDataContentType("application/json")
                .withTime(OffsetDateTime.now())
                .withData(
                        "application/json",
                        BytesCloudEventData.wrap(json.getBytes(StandardCharsets.UTF_8)))
                .build();
    }
}
