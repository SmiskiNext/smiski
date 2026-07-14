package io.github.smiskinext.shared.infrastructure.outbox;

import io.cloudevents.CloudEvent;
import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

@AutoConfiguration
@EnableConfigurationProperties(OutboxProperties.class)
@ConditionalOnBean(OutboxStore.class)
@EnableScheduling
public class OutboxAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public OutboxEventProtoMapperRegistry outboxEventProtoMapperRegistry(
            List<OutboxEventProtoMapper<?>> mappers) {
        return new OutboxEventProtoMapperRegistry(mappers);
    }

    @Bean
    @ConditionalOnMissingBean
    public CloudEventEncoder cloudEventEncoder(OutboxProperties properties) {
        return new CloudEventEncoder(properties.cloudevent().source());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
            prefix = "smiski.outbox",
            name = "transport",
            havingValue = "kafka",
            matchIfMissing = true)
    public KafkaOutboxTransport kafkaOutboxTransport(
            KafkaTemplate<String, CloudEvent> kafkaTemplate) {
        return new KafkaOutboxTransport(kafkaTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public OutboxRelay outboxRelay(
            OutboxStore outboxStore,
            OutboxTransport transport,
            CloudEventEncoder cloudEventEncoder,
            OutboxProperties properties) {
        return new OutboxRelay(outboxStore, transport, cloudEventEncoder, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
            prefix = "smiski.outbox.relay",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    public OutboxRelayTrigger outboxRelayTrigger(OutboxRelay outboxRelay) {
        return new OutboxRelayTrigger(outboxRelay);
    }
}
