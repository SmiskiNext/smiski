package io.github.smiskinext.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import io.github.smiskinext.notification.application.command.RelayJoinResolvedCommand;
import io.github.smiskinext.notification.application.service.RelayJoinResolvedApplicationService;
import io.github.smiskinext.notification.domain.model.JoinDecision;
import io.github.smiskinext.notification.domain.port.JoinDecisionStore;
import io.github.smiskinext.notification.domain.port.SseRelayPort;
import io.github.smiskinext.shared.domain.Result;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RelayJoinResolvedApplicationServiceTest {

    @Mock
    private JoinDecisionStore joinDecisionStore;

    @Mock
    private SseRelayPort sseRelayPort;

    @InjectMocks
    private RelayJoinResolvedApplicationService service;

    @Test
    void execute_upsertsStoreAndPushesViaSseRelayPort() {
        UUID requestId = UUID.randomUUID();
        JoinDecision decision = JoinDecision.approved(requestId, "lk-token", "room-1");

        Result<?, ?> result = service.execute(new RelayJoinResolvedCommand(requestId, decision));

        assertThat(result.isSuccess()).isTrue();
        verify(joinDecisionStore).upsert(decision);
        verify(sseRelayPort).pushJoinResolved(requestId, decision);
    }
}
