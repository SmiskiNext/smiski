package io.github.smiskinext.notification.infrastructure.email;

import io.github.smiskinext.notification.domain.model.CalendarEmail;
import io.github.smiskinext.notification.domain.port.TenantProjectionRepository;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

/**
 * Builds localised, multipart (plain-text + HTML) calendar emails for meeting events.
 *
 * <p>Delegates text content to the {@link MessageSource} and resolves the Jira deep-link from the
 * local tenant projection. When the site URL or issue key is unavailable the deep-link is simply
 * omitted — no error is raised.
 */
@Component
public class EmailContentBuilder {

    private final MessageSource messageSource;
    private final EmailProperties emailProperties;
    private final TenantProjectionRepository tenantProjectionRepository;

    public EmailContentBuilder(
            MessageSource messageSource,
            EmailProperties emailProperties,
            TenantProjectionRepository tenantProjectionRepository) {
        this.messageSource = messageSource;
        this.emailProperties = emailProperties;
        this.tenantProjectionRepository = tenantProjectionRepository;
    }

    /**
     * Contextual data shared across all email types.
     *
     * @param tenantId      tenant identifier used for site-URL lookup
     * @param meetingId     meeting UUID
     * @param title         meeting title, or {@code null} when untitled
     * @param startTime     scheduled start, or {@code null} when unscheduled
     * @param endTime       scheduled end, or {@code null} when unscheduled
     * @param zoneId        IANA time zone of the meeting host
     * @param organizerName display name of the meeting organizer
     * @param shortCode     human-friendly meeting short code
     * @param issueKey      Jira issue key (e.g. {@code PROJ-123}), or {@code null}
     */
    public record EmailContext(
            String tenantId,
            String meetingId,
            @Nullable String title,
            @Nullable Instant startTime,
            @Nullable Instant endTime,
            String zoneId,
            String organizerName,
            String shortCode,
            @Nullable String issueKey) {}

    /**
     * Builds a meeting invitation email for a single invitee.
     */
    public CalendarEmail buildInvitation(String recipient, EmailContext ctx, String icsContent) {
        Locale locale = resolveLocale();
        String displayTitle = resolveTitle(ctx.title(), locale);
        String subject = msg("email.invitation.subject", locale, displayTitle);
        String body = msg("email.invitation.body.text", locale, displayTitle);
        String htmlBody = buildHtmlBody(ctx, displayTitle, locale, null, null);
        return new CalendarEmail(
                recipient, subject, body, htmlBody, icsContent, "REQUEST", "invite.ics");
    }

    /**
     * Builds a meeting-updated email for a single invitee.
     */
    public CalendarEmail buildUpdate(String recipient, EmailContext ctx, String icsContent) {
        Locale locale = resolveLocale();
        String displayTitle = resolveTitle(ctx.title(), locale);
        String subject = msg("email.update.subject", locale, displayTitle);
        String body = msg("email.update.body.text", locale, displayTitle);
        String htmlBody = buildHtmlBody(ctx, displayTitle, locale, null, null);
        return new CalendarEmail(
                recipient, subject, body, htmlBody, icsContent, "REQUEST", "invite.ics");
    }

    /**
     * Builds an invitee-response notification email sent to the organizer.
     */
    public CalendarEmail buildResponse(
            String recipient,
            EmailContext ctx,
            String inviteeName,
            String responseStatus,
            String icsContent) {
        Locale locale = resolveLocale();
        String displayTitle = resolveTitle(ctx.title(), locale);
        String subject = msg("email.response.subject", locale, inviteeName, displayTitle);
        String body = msg("email.response.body.text", locale, inviteeName, responseStatus);
        String htmlBody = buildHtmlBody(ctx, displayTitle, locale, inviteeName, responseStatus);
        return new CalendarEmail(
                recipient, subject, body, htmlBody, icsContent, "REPLY", "reply.ics");
    }

    private String buildHtmlBody(
            EmailContext ctx,
            String displayTitle,
            Locale locale,
            @Nullable String inviteeName,
            @Nullable String responseStatus) {
        String timeDisplay = formatTime(ctx.startTime(), ctx.endTime(), ctx.zoneId(), locale);
        String jiraLink = resolveJiraLink(ctx.tenantId(), ctx.issueKey());

        StringBuilder html = new StringBuilder(512);
        html.append("<div style=\"font-family:sans-serif;max-width:600px\">");
        html.append("<h2>").append(escape(displayTitle)).append("</h2>");

        if (inviteeName != null && responseStatus != null) {
            html.append("<p><strong>")
                    .append(escape(inviteeName))
                    .append("</strong> — ")
                    .append(escape(responseStatus))
                    .append("</p>");
        }

        html.append("<table style=\"border-collapse:collapse;width:100%\">");
        appendRow(html, msg("email.html.label.time", locale), timeDisplay);
        appendRow(html, msg("email.html.label.organizer", locale), ctx.organizerName());
        appendRow(html, msg("email.html.label.short-code", locale), ctx.shortCode());
        appendRow(html, msg("email.html.label.meeting-id", locale), ctx.meetingId());
        html.append("</table>");

        if (jiraLink != null) {
            String linkLabel = msg("email.html.label.jira-link", locale);
            html.append("<p><a href=\"")
                    .append(escape(jiraLink))
                    .append("\">")
                    .append(escape(linkLabel))
                    .append("</a></p>");
        }

        html.append("</div>");
        return html.toString();
    }

    private static void appendRow(StringBuilder html, String label, String value) {
        html.append("<tr><td style=\"padding:4px 8px;font-weight:bold\">")
                .append(escape(label))
                .append("</td><td style=\"padding:4px 8px\">")
                .append(escape(value))
                .append("</td></tr>");
    }

    private String formatTime(
            @Nullable Instant startTime, @Nullable Instant endTime, String zoneId, Locale locale) {
        if (startTime == null) {
            return msg("email.fallback.unscheduled", locale);
        }
        DateTimeFormatter formatter =
                DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(locale);
        ZoneId zone = ZoneId.of(zoneId);
        String start = formatter.format(startTime.atZone(zone));
        if (endTime == null) {
            return start;
        }
        String end = formatter.format(endTime.atZone(zone));
        return start + " \u2013 " + end;
    }

    private @Nullable String resolveJiraLink(String tenantId, @Nullable String issueKey) {
        if (issueKey == null || issueKey.isBlank()) {
            return null;
        }
        return tenantProjectionRepository
                .findSiteUrl(tenantId)
                .map(siteUrl -> siteUrl + "/browse/" + issueKey)
                .orElse(null);
    }

    private String resolveTitle(@Nullable String title, Locale locale) {
        if (title == null || title.isBlank()) {
            return msg("email.fallback.meeting-title", locale);
        }
        return title;
    }

    private Locale resolveLocale() {
        return Locale.forLanguageTag(emailProperties.getDefaultLocale());
    }

    private String msg(String code, Locale locale, Object... args) {
        return messageSource.getMessage(code, args.length == 0 ? null : args, code, locale);
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
