package io.github.smiskinext.notification.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import io.cloudevents.CloudEvent;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.annotation.KafkaListener;

class MeetingInvitationsSentConsumerContractTest {

    @Test
    void listenerTopicMatchesPublishedInvitationTopic() throws Exception {
        Method method = MeetingInvitationsSentConsumer.class.getMethod(
                "onMeetingInvitationsSent", CloudEvent.class);
        KafkaListener annotation = method.getAnnotation(KafkaListener.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.topics())
                .containsExactly("meeting-management.meeting.invitations.sent");
    }
}
