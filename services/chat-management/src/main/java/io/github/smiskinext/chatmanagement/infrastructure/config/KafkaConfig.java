package io.github.smiskinext.chatmanagement.infrastructure.config;

import io.cloudevents.CloudEvent;
import io.cloudevents.kafka.CloudEventDeserializer;
import io.cloudevents.kafka.CloudEventSerializer;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;

/**
 * Kafka producer and consumer configuration for chat-management.
 *
 * <p>Uses CloudEvents binary content mode. Consumer uses <b>fixed group IDs</b>
 * (unlike meeting-management's SSE config which uses {@code UUID.randomUUID()} per instance)
 * because chat-management is stateful — each message must be processed exactly once.
 */
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    // ─── Producer ────────────────────────────────────────────────────────────

    @Bean
    public ProducerFactory<String, CloudEvent> cloudEventProducerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, CloudEventSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, CloudEvent> cloudEventKafkaTemplate(
            ProducerFactory<String, CloudEvent> cloudEventProducerFactory) {
        return new KafkaTemplate<>(cloudEventProducerFactory);
    }

    // ─── Consumer (meeting events) ───────────────────────────────────────────

    @Bean
    public ConsumerFactory<String, CloudEvent> cloudEventConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, CloudEventDeserializer.class);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "chat-management-meeting");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, CloudEvent>
            cloudEventKafkaListenerContainerFactory(
                    ConsumerFactory<String, CloudEvent> cloudEventConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, CloudEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(cloudEventConsumerFactory);
        return factory;
    }

    // ─── Consumer (participant events) ──────────────────────────────────────

    /**
     * Consumer factory for participant events — uses a separate fixed group ID
     * so meeting and participant consumers are independent.
     */
    @Bean
    public ConsumerFactory<String, CloudEvent> participantEventConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, CloudEventDeserializer.class);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "chat-management-participant");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, CloudEvent>
            participantEventKafkaListenerContainerFactory(
                    ConsumerFactory<String, CloudEvent> participantEventConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, CloudEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(participantEventConsumerFactory);
        return factory;
    }
}
