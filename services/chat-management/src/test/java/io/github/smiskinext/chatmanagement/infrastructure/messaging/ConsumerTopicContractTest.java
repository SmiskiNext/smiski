package io.github.smiskinext.chatmanagement.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import io.cloudevents.CloudEvent;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.annotation.KafkaListener;

class ConsumerTopicContractTest {

    @Test
    void participantEventConsumerTopicsMatchPublishedParticipantTopics() throws Exception {
        assertThat(listenerTopics(ParticipantEventConsumer.class, "onParticipantJoined"))
                .containsExactly("meeting-management.participant.joined");
        assertThat(listenerTopics(ParticipantEventConsumer.class, "onParticipantLeft"))
                .containsExactly("meeting-management.participant.left");
        assertThat(listenerTopics(ParticipantEventConsumer.class, "onParticipantKicked"))
                .containsExactly("meeting-management.participant.kicked");
    }

    @Test
    void meetingEventConsumerTopicsMatchPublishedMeetingTopics() throws Exception {
        assertThat(listenerTopics(MeetingEventConsumer.class, "onMeetingStarted"))
                .containsExactly("meeting-management.meeting.started");
        assertThat(listenerTopics(MeetingEventConsumer.class, "onMeetingEnded"))
                .containsExactly("meeting-management.meeting.ended");
        assertThat(listenerTopics(MeetingEventConsumer.class, "onMeetingCancelled"))
                .containsExactly("meeting-management.meeting.cancelled");
    }

    private static String[] listenerTopics(Class<?> owner, String methodName) throws Exception {
        Method method = owner.getMethod(methodName, CloudEvent.class);
        KafkaListener annotation = method.getAnnotation(KafkaListener.class);
        assertThat(annotation).isNotNull();
        return annotation.topics();
    }
}
