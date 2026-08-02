package io.github.smiskinext.notification.application.service;

import io.github.smiskinext.notification.application.command.ProcessInboundEmailReplyCommand;
import io.github.smiskinext.notification.application.result.ProcessInboundEmailReplyResult;
import io.github.smiskinext.notification.application.usecase.ProcessInboundEmailReplyUseCase;
import io.github.smiskinext.notification.domain.NotificationError;
import io.github.smiskinext.notification.domain.port.InboundEmailReplyProcessor;
import io.github.smiskinext.notification.domain.port.WebhookVerifier;
import io.github.smiskinext.shared.domain.Result;

import org.springframework.stereotype.Service;

/**
 * Verifies the Svix webhook signature and, if valid, delegates to the inbound email reply
 * processor to parse and publish the event.
 *
 * <p>Returns {@link NotificationError.InvalidSignature} when the signature check fails so the
 * controller can map it to HTTP 400 without coupling verification logic to the presentation layer.
 */
@Service
public class ProcessInboundEmailReplyApplicationService implements ProcessInboundEmailReplyUseCase {

    private final WebhookVerifier webhookVerifier;
    private final InboundEmailReplyProcessor inboundEmailReplyProcessor;

    public ProcessInboundEmailReplyApplicationService(
            WebhookVerifier webhookVerifier,
            InboundEmailReplyProcessor inboundEmailReplyProcessor) {
        this.webhookVerifier = webhookVerifier;
        this.inboundEmailReplyProcessor = inboundEmailReplyProcessor;
    }

    @Override
    public Result<ProcessInboundEmailReplyResult, NotificationError> execute(
            ProcessInboundEmailReplyCommand command) {
        if (!webhookVerifier.verify(command.payload(), command.headers())) {
            return Result.failure(new NotificationError.InvalidSignature());
        }
        inboundEmailReplyProcessor.processInboundEmail(command.payload());
        return Result.success(new ProcessInboundEmailReplyResult());
    }
}
