package io.github.smiskinext.notification.application.service;

import io.github.smiskinext.notification.application.command.SendMeetingInvitationEmailCommand;
import io.github.smiskinext.notification.application.result.SendMeetingInvitationEmailResult;
import io.github.smiskinext.notification.application.usecase.SendMeetingInvitationEmailUseCase;
import io.github.smiskinext.notification.domain.NotificationError;
import io.github.smiskinext.notification.domain.port.EmailSender;
import io.github.smiskinext.shared.domain.Result;

import org.springframework.stereotype.Service;

/**
 * Delivers a meeting invitation calendar email to one invitee.
 */
@Service
public class SendMeetingInvitationEmailApplicationService
        implements SendMeetingInvitationEmailUseCase {

    private final EmailSender emailSender;

    public SendMeetingInvitationEmailApplicationService(EmailSender emailSender) {
        this.emailSender = emailSender;
    }

    @Override
    public Result<SendMeetingInvitationEmailResult, NotificationError> execute(
            SendMeetingInvitationEmailCommand command) {
        emailSender.send(command.email());
        return Result.success(new SendMeetingInvitationEmailResult());
    }
}
