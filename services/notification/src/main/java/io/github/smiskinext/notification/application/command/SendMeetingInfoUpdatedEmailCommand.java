package io.github.smiskinext.notification.application.command;

import io.github.smiskinext.notification.domain.model.CalendarEmail;
import io.github.smiskinext.shared.application.Command;

/**
 * Command to send a meeting-info-updated calendar email to one invitee.
 *
 * @param email the fully-constructed calendar email ready to deliver
 */
public record SendMeetingInfoUpdatedEmailCommand(CalendarEmail email) implements Command {}
