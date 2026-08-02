package io.github.smiskinext.notification.application.usecase;

import io.github.smiskinext.notification.application.command.SendInviteeRespondedEmailCommand;
import io.github.smiskinext.notification.application.result.SendInviteeRespondedEmailResult;
import io.github.smiskinext.notification.domain.NotificationError;
import io.github.smiskinext.shared.application.UseCase;

/**
 * Inbound port for sending an invitee-responded confirmation calendar email to the organizer.
 */
public interface SendInviteeRespondedEmailUseCase
        extends UseCase<
                SendInviteeRespondedEmailCommand,
                SendInviteeRespondedEmailResult,
                NotificationError> {}
