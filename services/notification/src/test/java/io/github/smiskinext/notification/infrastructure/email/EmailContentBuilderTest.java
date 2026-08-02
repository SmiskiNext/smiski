package io.github.smiskinext.notification.infrastructure.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.github.smiskinext.notification.domain.model.CalendarEmail;
import io.github.smiskinext.notification.domain.port.TenantProjectionRepository;
import io.github.smiskinext.notification.infrastructure.email.EmailContentBuilder.EmailContext;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;

class EmailContentBuilderTest {

    private final MessageSource messageSource = mock(MessageSource.class);
    private final EmailProperties emailProperties = mock(EmailProperties.class);
    private final TenantProjectionRepository tenantProjectionRepository =
            mock(TenantProjectionRepository.class);

    private final EmailContentBuilder builder =
            new EmailContentBuilder(messageSource, emailProperties, tenantProjectionRepository);

    EmailContentBuilderTest() {
        when(emailProperties.getDefaultLocale()).thenReturn("en");
        when(messageSource.getMessage(any(), any(), any(), any()))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void buildInvitationWithAllFieldsAndSiteUrlContainsJiraLink() {
        when(tenantProjectionRepository.findSiteUrl("tenant-1"))
                .thenReturn(Optional.of("https://site.atlassian.net"));

        EmailContext ctx = new EmailContext(
                "tenant-1",
                "meeting-123",
                "Sprint Planning",
                Instant.parse("2026-03-01T10:00:00Z"),
                Instant.parse("2026-03-01T11:00:00Z"),
                "UTC",
                "Alice",
                "ABC123",
                "PROJ-1");

        CalendarEmail email = builder.buildInvitation("bob@test.com", ctx, "BEGIN:VCALENDAR");

        assertThat(email.recipient()).isEqualTo("bob@test.com");
        assertThat(email.subject()).contains("email.invitation.subject");
        assertThat(email.calendarMethod()).isEqualTo("REQUEST");
        assertThat(email.attachmentName()).isEqualTo("invite.ics");
        assertThat(email.icsContent()).isEqualTo("BEGIN:VCALENDAR");
        assertThat(email.htmlBody()).contains("https://site.atlassian.net/browse/PROJ-1");
        assertThat(email.htmlBody()).contains("ABC123");
        assertThat(email.htmlBody()).contains("meeting-123");
        assertThat(email.htmlBody()).contains("Alice");
    }

    @Test
    void buildInvitationWithEmptySiteUrlOmitsJiraLink() {
        when(tenantProjectionRepository.findSiteUrl("tenant-2")).thenReturn(Optional.empty());

        EmailContext ctx = new EmailContext(
                "tenant-2",
                "meeting-456",
                "Standup",
                Instant.parse("2026-03-01T10:00:00Z"),
                Instant.parse("2026-03-01T11:00:00Z"),
                "UTC",
                "Alice",
                "XYZ",
                "PROJ-2");

        CalendarEmail email = builder.buildInvitation("bob@test.com", ctx, "ics");

        assertThat(email.htmlBody()).doesNotContain("/browse/");
    }

    @Test
    void buildInvitationWithBlankIssueKeyOmitsJiraLinkAndSkipsLookup() {
        EmailContext ctx = new EmailContext(
                "tenant-3",
                "meeting-789",
                "Retro",
                Instant.parse("2026-03-01T10:00:00Z"),
                Instant.parse("2026-03-01T11:00:00Z"),
                "UTC",
                "Alice",
                "DEF",
                "");

        CalendarEmail email = builder.buildInvitation("bob@test.com", ctx, "ics");

        assertThat(email.htmlBody()).doesNotContain("/browse/");
        verify(tenantProjectionRepository, never()).findSiteUrl(any());
    }

    @Test
    void buildInvitationWithNullTitleUsesFallbackKey() {
        EmailContext ctx = new EmailContext(
                "tenant-1",
                "meeting-1",
                null,
                Instant.parse("2026-03-01T10:00:00Z"),
                Instant.parse("2026-03-01T11:00:00Z"),
                "UTC",
                "Alice",
                "SHORT",
                null);

        CalendarEmail email = builder.buildInvitation("bob@test.com", ctx, "ics");

        assertThat(email.htmlBody()).contains("email.fallback.meeting-title");
        assertThat(email.subject()).isEqualTo("email.invitation.subject");
    }

    @Test
    void buildInvitationWithNullStartTimeUsesFallbackUnscheduled() {
        EmailContext ctx = new EmailContext(
                "tenant-1", "meeting-1", "Meeting", null, null, "UTC", "Alice", "SHORT", null);

        CalendarEmail email = builder.buildInvitation("bob@test.com", ctx, "ics");

        assertThat(email.htmlBody()).contains("email.fallback.unscheduled");
    }

    @Test
    void buildResponseUsesReplyMethodAndReplyIcsAttachment() {
        EmailContext ctx = new EmailContext(
                "tenant-1",
                "meeting-1",
                "Sprint",
                Instant.parse("2026-03-01T10:00:00Z"),
                Instant.parse("2026-03-01T11:00:00Z"),
                "UTC",
                "Alice",
                "SHORT",
                null);

        CalendarEmail email =
                builder.buildResponse("organizer@test.com", ctx, "Bob", "ACCEPTED", "ics-reply");

        assertThat(email.recipient()).isEqualTo("organizer@test.com");
        assertThat(email.subject()).contains("email.response.subject");
        assertThat(email.body()).contains("email.response.body.text");
        assertThat(email.calendarMethod()).isEqualTo("REPLY");
        assertThat(email.attachmentName()).isEqualTo("reply.ics");
        assertThat(email.htmlBody()).contains("Bob");
        assertThat(email.htmlBody()).contains("ACCEPTED");
    }

    @Test
    void buildUpdateUsesRequestMethodAndInviteIcsAttachment() {
        EmailContext ctx = new EmailContext(
                "tenant-1",
                "meeting-1",
                "Sprint",
                Instant.parse("2026-03-01T10:00:00Z"),
                Instant.parse("2026-03-01T11:00:00Z"),
                "UTC",
                "Alice",
                "SHORT",
                null);

        CalendarEmail email = builder.buildUpdate("invitee@test.com", ctx, "ics-update");

        assertThat(email.calendarMethod()).isEqualTo("REQUEST");
        assertThat(email.attachmentName()).isEqualTo("invite.ics");
        assertThat(email.subject()).contains("email.update.subject");
    }
}
