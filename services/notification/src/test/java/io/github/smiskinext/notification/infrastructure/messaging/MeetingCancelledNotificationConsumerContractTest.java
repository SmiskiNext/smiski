package io.github.smiskinext.notification.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import io.cloudevents.CloudEvent;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.annotation.KafkaListener;

class MeetingCancelledNotificationConsumerContractTest {

    @Test
    void listenerTopicMatchesCancelledMeetingTopic() throws Exception {
        Method method = MeetingCancelledNotificationConsumer.class.getMethod(
                "onMeetingCancelled", CloudEvent.class);
        KafkaListener annotation = method.getAnnotation(KafkaListener.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.topics()).containsExactly("meeting-management.meeting.cancelled");
        assertThat(annotation.groupId())
                .isEqualTo("#{@notificationProperties.kafka.invitationConsumerGroup}");
    }
}
