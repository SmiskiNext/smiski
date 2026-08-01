package io.github.smiskinext.notification.application.usecase;

import io.github.smiskinext.notification.application.command.ProcessInboundEmailReplyCommand;
import io.github.smiskinext.notification.application.result.ProcessInboundEmailReplyResult;
import io.github.smiskinext.notification.domain.NotificationError;
import io.github.smiskinext.shared.application.UseCase;

/**
 * Inbound port for processing an inbound email webhook, including signature verification and
 * iMIP reply parsing.
 */
public interface ProcessInboundEmailReplyUseCase
        extends UseCase<
                ProcessInboundEmailReplyCommand,
                ProcessInboundEmailReplyResult,
                NotificationError> {}
