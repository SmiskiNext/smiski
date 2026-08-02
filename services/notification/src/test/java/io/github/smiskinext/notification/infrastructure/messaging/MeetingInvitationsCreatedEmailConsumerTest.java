package io.github.smiskinext.notification.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.core.data.BytesCloudEventData;
import io.github.smiskinext.notification.application.command.SendMeetingInvitationEmailCommand;
import io.github.smiskinext.notification.application.usecase.SendMeetingInvitationEmailUseCase;
import io.github.smiskinext.notification.domain.model.CalendarEmail;
import io.github.smiskinext.notification.infrastructure.email.EmailContentBuilder;
import io.github.smiskinext.notification.infrastructure.email.IcsGenerator;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MeetingInvitationsCreatedEmailConsumerTest {

    private static final String TYPE = "io.github.smiskinext.meet.meeting.invitations.created.v1";

    private final IcsGenerator icsGenerator = mock(IcsGenerator.class);
    private final EmailContentBuilder emailContentBuilder = mock(EmailContentBuilder.class);
    private final SendMeetingInvitationEmailUseCase useCase =
            mock(SendMeetingInvitationEmailUseCase.class);

    private final MeetingInvitationsCreatedEmailConsumer consumer =
            new MeetingInvitationsCreatedEmailConsumer(icsGenerator, emailContentBuilder, useCase);

    @Test
    void validEventDelegatesToEmailContentBuilderAndSendsPerInvitee() {
        when(icsGenerator.buildRequest(any())).thenReturn("BEGIN:VCALENDAR...");
        when(emailContentBuilder.buildInvitation(anyString(), any(), any()))
                .thenAnswer(inv -> new CalendarEmail(
                        inv.getArgument(0),
                        "subject",
                        "body",
                        "<html>body</html>",
                        inv.getArgument(2),
                        "REQUEST",
                        "invite.ics"));

        consumer.onMessage(event(validJson()));

        ArgumentCaptor<SendMeetingInvitationEmailCommand> captor =
                ArgumentCaptor.forClass(SendMeetingInvitationEmailCommand.class);
        verify(useCase, times(2)).execute(captor.capture());
        var commands = captor.getAllValues();
        assertThat(commands).hasSize(2);
        assertThat(commands.stream().map(c -> c.email().recipient()))
                .containsExactlyInAnyOrder("bob@test.com", "carol@test.com");
        assertThat(commands).allMatch(c -> c.email().calendarMethod().equals("REQUEST"));
        assertThat(commands).allMatch(c -> c.email().attachmentName().equals("invite.ics"));
    }

    @Test
    void issueFieldsAreExtractedIntoEmailContext() {
        when(icsGenerator.buildRequest(any())).thenReturn("ics");
        when(emailContentBuilder.buildInvitation(anyString(), any(), any())).thenAnswer(inv -> {
            EmailContentBuilder.EmailContext ctx = inv.getArgument(1);
            assertThat(ctx.issueKey()).isEqualTo("PROJ-1");
            assertThat(ctx.shortCode()).isEqualTo("SC1");
            assertThat(ctx.tenantId()).isEqualTo("tenant-1");
            assertThat(ctx.meetingId()).isNotBlank();
            return new CalendarEmail(
                    inv.getArgument(0), "s", "b", null, "ics", "REQUEST", "invite.ics");
        });

        consumer.onMessage(event(validJson()));

        verify(emailContentBuilder, times(2)).buildInvitation(anyString(), any(), any());
    }

    @Test
    void malformedEventIsSkippedWithoutCrash() {
        consumer.onMessage(event("{bad json}"));

        verifyNoInteractions(icsGenerator);
        verifyNoInteractions(useCase);
    }

    @Test
    void missingDataIsSkippedWithoutCrash() {
        CloudEvent noData = CloudEventBuilder.v1()
                .withId(UUID.randomUUID().toString())
                .withType(TYPE)
                .withSource(URI.create("/meet"))
                .withTime(OffsetDateTime.now())
                .build();

        consumer.onMessage(noData);

        verifyNoInteractions(icsGenerator);
        verifyNoInteractions(useCase);
    }

    private static String validJson() {
        return """
                {"meetingId":"%s","tenantId":"tenant-1","meetingTitle":"Sprint",\
                "meetingShortCode":"SC1",\
                "startTime":"2026-01-15T10:00:00Z","endTime":"2026-01-15T11:00:00Z",\
                "zoneId":"UTC","organizerEmail":"host@test.com","organizerDisplayName":"Host",\
                "calendarUid":"uid-cal-1","calendarSequence":1,\
                "issueId":"issue-1","issueKey":"PROJ-1","projectKey":"PROJ",\
                "invitees":[\
                {"inviteeId":"%s","accountId":"acc-1","email":"bob@test.com","displayName":"Bob","status":"NEEDS_ACTION"},\
                {"inviteeId":"%s","accountId":"acc-2","email":"carol@test.com","displayName":"Carol","status":"NEEDS_ACTION"}\
                ]}""".formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
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
