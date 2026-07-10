package io.github.smiskinext.shared.testcontainers;

import org.testcontainers.kafka.KafkaContainer;

/**
 * Factory for a reusable Kafka testcontainer.
 *
 * <p>Per-service {@code @TestConfiguration} classes call these static methods and expose the result
 * as Spring beans.
 */
public final class KafkaContainerSupport {

    private KafkaContainerSupport() {}

    public static KafkaContainer kafka() {
        return new KafkaContainer("apache/kafka:3.8.0");
    }
}
