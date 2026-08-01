package io.github.smiskinext.notification.application.service;

import io.github.smiskinext.notification.application.command.SendInviteeRespondedEmailCommand;
import io.github.smiskinext.notification.application.result.SendInviteeRespondedEmailResult;
import io.github.smiskinext.notification.application.usecase.SendInviteeRespondedEmailUseCase;
import io.github.smiskinext.notification.domain.NotificationError;
import io.github.smiskinext.notification.domain.port.EmailSender;
import io.github.smiskinext.shared.domain.Result;

import org.springframework.stereotype.Service;

/**
 * Delivers an invitee-responded confirmation calendar email to the organizer.
 */
@Service
public class SendInviteeRespondedEmailApplicationService
        implements SendInviteeRespondedEmailUseCase {

    private final EmailSender emailSender;

    public SendInviteeRespondedEmailApplicationService(EmailSender emailSender) {
        this.emailSender = emailSender;
    }

    @Override
    public Result<SendInviteeRespondedEmailResult, NotificationError> execute(
            SendInviteeRespondedEmailCommand command) {
        emailSender.send(command.email());
        return Result.success(new SendInviteeRespondedEmailResult());
    }
}
