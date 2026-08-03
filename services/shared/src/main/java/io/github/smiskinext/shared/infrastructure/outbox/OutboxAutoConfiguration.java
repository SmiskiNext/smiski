package io.github.smiskinext.shared.infrastructure.outbox;

import io.cloudevents.CloudEvent;
import io.github.smiskinext.shared.domain.EventPublisher;
import io.github.smiskinext.shared.infrastructure.event.SpringDomainEventPublisher;
import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
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
    public OutboxEventPublisher outboxEventPublisher(
            OutboxStore outboxStore,
            OutboxEventProtoMapperRegistry mapperRegistry,
            CloudEventEncoder cloudEventEncoder) {
        return new OutboxEventPublisher(outboxStore, mapperRegistry, cloudEventEncoder);
    }

    @Bean
    @ConditionalOnMissingBean(EventPublisher.class)
    public EventPublisher eventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        return new SpringDomainEventPublisher(applicationEventPublisher);
    }

    @Bean
    @ConditionalOnMissingBean
    public OutboxDomainEventListener outboxDomainEventListener(
            OutboxEventPublisher outboxEventPublisher) {
        return new OutboxDomainEventListener(outboxEventPublisher);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
            prefix = "app.outbox",
            name = "transport",
            havingValue = "kafka",
            matchIfMissing = true)
    public KafkaOutboxTransport kafkaOutboxTransport(
            KafkaTemplate<String, CloudEvent> kafkaTemplate) {
        return new KafkaOutboxTransport(kafkaTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public OutboxRelayTransactionDelegate outboxRelayTransactionDelegate(
            OutboxStore outboxStore, OutboxProperties properties) {
        return new OutboxRelayTransactionDelegate(outboxStore, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public OutboxRelay outboxRelay(
            OutboxRelayTransactionDelegate transactionDelegate,
            OutboxTransport transport,
            CloudEventEncoder cloudEventEncoder) {
        return new OutboxRelay(transactionDelegate, transport, cloudEventEncoder);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
            prefix = "app.outbox.relay",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    public OutboxRelayTrigger outboxRelayTrigger(OutboxRelay outboxRelay) {
        return new OutboxRelayTrigger(outboxRelay);
    }
}
