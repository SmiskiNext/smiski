package io.github.smiskinext.notification.infrastructure.config;

import io.cloudevents.CloudEvent;
import io.cloudevents.kafka.CloudEventDeserializer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;

/**
 * Kafka consumer configuration for CloudEvent-encoded join events.
 *
 * <p>Each replica fans out join events locally: every replica must receive every event so it can
 * push to the emitters it holds. To achieve this each listener gets a dedicated consumer factory
 * whose group id is randomized per replica ({@code notification-join-created-sse-<uuid>} and
 * {@code notification-join-resolved-sse-<uuid>}). Sharing one consumer factory would merge both
 * listeners into a single consumer group whose partitions are split between them, so records for
 * one topic landing on partitions held by the other listener would be dropped as malformed.
 *
 * <p>{@code auto.offset.reset=latest} avoids replaying historical events on restart.
 */
@Configuration
@EnableKafka
public class KafkaConfig {

    @Bean
    public ConsumerFactory<String, CloudEvent> joinCreatedConsumerFactory(
            KafkaProperties kafkaProperties) {
        return new DefaultKafkaConsumerFactory<>(
                cloudEventConsumerProperties(kafkaProperties, "notification-join-created-sse-"));
    }

    @Bean
    public ConsumerFactory<String, CloudEvent> joinResolvedConsumerFactory(
            KafkaProperties kafkaProperties) {
        return new DefaultKafkaConsumerFactory<>(
                cloudEventConsumerProperties(kafkaProperties, "notification-join-resolved-sse-"));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, CloudEvent>
            joinCreatedKafkaListenerContainerFactory(
                    ConsumerFactory<String, CloudEvent> joinCreatedConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, CloudEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(joinCreatedConsumerFactory);
        return factory;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, CloudEvent>
            joinResolvedKafkaListenerContainerFactory(
                    ConsumerFactory<String, CloudEvent> joinResolvedConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, CloudEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(joinResolvedConsumerFactory);
        return factory;
    }

    private static Map<String, Object> cloudEventConsumerProperties(
            KafkaProperties kafkaProperties, String groupIdPrefix) {
        Map<String, Object> props = new HashMap<>(kafkaProperties.buildConsumerProperties());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, CloudEventDeserializer.class);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupIdPrefix + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        return props;
    }
}
