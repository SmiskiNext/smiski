package io.github.smiskinext.meet.infrastructure.messaging;

import com.google.protobuf.Message;

import io.github.smiskinext.event.meet.v1.JoinDenied;
import io.github.smiskinext.meet.domain.event.JoinRequestDeniedEvent;
import io.github.smiskinext.shared.infrastructure.outbox.OutboxEventProtoMapper;

import org.springframework.stereotype.Component;

@Component
public class JoinDeniedEventProtoMapper implements OutboxEventProtoMapper<JoinRequestDeniedEvent> {

    @Override
    public Class<JoinRequestDeniedEvent> eventType() {
        return JoinRequestDeniedEvent.class;
    }

    @Override
    public String dataSchema() {
        return "io.github.smiskinext.event.meet.v1.JoinDenied";
    }

    @Override
    public Message toProto(JoinRequestDeniedEvent event) {
        return JoinDenied.newBuilder()
                .setMeetingId(event.meetingId().toString())
                .setJoinRequestId(event.joinRequestId().toString())
                .setTenantId(event.tenantId())
                .setAccountId(event.accountId())
                .setDeviceId(event.deviceId())
                .setDeniedBy(event.deniedBy())
                .setOccurredAt(event.occurredAt().toString())
                .build();
    }
}
