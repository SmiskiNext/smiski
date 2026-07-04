package io.github.smiskinext.meetingmanagement.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import io.cloudevents.CloudEvent;
import io.github.smiskinext.meetingmanagement.domain.event.JoinRequestApprovedEvent;
import io.github.smiskinext.meetingmanagement.domain.event.JoinRequestCreatedEvent;
import io.github.smiskinext.meetingmanagement.domain.event.JoinRequestDeniedEvent;
import io.github.smiskinext.meetingmanagement.domain.event.JoinRequestExpiredEvent;
import io.github.smiskinext.meetingmanagement.domain.event.MeetingCancelledEvent;
import io.github.smiskinext.meetingmanagement.domain.event.ParticipantKickedEvent;
import io.github.smiskinext.meetingmanagement.infrastructure.sse.MeetingSseManager;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.annotation.KafkaListener;

class EventTopicContractTest {

    @Test
    void meetingSseManagerListenerTopicsMatchJoinRequestAndParticipantEvents() throws Exception {
        assertThat(listenerTopics(MeetingSseManager.class, "onJoinRequestCreated"))
                .containsExactly(new JoinRequestCreatedEvent(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                "Guest",
                                "device-1",
                                Instant.parse("2026-04-02T09:15:00Z"))
                        .topic());
        assertThat(listenerTopics(MeetingSseManager.class, "onJoinRequestApproved"))
                .containsExactly(new JoinRequestApprovedEvent(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                "livekit-token",
                                "meeting-room",
                                Instant.parse("2026-04-02T09:20:00Z"))
                        .topic());
        assertThat(listenerTopics(MeetingSseManager.class, "onJoinRequestDenied"))
                .containsExactly(new JoinRequestDeniedEvent(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                Instant.parse("2026-04-02T09:25:00Z"))
                        .topic());
        assertThat(listenerTopics(MeetingSseManager.class, "onJoinRequestExpired"))
                .containsExactly(new JoinRequestExpiredEvent(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                Instant.parse("2026-04-02T09:30:00Z"))
                        .topic());
        assertThat(listenerTopics(MeetingSseManager.class, "onParticipantKicked"))
                .containsExactly(new ParticipantKickedEvent(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                "Charlie",
                                Instant.parse("2026-04-02T09:10:00Z"))
                        .topic());
    }

    @Test
    void meetingCancelledEvent_usesExpectedTopicAndType() {
        MeetingCancelledEvent event = new MeetingCancelledEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Planning Session",
                "ABC1234567",
                Instant.parse("2026-04-03T10:00:00Z"),
                List.of(),
                Instant.parse("2026-04-02T11:00:00Z"));

        assertThat(event.topic()).isEqualTo("meeting-management.meeting.cancelled");
        assertThat(event.eventType()).isEqualTo("io.github.phunguy65.zms.meeting.cancelled.v1");
    }

    private static String[] listenerTopics(Class<?> owner, String methodName) throws Exception {
        Method method = owner.getMethod(methodName, CloudEvent.class);
        KafkaListener annotation = method.getAnnotation(KafkaListener.class);
        assertThat(annotation).isNotNull();
        return annotation.topics();
    }
}
