package io.github.smiskinext.notification.application.service;

import io.github.smiskinext.notification.application.command.RelayJoinCreatedCommand;
import io.github.smiskinext.notification.application.result.RelayJoinCreatedResult;
import io.github.smiskinext.notification.application.usecase.RelayJoinCreatedUseCase;
import io.github.smiskinext.notification.domain.NotificationError;
import io.github.smiskinext.notification.domain.port.PendingJoinRequestStore;
import io.github.smiskinext.notification.domain.port.SseRelayPort;
import io.github.smiskinext.shared.domain.Result;

import org.springframework.stereotype.Service;

/**
 * Persists the pending join request and relays it to all connected meeting-host SSE streams.
 */
@Service
public class RelayJoinCreatedApplicationService implements RelayJoinCreatedUseCase {

    private final PendingJoinRequestStore pendingJoinRequestStore;
    private final SseRelayPort sseRelayPort;

    public RelayJoinCreatedApplicationService(
            PendingJoinRequestStore pendingJoinRequestStore, SseRelayPort sseRelayPort) {
        this.pendingJoinRequestStore = pendingJoinRequestStore;
        this.sseRelayPort = sseRelayPort;
    }

    @Override
    public Result<RelayJoinCreatedResult, NotificationError> execute(
            RelayJoinCreatedCommand command) {
        pendingJoinRequestStore.upsert(command.request());
        sseRelayPort.pushJoinRequestCreated(command.meetingId(), command.request());
        return Result.success(new RelayJoinCreatedResult());
    }
}
