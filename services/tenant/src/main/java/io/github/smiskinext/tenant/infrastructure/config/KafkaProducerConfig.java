package io.github.smiskinext.tenant.infrastructure.config;

import io.cloudevents.CloudEvent;
import io.cloudevents.kafka.CloudEventSerializer;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

@Configuration
public class KafkaProducerConfig {

    @Bean
    @ConditionalOnMissingBean(name = "cloudEventProducerFactory")
    public ProducerFactory<String, CloudEvent> cloudEventProducerFactory(
            KafkaProperties kafkaProperties) {
        Map<String, Object> props = kafkaProperties.buildProducerProperties();
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, CloudEventSerializer.class);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    @ConditionalOnMissingBean(name = "cloudEventKafkaTemplate")
    public KafkaTemplate<String, CloudEvent> cloudEventKafkaTemplate(
            ProducerFactory<String, CloudEvent> cloudEventProducerFactory) {
        return new KafkaTemplate<>(cloudEventProducerFactory);
    }
}
