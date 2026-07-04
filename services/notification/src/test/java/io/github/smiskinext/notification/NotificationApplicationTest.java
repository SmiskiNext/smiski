package io.github.smiskinext.notification;

import static org.assertj.core.api.Assertions.assertThat;

import io.cloudevents.CloudEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;

@SpringBootTest(
        classes = NotificationApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
            "spring.kafka.bootstrap-servers=localhost:9092",
            "app.notification.resend.api-key=re_test_key",
            "app.notification.resend.from-email=notifications@example.com",
            "app.notification.resend.from-name=Zero Meeting System",
            "app.notification.invitation.join-base-url=https://app.example.com/join"
        })
class NotificationApplicationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private ConsumerFactory<String, CloudEvent> cloudEventConsumerFactory;

    @Autowired
    private ConcurrentKafkaListenerContainerFactory<String, CloudEvent>
            cloudEventKafkaListenerContainerFactory;

    @Test
    void loadsContextWithInvitationConsumerBeans() {
        assertThat(applicationContext).isNotNull();
        assertThat(cloudEventConsumerFactory).isNotNull();
        assertThat(cloudEventKafkaListenerContainerFactory.getConsumerFactory())
                .isSameAs(cloudEventConsumerFactory);
    }
}
