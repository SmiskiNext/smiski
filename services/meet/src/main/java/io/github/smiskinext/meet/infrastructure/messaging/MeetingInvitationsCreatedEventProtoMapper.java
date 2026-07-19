package io.github.smiskinext.meet.infrastructure.messaging;

import com.google.protobuf.Message;

import io.github.smiskinext.event.meet.v1.MeetingInvitationsSent;
import io.github.smiskinext.meet.domain.event.MeetingInvitationsCreatedEvent;
import io.github.smiskinext.shared.infrastructure.outbox.OutboxEventProtoMapper;

import org.springframework.stereotype.Component;

@Component
public class MeetingInvitationsCreatedEventProtoMapper
        implements OutboxEventProtoMapper<MeetingInvitationsCreatedEvent> {

    @Override
    public Class<MeetingInvitationsCreatedEvent> eventType() {
        return MeetingInvitationsCreatedEvent.class;
    }

    @Override
    public String dataSchema() {
        return "io.github.smiskinext.event.meet.v1.MeetingInvitationsCreated";
    }

    @Override
    public Message toProto(MeetingInvitationsCreatedEvent event) {
        MeetingInvitationsSent.Builder builder = MeetingInvitationsSent.newBuilder()
                .setMeetingId(event.meetingId().toString())
                .setTenantId(event.tenantId())
                .setMeetingShortCode(event.meetingShortCode())
                .setZoneId(event.zoneId());

        if (event.meetingTitle() != null) {
            builder.setMeetingTitle(event.meetingTitle());
        }
        if (event.startTime() != null) {
            builder.setStartTime(event.startTime().toString());
        }
        if (event.endTime() != null) {
            builder.setEndTime(event.endTime().toString());
        }

        for (MeetingInvitationsCreatedEvent.InviteeInfo invitee : event.invitees()) {
            MeetingInvitationsSent.InviteeInfo.Builder inviteeBuilder =
                    MeetingInvitationsSent.InviteeInfo.newBuilder()
                            .setAccountId(invitee.accountId())
                            .setEmail(invitee.email())
                            .setDisplayName(invitee.displayName())
                            .setToken(invitee.token());
            builder.addInvitees(inviteeBuilder.build());
        }

        return builder.build();
    }
}
