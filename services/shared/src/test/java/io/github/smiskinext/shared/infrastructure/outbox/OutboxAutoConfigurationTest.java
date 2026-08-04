package io.github.smiskinext.shared.infrastructure.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.cloudevents.CloudEvent;
import io.github.smiskinext.shared.domain.EventPublisher;
import io.github.smiskinext.shared.infrastructure.event.SpringDomainEventPublisher;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.EnableTransactionManagement;

class OutboxAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(OutboxAutoConfiguration.class));

    @Test
    void relay_stays_inactive_when_no_outbox_store_bean_present() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(OutboxRelay.class);
            assertThat(context).doesNotHaveBean(OutboxRelayTransactionDelegate.class);
            assertThat(context).doesNotHaveBean(OutboxRelayTrigger.class);
            assertThat(context).doesNotHaveBean(OutboxTransport.class);
            assertThat(context).doesNotHaveBean(EventPublisher.class);
            assertThat(context).doesNotHaveBean(SpringDomainEventPublisher.class);
            assertThat(context).doesNotHaveBean(OutboxDomainEventListener.class);
        });
    }

    @Nested
    class TransactionBoundaries {

        @Test
        void transactional_delegate_is_proxied_and_applies_each_relay_transaction_boundary() {
            contextRunner
                    .withUserConfiguration(OutboxTestConfiguration.class)
                    .withPropertyValues(
                            "app.outbox.transport=test", "app.outbox.relay.enabled=false")
                    .run(context -> {
                        assertThat(context).hasSingleBean(OutboxRelay.class);
                        assertThat(context).hasSingleBean(OutboxRelayTransactionDelegate.class);

                        OutboxRelay relay = context.getBean(OutboxRelay.class);
                        OutboxRelayTransactionDelegate transactionDelegate =
                                context.getBean(OutboxRelayTransactionDelegate.class);
                        OutboxStore outboxStore = context.getBean(OutboxStore.class);
                        OutboxTransport transport = context.getBean(OutboxTransport.class);
                        CloudEventEncoder cloudEventEncoder =
                                context.getBean(CloudEventEncoder.class);
                        PlatformTransactionManager transactionManager =
                                context.getBean(PlatformTransactionManager.class);
                        TransactionStatus transactionStatus =
                                context.getBean(TransactionStatus.class);

                        assertThat(AopUtils.isAopProxy(relay)).isFalse();
                        assertThat(AopUtils.isAopProxy(transactionDelegate)).isTrue();

                        UUID publishedId = UUID.randomUUID();
                        CloudEvent cloudEvent = mock(CloudEvent.class);
                        when(outboxStore.claimBatch(50))
                                .thenReturn(List.of(
                                        outboxRow(publishedId, "valid"),
                                        outboxRow(UUID.randomUUID(), "invalid-one"),
                                        outboxRow(UUID.randomUUID(), "invalid-two")));
                        when(cloudEventEncoder.decode("valid")).thenReturn(cloudEvent);
                        when(cloudEventEncoder.decode("invalid-one"))
                                .thenThrow(new IllegalArgumentException("invalid-one"));
                        when(cloudEventEncoder.decode("invalid-two"))
                                .thenThrow(new IllegalArgumentException("invalid-two"));
                        when(transport.send("topic", "aggregate-1", cloudEvent))
                                .thenReturn(CompletableFuture.completedFuture(null));

                        relay.relay();

                        ArgumentCaptor<TransactionDefinition> definitions =
                                ArgumentCaptor.forClass(TransactionDefinition.class);
                        verify(transactionManager, times(4)).getTransaction(definitions.capture());
                        verify(transactionManager, times(4)).commit(transactionStatus);
                        verify(outboxStore).markPublished(List.of(publishedId));
                        verify(outboxStore, times(2))
                                .recordFailure(any(UUID.class), any(String.class));

                        assertThat(definitions.getAllValues())
                                .extracting(TransactionDefinition::getPropagationBehavior)
                                .containsExactly(
                                        TransactionDefinition.PROPAGATION_REQUIRED,
                                        TransactionDefinition.PROPAGATION_REQUIRED,
                                        TransactionDefinition.PROPAGATION_REQUIRES_NEW,
                                        TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                    });
        }

        @Test
        void custom_transactional_delegate_replaces_the_default() {
            contextRunner
                    .withUserConfiguration(
                            OutboxTestConfiguration.class, CustomDelegateConfiguration.class)
                    .withPropertyValues(
                            "app.outbox.transport=test", "app.outbox.relay.enabled=false")
                    .run(context -> {
                        assertThat(context).hasSingleBean(OutboxRelayTransactionDelegate.class);
                        assertThat(context.getBeanNamesForType(
                                        OutboxRelayTransactionDelegate.class))
                                .containsExactly("customOutboxRelayTransactionDelegate");
                        assertThat(context).hasSingleBean(OutboxRelay.class);
                    });
        }
    }

    private static OutboxStore.OutboxRow outboxRow(UUID id, String payload) {
        return new OutboxStore.OutboxRow(id, "tenant-1", "aggregate-1", "topic", payload);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class OutboxTestConfiguration {

        @Bean
        OutboxStore outboxStore() {
            return mock(OutboxStore.class);
        }

        @Bean
        OutboxTransport outboxTransport() {
            return mock(OutboxTransport.class);
        }

        @Bean
        CloudEventEncoder cloudEventEncoder() {
            return mock(CloudEventEncoder.class);
        }

        @Bean
        TransactionStatus transactionStatus() {
            return mock(TransactionStatus.class);
        }

        @Bean
        PlatformTransactionManager transactionManager(TransactionStatus transactionStatus) {
            PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
            when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                    .thenReturn(transactionStatus);
            return transactionManager;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomDelegateConfiguration {

        @Bean
        OutboxRelayTransactionDelegate customOutboxRelayTransactionDelegate(
                OutboxStore outboxStore, OutboxProperties properties) {
            return new OutboxRelayTransactionDelegate(outboxStore, properties);
        }
    }
}
