package io.github.smiskinext.notification.application.usecase;

import io.github.smiskinext.notification.application.command.RelayJoinCreatedCommand;
import io.github.smiskinext.notification.application.result.RelayJoinCreatedResult;
import io.github.smiskinext.notification.domain.NotificationError;
import io.github.smiskinext.shared.application.UseCase;

/**
 * Inbound port for relaying a join-request-created event to connected meeting-host SSE streams.
 */
public interface RelayJoinCreatedUseCase
        extends UseCase<RelayJoinCreatedCommand, RelayJoinCreatedResult, NotificationError> {}
