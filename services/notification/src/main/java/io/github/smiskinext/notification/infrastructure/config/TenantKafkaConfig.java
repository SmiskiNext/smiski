package io.github.smiskinext.notification.infrastructure.config;

import io.cloudevents.CloudEvent;
import io.cloudevents.kafka.CloudEventDeserializer;
import io.github.smiskinext.notification.infrastructure.messaging.TenantKafkaProperties;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Kafka configuration for the tenant lifecycle consumers in the notification service.
 *
 * <p>Failed messages are retried a bounded number of times before being routed to a per-topic
 * dead-letter topic, so a persistent failure never blocks the partition.
 */
@Configuration
@EnableKafka
@EnableConfigurationProperties(TenantKafkaProperties.class)
public class TenantKafkaConfig {

    @Bean
    public ConsumerFactory<String, CloudEvent> tenantCloudEventConsumerFactory(
            KafkaProperties kafkaProperties) {
        Map<String, Object> props = new HashMap<>(kafkaProperties.buildConsumerProperties());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, CloudEventDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public DefaultErrorHandler tenantConsumerErrorHandler(
            @Qualifier("emailCloudEventKafkaTemplate") KafkaTemplate<String, CloudEvent> kafkaTemplate,
            TenantKafkaProperties properties) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) -> new TopicPartition(
                        record.topic() + properties.getDeadLetterSuffix(), record.partition()));
        long maxRetries = Math.max(0, properties.getRetryAttempts() - 1L);
        FixedBackOff backOff = new FixedBackOff(properties.getRetryBackoffMs(), maxRetries);
        return new DefaultErrorHandler(recoverer, backOff);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, CloudEvent>
            tenantKafkaListenerContainerFactory(
                    ConsumerFactory<String, CloudEvent> tenantCloudEventConsumerFactory,
                    DefaultErrorHandler tenantConsumerErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, CloudEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(tenantCloudEventConsumerFactory);
        factory.setCommonErrorHandler(tenantConsumerErrorHandler);
        return factory;
    }
}
