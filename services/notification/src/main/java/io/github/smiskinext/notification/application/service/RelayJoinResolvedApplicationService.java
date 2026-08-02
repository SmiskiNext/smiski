package io.github.smiskinext.notification.application.service;

import io.github.smiskinext.notification.application.command.RelayJoinResolvedCommand;
import io.github.smiskinext.notification.application.result.RelayJoinResolvedResult;
import io.github.smiskinext.notification.application.usecase.RelayJoinResolvedUseCase;
import io.github.smiskinext.notification.domain.NotificationError;
import io.github.smiskinext.notification.domain.port.JoinDecisionStore;
import io.github.smiskinext.notification.domain.port.SseRelayPort;
import io.github.smiskinext.shared.domain.Result;

import org.springframework.stereotype.Service;

/**
 * Persists the join decision and relays it to all connected requester SSE streams.
 */
@Service
public class RelayJoinResolvedApplicationService implements RelayJoinResolvedUseCase {

    private final JoinDecisionStore joinDecisionStore;
    private final SseRelayPort sseRelayPort;

    public RelayJoinResolvedApplicationService(
            JoinDecisionStore joinDecisionStore, SseRelayPort sseRelayPort) {
        this.joinDecisionStore = joinDecisionStore;
        this.sseRelayPort = sseRelayPort;
    }

    @Override
    public Result<RelayJoinResolvedResult, NotificationError> execute(
            RelayJoinResolvedCommand command) {
        joinDecisionStore.upsert(command.decision());
        sseRelayPort.pushJoinResolved(command.requestId(), command.decision());
        return Result.success(new RelayJoinResolvedResult());
    }
}
