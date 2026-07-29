package io.github.smiskinext.notification.domain.port;

import io.github.smiskinext.notification.domain.model.CalendarEmail;

/**
 * Outbound port for delivering a calendar email through an email provider.
 *
 * <p>Implementations attach the iCalendar payload as a {@code text/calendar} part carrying the
 * calendar {@code method} parameter. A delivery failure is signaled by throwing so the consumer's
 * retry and dead-letter handling can engage.
 */
public interface EmailSender {

    /**
     * Sends the given calendar email.
     *
     * @param email the email to deliver
     * @throws RuntimeException when delivery fails
     */
    void send(CalendarEmail email);
}
