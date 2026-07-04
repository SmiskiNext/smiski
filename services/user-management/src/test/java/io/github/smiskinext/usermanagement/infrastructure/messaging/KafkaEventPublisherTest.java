package io.github.smiskinext.usermanagement.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.f4b6a3.uuid.UuidCreator;
import io.cloudevents.CloudEvent;
import io.github.phunguy65.zms.shared.domain.valueobject.UserId;
import io.github.smiskinext.usermanagement.domain.event.UserRegisteredEvent;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class KafkaEventPublisherTest {

    @Mock
    KafkaTemplate<String, CloudEvent> kafkaTemplate;

    @Spy
    ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    KafkaEventPublisher publisher;

    @Test
    void publish_sendsCloudEventWithCorrectTopicAndKey() {
        var userId = UserId.of(UuidCreator.getTimeOrderedEpoch());
        var event = new UserRegisteredEvent(
                UuidCreator.getTimeOrderedEpoch(),
                userId.value(),
                "alice@example.com",
                "Alice",
                "alice_user",
                Instant.now());

        when(kafkaTemplate.send(any(String.class), any(String.class), any(CloudEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher.publish(event);

        ArgumentCaptor<CloudEvent> ceCaptor = ArgumentCaptor.forClass(CloudEvent.class);
        verify(kafkaTemplate)
                .send(
                        eq("user-management.user.registered"),
                        eq(userId.value().toString()),
                        ceCaptor.capture());

        CloudEvent ce = ceCaptor.getValue();
        assertThat(ce.getId()).isEqualTo(event.eventId().toString());
        assertThat(ce.getType()).isEqualTo("io.github.phunguy65.zms.user.registered.v1");
        assertThat(ce.getSource().toString()).isEqualTo("user-management");
        assertThat(ce.getSubject()).isEqualTo(userId.value().toString());
        assertThat(ce.getDataContentType()).isEqualTo("application/json");
        assertThat(ce.getData()).isNotNull();
    }
}
