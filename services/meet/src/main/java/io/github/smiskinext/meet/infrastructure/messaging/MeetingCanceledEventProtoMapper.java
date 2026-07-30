package io.github.smiskinext.meet.infrastructure.messaging;

import com.google.protobuf.Message;

import io.github.smiskinext.event.meet.v1.MeetingCanceled;
import io.github.smiskinext.meet.domain.event.MeetingCanceledEvent;
import io.github.smiskinext.shared.infrastructure.outbox.OutboxEventProtoMapper;

import org.springframework.stereotype.Component;

@Component
public class MeetingCanceledEventProtoMapper
        implements OutboxEventProtoMapper<MeetingCanceledEvent> {

    @Override
    public Class<MeetingCanceledEvent> eventType() {
        return MeetingCanceledEvent.class;
    }

    @Override
    public String dataSchema() {
        return "io.github.smiskinext.event.meet.v1.MeetingCanceled";
    }

    @Override
    public Message toProto(MeetingCanceledEvent event) {
        MeetingCanceled.Builder builder = MeetingCanceled.newBuilder()
                .setMeetingId(event.meetingId().toString())
                .setTenantId(event.tenantId())
                .setHostId(event.hostId())
                .setCancelReason(event.cancelReason())
                .setMeetingShortCode(event.meetingShortCode())
                .setCanceledAt(event.canceledAt().toString());

        if (event.meetingTitle() != null) {
            builder.setMeetingTitle(event.meetingTitle());
        }
        if (event.startTime() != null) {
            builder.setStartTime(event.startTime().toString());
        }

        for (MeetingCanceledEvent.InviteeInfo invitee : event.invitees()) {
            MeetingCanceled.InviteeInfo.Builder inviteeBuilder =
                    MeetingCanceled.InviteeInfo.newBuilder()
                            .setEmail(invitee.email())
                            .setStatus(invitee.status())
                            .setInvitedAt(invitee.invitedAt().toString());
            if (invitee.accountId() != null) {
                inviteeBuilder.setAccountId(invitee.accountId());
            }
            if (invitee.displayName() != null) {
                inviteeBuilder.setDisplayName(invitee.displayName());
            }
            builder.addInvitees(inviteeBuilder.build());
        }

        return builder.build();
    }
}
