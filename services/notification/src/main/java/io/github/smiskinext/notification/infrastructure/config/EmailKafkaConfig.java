package io.github.smiskinext.notification.infrastructure.config;

import io.cloudevents.CloudEvent;
import io.cloudevents.kafka.CloudEventDeserializer;
import io.cloudevents.kafka.CloudEventSerializer;
import io.github.smiskinext.notification.infrastructure.messaging.EmailConsumerProperties;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Kafka configuration for the calendar-email consumers.
 *
 * <p>Unlike the SSE relay (which fans out to every replica via random group ids), the email
 * consumers subscribe under fixed, externally configured groups so each event is emailed by exactly
 * one replica. Send failures are retried a bounded number of times and then routed to a per-topic
 * dead-letter topic so a persistent failure never blocks the partition.
 */
@Configuration
@EnableConfigurationProperties(EmailConsumerProperties.class)
public class EmailKafkaConfig {

    @Bean
    public ConsumerFactory<String, CloudEvent> emailCloudEventConsumerFactory(
            KafkaProperties kafkaProperties) {
        Map<String, Object> props = new HashMap<>(kafkaProperties.buildConsumerProperties());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, CloudEventDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ProducerFactory<String, CloudEvent> emailCloudEventProducerFactory(
            KafkaProperties kafkaProperties) {
        Map<String, Object> props = new HashMap<>(kafkaProperties.buildProducerProperties());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, CloudEventSerializer.class);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, CloudEvent> emailCloudEventKafkaTemplate(
            ProducerFactory<String, CloudEvent> emailCloudEventProducerFactory) {
        return new KafkaTemplate<>(emailCloudEventProducerFactory);
    }

    @Bean
    public DefaultErrorHandler emailConsumerErrorHandler(
            KafkaTemplate<String, CloudEvent> emailCloudEventKafkaTemplate,
            EmailConsumerProperties properties) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                emailCloudEventKafkaTemplate,
                (record, exception) -> new TopicPartition(
                        record.topic() + properties.getEmailDeadLetterSuffix(),
                        record.partition()));
        long maxRetries = Math.max(0, properties.getEmailRetryAttempts() - 1L);
        FixedBackOff backOff = new FixedBackOff(properties.getEmailRetryBackoffMs(), maxRetries);
        return new DefaultErrorHandler(recoverer, backOff);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, CloudEvent>
            emailKafkaListenerContainerFactory(
                    ConsumerFactory<String, CloudEvent> emailCloudEventConsumerFactory,
                    DefaultErrorHandler emailConsumerErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, CloudEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(emailCloudEventConsumerFactory);
        factory.setCommonErrorHandler(emailConsumerErrorHandler);
        return factory;
    }
}
