package io.github.smiskinext.notification.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.cloudevents.CloudEvent;
import io.cloudevents.kafka.CloudEventDeserializer;
import io.github.smiskinext.notification.NotificationApplication;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;

class KafkaConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(NotificationApplication.class)
            .withPropertyValues(
                    "spring.kafka.bootstrap-servers=localhost:9092",
                    "app.notification.resend.api-key=re_test_key",
                    "app.notification.resend.from-email=notifications@example.com",
                    "app.notification.resend.from-name=Zero Meeting System",
                    "app.notification.invitation.join-base-url=https://app.example.com/join",
                    "app.notification.kafka.invitation-consumer-group=notification-test-group");

    @Test
    void cloudEventConsumerFactoryUsesConfiguredGroupAndDeserializer() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();

            DefaultKafkaConsumerFactory<String, CloudEvent> consumerFactory =
                    (DefaultKafkaConsumerFactory<String, CloudEvent>)
                            context.getBean("cloudEventConsumerFactory", ConsumerFactory.class);

            assertThat(consumerFactory.getConfigurationProperties())
                    .containsEntry(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092")
                    .containsEntry(ConsumerConfig.GROUP_ID_CONFIG, "notification-test-group")
                    .containsEntry(
                            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                            CloudEventDeserializer.class)
                    .containsEntry(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        });
    }

    @Test
    void listenerContainerFactoryUsesCloudEventConsumerFactory() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();

            ConcurrentKafkaListenerContainerFactory<String, CloudEvent> factory = context.getBean(
                    "cloudEventKafkaListenerContainerFactory",
                    ConcurrentKafkaListenerContainerFactory.class);
            ConsumerFactory<String, CloudEvent> consumerFactory =
                    context.getBean("cloudEventConsumerFactory", ConsumerFactory.class);

            assertThat(factory.getConsumerFactory()).isSameAs(consumerFactory);
        });
    }
}
