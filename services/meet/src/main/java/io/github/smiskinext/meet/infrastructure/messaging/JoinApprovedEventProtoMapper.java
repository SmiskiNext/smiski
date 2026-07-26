package io.github.smiskinext.meet.infrastructure.messaging;

import com.google.protobuf.Message;

import io.github.smiskinext.event.meet.v1.JoinApproved;
import io.github.smiskinext.meet.domain.event.JoinRequestApprovedEvent;
import io.github.smiskinext.shared.infrastructure.outbox.OutboxEventProtoMapper;

import org.springframework.stereotype.Component;

@Component
public class JoinApprovedEventProtoMapper
        implements OutboxEventProtoMapper<JoinRequestApprovedEvent> {

    @Override
    public Class<JoinRequestApprovedEvent> eventType() {
        return JoinRequestApprovedEvent.class;
    }

    @Override
    public String dataSchema() {
        return "io.github.smiskinext.event.meet.v1.JoinApproved";
    }

    @Override
    public Message toProto(JoinRequestApprovedEvent event) {
        return JoinApproved.newBuilder()
                .setMeetingId(event.meetingId().toString())
                .setJoinRequestId(event.joinRequestId().toString())
                .setTenantId(event.tenantId())
                .setAccountId(event.accountId())
                .setDeviceId(event.deviceId())
                .setLiveKitToken(event.liveKitToken())
                .setRoomName(event.roomName())
                .setApprovedBy(event.approvedBy())
                .setOccurredAt(event.occurredAt().toString())
                .build();
    }
}
