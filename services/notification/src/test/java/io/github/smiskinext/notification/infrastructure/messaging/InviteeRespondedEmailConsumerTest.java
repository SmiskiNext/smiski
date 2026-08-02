package io.github.smiskinext.notification.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.core.data.BytesCloudEventData;
import io.github.smiskinext.notification.application.command.SendInviteeRespondedEmailCommand;
import io.github.smiskinext.notification.application.usecase.SendInviteeRespondedEmailUseCase;
import io.github.smiskinext.notification.domain.model.CalendarEmail;
import io.github.smiskinext.notification.infrastructure.email.EmailContentBuilder;
import io.github.smiskinext.notification.infrastructure.email.IcsGenerator;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class InviteeRespondedEmailConsumerTest {

    private static final String TYPE_ACCEPTED = "io.github.smiskinext.meet.invitee.accepted.v1";
    private static final String TYPE_DECLINED = "io.github.smiskinext.meet.invitee.declined.v1";
    private static final String TYPE_TENTATIVE = "io.github.smiskinext.meet.invitee.tentative.v1";

    private final IcsGenerator icsGenerator = mock(IcsGenerator.class);
    private final EmailContentBuilder emailContentBuilder = mock(EmailContentBuilder.class);
    private final SendInviteeRespondedEmailUseCase useCase =
            mock(SendInviteeRespondedEmailUseCase.class);

    private final InviteeRespondedEmailConsumer consumer =
            new InviteeRespondedEmailConsumer(icsGenerator, emailContentBuilder, useCase);

    @Test
    void acceptedEventBuildsResponseEmailAndSends() {
        when(icsGenerator.buildReply(any())).thenReturn("BEGIN:VCALENDAR...");
        when(emailContentBuilder.buildResponse(anyString(), any(), anyString(), anyString(), any()))
                .thenAnswer(inv -> new CalendarEmail(
                        inv.getArgument(0),
                        "subj",
                        "body",
                        null,
                        inv.getArgument(4),
                        "REPLY",
                        "reply.ics"));

        consumer.onMessage(event(TYPE_ACCEPTED, responseJson("ACCEPTED")));

        ArgumentCaptor<SendInviteeRespondedEmailCommand> captor =
                ArgumentCaptor.forClass(SendInviteeRespondedEmailCommand.class);
        verify(useCase).execute(captor.capture());
        assertThat(captor.getValue().email().recipient()).isEqualTo("organizer@test.com");
        assertThat(captor.getValue().email().calendarMethod()).isEqualTo("REPLY");
    }

    @Test
    void declinedEventBuildsResponseEmailAndSends() {
        when(icsGenerator.buildReply(any())).thenReturn("ics");
        when(emailContentBuilder.buildResponse(anyString(), any(), anyString(), anyString(), any()))
                .thenAnswer(inv -> new CalendarEmail(
                        inv.getArgument(0),
                        "subj",
                        "body",
                        null,
                        inv.getArgument(4),
                        "REPLY",
                        "reply.ics"));

        consumer.onMessage(event(TYPE_DECLINED, responseJson("DECLINED")));

        verify(useCase).execute(any());
    }

    @Test
    void tentativeEventBuildsResponseEmailAndSends() {
        when(icsGenerator.buildReply(any())).thenReturn("ics");
        when(emailContentBuilder.buildResponse(anyString(), any(), anyString(), anyString(), any()))
                .thenAnswer(inv -> new CalendarEmail(
                        inv.getArgument(0),
                        "subj",
                        "body",
                        null,
                        inv.getArgument(4),
                        "REPLY",
                        "reply.ics"));

        consumer.onMessage(event(TYPE_TENTATIVE, responseJson("TENTATIVE")));

        verify(useCase).execute(any());
    }

    @Test
    void malformedEventIsSkippedWithoutCrash() {
        consumer.onMessage(event(TYPE_ACCEPTED, "{bad json}"));

        verifyNoInteractions(icsGenerator);
        verifyNoInteractions(useCase);
    }

    @Test
    void missingDataIsSkippedWithoutCrash() {
        CloudEvent noData = CloudEventBuilder.v1()
                .withId(UUID.randomUUID().toString())
                .withType(TYPE_ACCEPTED)
                .withSource(URI.create("/meet"))
                .withTime(OffsetDateTime.now())
                .build();

        consumer.onMessage(noData);

        verifyNoInteractions(icsGenerator);
        verifyNoInteractions(useCase);
    }

    private static String responseJson(String status) {
        return """
                {"eventId":"%s","tenantId":"tenant-1","meetingId":"%s",\
                "inviterId":"host-1","inviteeId":"%s",\
                "inviteeEmail":"invitee@test.com","inviteeDisplayName":"Invitee",\
                "status":"%s","acceptedAt":"2026-01-01T10:00:00Z","declinedAt":"2026-01-01T10:00:00Z","tentativeAt":"2026-01-01T10:00:00Z",\
                "meetingTitle":"Sprint","startTime":"2026-01-01T10:00:00Z","endTime":"2026-01-01T11:00:00Z",\
                "zoneId":"UTC","organizerEmail":"organizer@test.com","organizerDisplayName":"Organizer",\
                "calendarUid":"uid-1","calendarSequence":1,\
                "issueId":"issue-1","issueKey":"PROJ-1","projectKey":"PROJ","shortCode":"SC1"}""".formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), status);
    }

    private static CloudEvent event(String type, String json) {
        return CloudEventBuilder.v1()
                .withId(UUID.randomUUID().toString())
                .withType(type)
                .withSource(URI.create("/meet"))
                .withDataContentType("application/json")
                .withTime(OffsetDateTime.now())
                .withData(
                        "application/json",
                        BytesCloudEventData.wrap(json.getBytes(StandardCharsets.UTF_8)))
                .build();
    }
}
