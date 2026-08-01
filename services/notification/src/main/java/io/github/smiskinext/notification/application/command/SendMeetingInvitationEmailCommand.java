package io.github.smiskinext.notification.application.command;

import io.github.smiskinext.notification.domain.model.CalendarEmail;
import io.github.smiskinext.shared.application.Command;

/**
 * Command to send a meeting invitation calendar email to one invitee.
 *
 * @param email the fully-constructed calendar email ready to deliver
 */
public record SendMeetingInvitationEmailCommand(CalendarEmail email) implements Command {}
