package io.github.smiskinext.notification.application.service;

import io.github.smiskinext.notification.application.command.SendMeetingInfoUpdatedEmailCommand;
import io.github.smiskinext.notification.application.result.SendMeetingInfoUpdatedEmailResult;
import io.github.smiskinext.notification.application.usecase.SendMeetingInfoUpdatedEmailUseCase;
import io.github.smiskinext.notification.domain.NotificationError;
import io.github.smiskinext.notification.domain.port.EmailSender;
import io.github.smiskinext.shared.domain.Result;

import org.springframework.stereotype.Service;

/**
 * Delivers a meeting-info-updated calendar email to one invitee.
 */
@Service
public class SendMeetingInfoUpdatedEmailApplicationService
        implements SendMeetingInfoUpdatedEmailUseCase {

    private final EmailSender emailSender;

    public SendMeetingInfoUpdatedEmailApplicationService(EmailSender emailSender) {
        this.emailSender = emailSender;
    }

    @Override
    public Result<SendMeetingInfoUpdatedEmailResult, NotificationError> execute(
            SendMeetingInfoUpdatedEmailCommand command) {
        emailSender.send(command.email());
        return Result.success(new SendMeetingInfoUpdatedEmailResult());
    }
}
