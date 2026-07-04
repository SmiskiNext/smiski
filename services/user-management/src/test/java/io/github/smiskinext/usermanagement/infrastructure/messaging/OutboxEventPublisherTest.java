package io.github.smiskinext.usermanagement.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.f4b6a3.uuid.UuidCreator;
import io.cloudevents.CloudEvent;
import io.github.smiskinext.usermanagement.infrastructure.persistence.OutboxEventJpaEntity;
import io.github.smiskinext.usermanagement.infrastructure.persistence.OutboxEventRepository;
import java.time.Instant;
import java.util.List;
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
class OutboxEventPublisherTest {

    @Mock
    OutboxEventRepository outboxEventRepository;

    @Mock
    KafkaTemplate<String, CloudEvent> kafkaTemplate;

    @Spy
    ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    OutboxEventPublisher outboxEventPublisher;

    private OutboxEventJpaEntity buildRow(int retryCount) {
        var row = new OutboxEventJpaEntity(
                UuidCreator.getTimeOrderedEpoch(),
                "user",
                "io.github.phunguy65.zms.user.registered.v1",
                "user-management.user.registered",
                "{\"email\":\"alice@example.com\"}",
                Instant.now());
        for (int i = 0; i < retryCount; i++) row.incrementRetryCount();
        return row;
    }

    @Test
    void successfulPublish_marksRowAsPublished() throws Exception {
        var row = buildRow(0);
        when(outboxEventRepository.findAllByPublishedAtIsNull()).thenReturn(List.of(row));
        when(kafkaTemplate.send(any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        outboxEventPublisher.pollAndPublish();

        assertThat(row.getPublishedAt()).isNotNull();
        verify(outboxEventRepository, times(1)).save(row);
    }

    @Test
    void failedPublish_incrementsRetryCount() throws Exception {
        var row = buildRow(0);
        when(outboxEventRepository.findAllByPublishedAtIsNull()).thenReturn(List.of(row));
        var failedFuture = new CompletableFuture<Object>();
        failedFuture.completeExceptionally(new RuntimeException("Kafka down"));
        when(kafkaTemplate.send(any(), any(), any())).thenReturn((CompletableFuture) failedFuture);

        outboxEventPublisher.pollAndPublish();

        assertThat(row.getRetryCount()).isEqualTo(1);
        assertThat(row.getPublishedAt()).isNull();
    }

    @Test
    void maxRetriesExceeded_routesToDlt() throws Exception {
        var row = buildRow(3); // already at MAX_RETRIES
        when(outboxEventRepository.findAllByPublishedAtIsNull()).thenReturn(List.of(row));
        when(kafkaTemplate.send(any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        outboxEventPublisher.pollAndPublish();

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(topicCaptor.capture(), any(), any());
        assertThat(topicCaptor.getValue()).endsWith("-dlt");
        assertThat(row.getPublishedAt()).isNotNull();
    }
}
