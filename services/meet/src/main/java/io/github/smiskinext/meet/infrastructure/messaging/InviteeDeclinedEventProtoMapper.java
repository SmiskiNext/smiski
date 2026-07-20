package io.github.smiskinext.meet.infrastructure.messaging;

import com.google.protobuf.Message;

import io.github.smiskinext.event.meet.v1.InviteeDeclined;
import io.github.smiskinext.meet.domain.event.InviteeDeclinedEvent;
import io.github.smiskinext.shared.infrastructure.outbox.OutboxEventProtoMapper;

import org.springframework.stereotype.Component;

@Component
public class InviteeDeclinedEventProtoMapper
        implements OutboxEventProtoMapper<InviteeDeclinedEvent> {
    @Override
    public Class<InviteeDeclinedEvent> eventType() {
        return InviteeDeclinedEvent.class;
    }

    @Override
    public String dataSchema() {
        return "io.github.smiskinext.event.meet.v1.InviteeDeclined";
    }

    @Override
    public Message toProto(InviteeDeclinedEvent event) {
        return InviteeDeclined.newBuilder()
                .setEventId(event.eventId().toString())
                .setTenantId(event.tenantId())
                .setMeetingId(event.meetingId().toString())
                .setInviterId(event.inviterId())
                .setInviteeId(event.inviteeId().toString())
                .setInviteeEmail(event.inviteeEmail())
                .setStatus(event.status())
                .setDeclinedAt(event.declinedAt().toString())
                .build();
    }
}
