package io.github.smiskinext.meet.infrastructure.messaging;

import com.google.protobuf.Message;

import io.github.smiskinext.event.meet.v1.ParticipantJoined;
import io.github.smiskinext.meet.domain.event.ParticipantJoinedEvent;
import io.github.smiskinext.shared.infrastructure.outbox.OutboxEventProtoMapper;

import org.springframework.stereotype.Component;

@Component
public class ParticipantJoinedEventProtoMapper
        implements OutboxEventProtoMapper<ParticipantJoinedEvent> {

    @Override
    public Class<ParticipantJoinedEvent> eventType() {
        return ParticipantJoinedEvent.class;
    }

    @Override
    public String dataSchema() {
        return "io.github.smiskinext.event.meet.v1.ParticipantJoined";
    }

    @Override
    public Message toProto(ParticipantJoinedEvent event) {
        return ParticipantJoined.newBuilder()
                .setMeetingId(event.meetingId().toString())
                .setTenantId(event.tenantId())
                .setAccountId(event.accountId())
                .setOccurredAt(event.occurredAt().toString())
                .setIdentity(event.identity())
                .build();
    }
}
