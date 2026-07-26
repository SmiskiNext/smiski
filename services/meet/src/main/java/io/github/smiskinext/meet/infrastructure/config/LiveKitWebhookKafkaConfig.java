package io.github.smiskinext.meet.infrastructure.config;

import io.github.smiskinext.meet.infrastructure.messaging.LiveKitWebhookMessage;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

/**
 * Kafka producer template and consumer container factory for the internal LiveKit webhook topic.
 * Messages are JSON-serialized {@link LiveKitWebhookMessage} instances keyed by room name.
 */
@Configuration
public class LiveKitWebhookKafkaConfig {

    @Bean
    public ProducerFactory<String, LiveKitWebhookMessage> liveKitWebhookProducerFactory(
            KafkaProperties kafkaProperties) {
        Map<String, Object> props = kafkaProperties.buildProducerProperties();
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, LiveKitWebhookMessage> liveKitWebhookKafkaTemplate(
            ProducerFactory<String, LiveKitWebhookMessage> liveKitWebhookProducerFactory) {
        return new KafkaTemplate<>(liveKitWebhookProducerFactory);
    }

    @Bean
    public ConsumerFactory<String, LiveKitWebhookMessage> liveKitWebhookConsumerFactory(
            KafkaProperties kafkaProperties) {
        Map<String, Object> props = new HashMap<>(kafkaProperties.buildConsumerProperties());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, LiveKitWebhookMessage.class.getName());
        props.put(JsonDeserializer.TRUSTED_PACKAGES, LiveKitWebhookMessage.class.getPackageName());
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, LiveKitWebhookMessage>
            liveKitWebhookKafkaListenerContainerFactory(
                    ConsumerFactory<String, LiveKitWebhookMessage> liveKitWebhookConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, LiveKitWebhookMessage> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(liveKitWebhookConsumerFactory);
        return factory;
    }
}
