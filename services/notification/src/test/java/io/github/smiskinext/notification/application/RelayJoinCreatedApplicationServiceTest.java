package io.github.smiskinext.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import io.github.smiskinext.notification.application.command.RelayJoinCreatedCommand;
import io.github.smiskinext.notification.application.service.RelayJoinCreatedApplicationService;
import io.github.smiskinext.notification.domain.model.PendingJoinRequest;
import io.github.smiskinext.notification.domain.port.PendingJoinRequestStore;
import io.github.smiskinext.notification.domain.port.SseRelayPort;
import io.github.smiskinext.shared.domain.Result;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RelayJoinCreatedApplicationServiceTest {

    @Mock
    private PendingJoinRequestStore pendingJoinRequestStore;

    @Mock
    private SseRelayPort sseRelayPort;

    @InjectMocks
    private RelayJoinCreatedApplicationService service;

    @Test
    void execute_upsertsStoreAndPushesViaSseRelayPort() {
        UUID meetingId = UUID.randomUUID();
        PendingJoinRequest request = new PendingJoinRequest(
                UUID.randomUUID(),
                meetingId,
                "acc-1",
                "Alice",
                "device-1",
                null,
                Instant.now().plusSeconds(300));

        Result<?, ?> result = service.execute(new RelayJoinCreatedCommand(meetingId, request));

        assertThat(result.isSuccess()).isTrue();
        verify(pendingJoinRequestStore).upsert(request);
        verify(sseRelayPort).pushJoinRequestCreated(meetingId, request);
    }
}
