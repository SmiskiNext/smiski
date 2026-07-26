package io.github.smiskinext.meet.infrastructure.messaging;

import com.google.protobuf.Message;

import io.github.smiskinext.event.meet.v1.ParticipantLeft;
import io.github.smiskinext.meet.domain.event.ParticipantLeftEvent;
import io.github.smiskinext.shared.infrastructure.outbox.OutboxEventProtoMapper;

import org.springframework.stereotype.Component;

@Component
public class ParticipantLeftEventProtoMapper
        implements OutboxEventProtoMapper<ParticipantLeftEvent> {

    @Override
    public Class<ParticipantLeftEvent> eventType() {
        return ParticipantLeftEvent.class;
    }

    @Override
    public String dataSchema() {
        return "io.github.smiskinext.event.meet.v1.ParticipantLeft";
    }

    @Override
    public Message toProto(ParticipantLeftEvent event) {
        return ParticipantLeft.newBuilder()
                .setMeetingId(event.meetingId().toString())
                .setTenantId(event.tenantId())
                .setAccountId(event.accountId())
                .setOccurredAt(event.occurredAt().toString())
                .setIdentity(event.identity())
                .build();
    }
}
