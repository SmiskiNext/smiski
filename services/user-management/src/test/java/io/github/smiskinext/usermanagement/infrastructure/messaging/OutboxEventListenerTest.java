package io.github.smiskinext.usermanagement.infrastructure.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.smiskinext.shared.domain.valueobject.UserId;
import io.github.smiskinext.usermanagement.domain.event.UserRegisteredEvent;
import io.github.smiskinext.usermanagement.infrastructure.persistence.OutboxEventJpaEntity;
import io.github.smiskinext.usermanagement.infrastructure.persistence.OutboxEventRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class OutboxEventListenerTest {

    @Mock
    OutboxEventRepository outboxEventRepository;

    @Spy
    ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    OutboxEventListener listener;

    @Test
    void onPublishableEvent_persistsOutboxRow() {
        var userId = UserId.of(UuidCreator.getTimeOrderedEpoch());
        var event = new UserRegisteredEvent(
                UuidCreator.getTimeOrderedEpoch(),
                userId.value(),
                "alice@example.com",
                "Alice",
                "alice_user",
                Instant.now());

        listener.onPublishableEvent(event);

        verify(outboxEventRepository).save(any(OutboxEventJpaEntity.class));
    }
}
