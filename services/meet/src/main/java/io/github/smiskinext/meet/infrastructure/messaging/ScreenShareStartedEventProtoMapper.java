package io.github.smiskinext.meet.infrastructure.messaging;

import com.google.protobuf.Message;

import io.github.smiskinext.event.meet.v1.ScreenShareStarted;
import io.github.smiskinext.meet.domain.event.ScreenShareStartedEvent;
import io.github.smiskinext.shared.infrastructure.outbox.OutboxEventProtoMapper;

import org.springframework.stereotype.Component;

@Component
public class ScreenShareStartedEventProtoMapper
        implements OutboxEventProtoMapper<ScreenShareStartedEvent> {

    @Override
    public Class<ScreenShareStartedEvent> eventType() {
        return ScreenShareStartedEvent.class;
    }

    @Override
    public String dataSchema() {
        return "io.github.smiskinext.event.meet.v1.ScreenShareStarted";
    }

    @Override
    public Message toProto(ScreenShareStartedEvent event) {
        return ScreenShareStarted.newBuilder()
                .setMeetingId(event.meetingId().toString())
                .setTenantId(event.tenantId())
                .setAccountId(event.accountId())
                .setOccurredAt(event.occurredAt().toString())
                .setIdentity(event.identity())
                .build();
    }
}
