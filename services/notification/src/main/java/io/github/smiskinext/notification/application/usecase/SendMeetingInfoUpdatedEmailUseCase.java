package io.github.smiskinext.notification.application.usecase;

import io.github.smiskinext.notification.application.command.SendMeetingInfoUpdatedEmailCommand;
import io.github.smiskinext.notification.application.result.SendMeetingInfoUpdatedEmailResult;
import io.github.smiskinext.notification.domain.NotificationError;
import io.github.smiskinext.shared.application.UseCase;

/**
 * Inbound port for sending a meeting-info-updated calendar email to invitees.
 */
public interface SendMeetingInfoUpdatedEmailUseCase
        extends UseCase<
                SendMeetingInfoUpdatedEmailCommand,
                SendMeetingInfoUpdatedEmailResult,
                NotificationError> {}
