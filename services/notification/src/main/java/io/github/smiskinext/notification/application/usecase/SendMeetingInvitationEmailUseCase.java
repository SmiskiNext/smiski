package io.github.smiskinext.notification.application.usecase;

import io.github.smiskinext.notification.application.command.SendMeetingInvitationEmailCommand;
import io.github.smiskinext.notification.application.result.SendMeetingInvitationEmailResult;
import io.github.smiskinext.notification.domain.NotificationError;
import io.github.smiskinext.shared.application.UseCase;

/**
 * Inbound port for sending a meeting invitation calendar email to an invitee.
 */
public interface SendMeetingInvitationEmailUseCase
        extends UseCase<
                SendMeetingInvitationEmailCommand,
                SendMeetingInvitationEmailResult,
                NotificationError> {}
