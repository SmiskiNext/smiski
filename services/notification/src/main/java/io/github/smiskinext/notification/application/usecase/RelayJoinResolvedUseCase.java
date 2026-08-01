package io.github.smiskinext.notification.application.usecase;

import io.github.smiskinext.notification.application.command.RelayJoinResolvedCommand;
import io.github.smiskinext.notification.application.result.RelayJoinResolvedResult;
import io.github.smiskinext.notification.domain.NotificationError;
import io.github.smiskinext.shared.application.UseCase;

/**
 * Inbound port for relaying a join-request-resolved decision to connected requester SSE streams.
 */
public interface RelayJoinResolvedUseCase
        extends UseCase<RelayJoinResolvedCommand, RelayJoinResolvedResult, NotificationError> {}
