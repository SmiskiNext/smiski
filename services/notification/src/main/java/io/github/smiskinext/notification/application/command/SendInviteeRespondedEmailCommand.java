package io.github.smiskinext.notification.application.command;

import io.github.smiskinext.notification.domain.model.CalendarEmail;
import io.github.smiskinext.shared.application.Command;

/**
 * Command to send an invitee-responded confirmation calendar email to the organizer.
 *
 * @param email the fully-constructed calendar email ready to deliver
 */
public record SendInviteeRespondedEmailCommand(CalendarEmail email) implements Command {}
