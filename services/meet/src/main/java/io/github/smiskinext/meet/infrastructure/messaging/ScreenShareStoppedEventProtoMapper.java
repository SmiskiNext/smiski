package io.github.smiskinext.meet.infrastructure.messaging;

import com.google.protobuf.Message;

import io.github.smiskinext.event.meet.v1.ScreenShareStopped;
import io.github.smiskinext.meet.domain.event.ScreenShareStoppedEvent;
import io.github.smiskinext.shared.infrastructure.outbox.OutboxEventProtoMapper;

import org.springframework.stereotype.Component;

@Component
public class ScreenShareStoppedEventProtoMapper
        implements OutboxEventProtoMapper<ScreenShareStoppedEvent> {

    @Override
    public Class<ScreenShareStoppedEvent> eventType() {
        return ScreenShareStoppedEvent.class;
    }

    @Override
    public String dataSchema() {
        return "io.github.smiskinext.event.meet.v1.ScreenShareStopped";
    }

    @Override
    public Message toProto(ScreenShareStoppedEvent event) {
        return ScreenShareStopped.newBuilder()
                .setMeetingId(event.meetingId().toString())
                .setTenantId(event.tenantId())
                .setAccountId(event.accountId())
                .setOccurredAt(event.occurredAt().toString())
                .setIdentity(event.identity())
                .build();
    }
}
