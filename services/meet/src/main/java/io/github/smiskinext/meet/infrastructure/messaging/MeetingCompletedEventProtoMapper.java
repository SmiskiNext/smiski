package io.github.smiskinext.meet.infrastructure.messaging;

import com.google.protobuf.Message;

import io.github.smiskinext.event.meet.v1.MeetingCompleted;
import io.github.smiskinext.meet.domain.event.MeetingCompletedEvent;
import io.github.smiskinext.shared.infrastructure.outbox.OutboxEventProtoMapper;

import org.springframework.stereotype.Component;

@Component
public class MeetingCompletedEventProtoMapper
        implements OutboxEventProtoMapper<MeetingCompletedEvent> {

    @Override
    public Class<MeetingCompletedEvent> eventType() {
        return MeetingCompletedEvent.class;
    }

    @Override
    public String dataSchema() {
        return "io.github.smiskinext.event.meet.v1.MeetingCompleted";
    }

    @Override
    public Message toProto(MeetingCompletedEvent event) {
        return MeetingCompleted.newBuilder()
                .setMeetingId(event.meetingId().toString())
                .setTenantId(event.tenantId())
                .setHostId(event.hostId())
                .setCompletedAt(event.completedAt().toString())
                .build();
    }
}
