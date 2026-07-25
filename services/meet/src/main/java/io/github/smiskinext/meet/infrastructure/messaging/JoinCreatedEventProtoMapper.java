package io.github.smiskinext.meet.infrastructure.messaging;

import com.google.protobuf.Message;

import io.github.smiskinext.event.meet.v1.JoinCreated;
import io.github.smiskinext.meet.domain.event.JoinRequestCreatedEvent;
import io.github.smiskinext.shared.infrastructure.outbox.OutboxEventProtoMapper;

import org.springframework.stereotype.Component;

@Component
public class JoinCreatedEventProtoMapper
        implements OutboxEventProtoMapper<JoinRequestCreatedEvent> {

    @Override
    public Class<JoinRequestCreatedEvent> eventType() {
        return JoinRequestCreatedEvent.class;
    }

    @Override
    public String dataSchema() {
        return "io.github.smiskinext.event.meet.v1.JoinCreated";
    }

    @Override
    public Message toProto(JoinRequestCreatedEvent event) {
        return JoinCreated.newBuilder()
                .setMeetingId(event.meetingId().toString())
                .setJoinRequestId(event.joinRequestId().toString())
                .setTenantId(event.tenantId())
                .setAccountId(event.accountId())
                .setDisplayName(event.displayName())
                .setDeviceId(event.deviceId())
                .setOccurredAt(event.occurredAt().toString())
                .setAvatarUrl(event.avatarUrl() == null ? "" : event.avatarUrl())
                .build();
    }
}
