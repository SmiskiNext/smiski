package io.github.smiskinext.meet.infrastructure.messaging;

import com.google.protobuf.Message;

import io.github.smiskinext.event.meet.v1.MeetingInvitationsSent;
import io.github.smiskinext.meet.domain.event.MeetingInvitationsSentEvent;
import io.github.smiskinext.shared.infrastructure.outbox.OutboxEventProtoMapper;

import org.springframework.stereotype.Component;

@Component
public class MeetingInvitationsSentEventProtoMapper
        implements OutboxEventProtoMapper<MeetingInvitationsSentEvent> {

    @Override
    public Class<MeetingInvitationsSentEvent> eventType() {
        return MeetingInvitationsSentEvent.class;
    }

    @Override
    public String dataSchema() {
        return "io.github.smiskinext.event.meet.v1.MeetingInvitationsSent";
    }

    @Override
    public Message toProto(MeetingInvitationsSentEvent event) {
        MeetingInvitationsSent.Builder builder = MeetingInvitationsSent.newBuilder()
                .setMeetingId(event.meetingId().toString())
                .setTenantId(event.tenantId())
                .setMeetingShortCode(event.meetingShortCode());

        if (event.meetingTitle() != null) {
            builder.setMeetingTitle(event.meetingTitle());
        }
        if (event.startTime() != null) {
            builder.setStartTime(event.startTime().toString());
        }

        for (MeetingInvitationsSentEvent.InviteeInfo invitee : event.invitees()) {
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
