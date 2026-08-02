package io.github.smiskinext.notification.domain.model;

/**
 * An email carrying a calendar attachment to a single recipient.
 *
 * @param recipient      destination email address
 * @param subject        email subject line
 * @param body           human-readable plain-text body
 * @param htmlBody       HTML body, or {@code null} when only plain text is required
 * @param icsContent     the serialized iCalendar object
 * @param calendarMethod the iCalendar {@code METHOD} (e.g. {@code REQUEST}, {@code REPLY}) applied
 *                       as the attachment's {@code method} content-type parameter
 * @param attachmentName file name for the {@code text/calendar} attachment
 */
public record CalendarEmail(
        String recipient,
        String subject,
        String body,
        @org.jspecify.annotations.Nullable String htmlBody,
        String icsContent,
        String calendarMethod,
        String attachmentName) {}
