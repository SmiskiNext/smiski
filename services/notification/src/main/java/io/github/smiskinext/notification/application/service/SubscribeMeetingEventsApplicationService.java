package io.github.smiskinext.notification.application.service;

import io.github.smiskinext.notification.application.SseSubscriptionPort;
import io.github.smiskinext.notification.application.command.SubscribeMeetingEventsCommand;
import io.github.smiskinext.notification.application.result.SubscribeMeetingEventsResult;
import io.github.smiskinext.notification.application.usecase.SubscribeMeetingEventsUseCase;
import io.github.smiskinext.notification.domain.NotificationError;
import io.github.smiskinext.shared.domain.Result;

import org.springframework.stereotype.Service;

/**
 * Delegates to {@link SseSubscriptionPort} to register the SSE emitter and wraps it in the result.
 */
@Service
public class SubscribeMeetingEventsApplicationService implements SubscribeMeetingEventsUseCase {

    private final SseSubscriptionPort sseSubscriptionPort;

    public SubscribeMeetingEventsApplicationService(SseSubscriptionPort sseSubscriptionPort) {
        this.sseSubscriptionPort = sseSubscriptionPort;
    }

    @Override
    public Result<SubscribeMeetingEventsResult, NotificationError> execute(
            SubscribeMeetingEventsCommand command) {
        var emitter = command.isRequestStream()
                ? sseSubscriptionPort.subscribeRequest(command.requestId())
                : sseSubscriptionPort.subscribe(command.meetingId());
        return Result.success(new SubscribeMeetingEventsResult(emitter));
    }
}
