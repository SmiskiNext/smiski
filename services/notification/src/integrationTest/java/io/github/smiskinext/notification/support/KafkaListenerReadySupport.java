package io.github.smiskinext.notification.support;

import static org.awaitility.Awaitility.await;

import java.time.Duration;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;

/**
 * Test helper that blocks until every registered Kafka listener container has been assigned its
 * partitions.
 *
 * <p>The join consumers use {@code auto.offset.reset=latest}, so a message produced before the
 * container joins its group and is assigned partitions is silently missed. Integration tests call
 * this after context start and before producing so the assertion is not racy.
 */
public final class KafkaListenerReadySupport {

    private static final Duration ASSIGNMENT_TIMEOUT = Duration.ofSeconds(30);

    private KafkaListenerReadySupport() {}

    public static void awaitAllContainersAssigned(KafkaListenerEndpointRegistry registry) {
        await().atMost(ASSIGNMENT_TIMEOUT)
                .until(() -> registry.getListenerContainers().stream()
                        .allMatch(KafkaListenerReadySupport::hasAssignment));
    }

    private static boolean hasAssignment(MessageListenerContainer container) {
        return container.getAssignedPartitions() != null
                && !container.getAssignedPartitions().isEmpty();
    }
}
