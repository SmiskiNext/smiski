package io.github.smiskinext.notification.application.usecase;

import io.github.smiskinext.notification.application.command.SubscribeMeetingEventsCommand;
import io.github.smiskinext.notification.application.result.SubscribeMeetingEventsResult;
import io.github.smiskinext.notification.domain.NotificationError;
import io.github.smiskinext.shared.application.UseCase;

/**
 * Inbound port for subscribing to meeting or join-request SSE event streams.
 */
public interface SubscribeMeetingEventsUseCase
        extends UseCase<
                SubscribeMeetingEventsCommand, SubscribeMeetingEventsResult, NotificationError> {}
