package io.github.smiskinext.meet.infrastructure.messaging;

import com.google.protobuf.Message;

import io.github.smiskinext.event.meet.v1.MeetingInvitationsDeleted;
import io.github.smiskinext.meet.domain.event.MeetingInvitationsDeletedEvent;
import io.github.smiskinext.shared.infrastructure.outbox.OutboxEventProtoMapper;

import org.springframework.stereotype.Component;

@Component
public class MeetingInvitationsDeletedEventProtoMapper
        implements OutboxEventProtoMapper<MeetingInvitationsDeletedEvent> {

    @Override
    public Class<MeetingInvitationsDeletedEvent> eventType() {
        return MeetingInvitationsDeletedEvent.class;
    }

    @Override
    public String dataSchema() {
        return "io.github.smiskinext.event.meet.v1.MeetingInvitationsDeleted";
    }

    @Override
    public Message toProto(MeetingInvitationsDeletedEvent event) {
        MeetingInvitationsDeleted.Builder builder = MeetingInvitationsDeleted.newBuilder()
                .setMeetingId(event.meetingId().toString())
                .setTenantId(event.tenantId())
                .setMeetingShortCode(event.meetingShortCode())
                .setZoneId(event.zoneId())
                .setCalendarUid(event.calendarUid())
                .setCalendarSequence(event.calendarSequence());

        if (event.organizerEmail() != null) builder.setOrganizerEmail(event.organizerEmail());
        if (event.organizerDisplayName() != null) {
            builder.setOrganizerDisplayName(event.organizerDisplayName());
        }

        if (event.meetingTitle() != null) {
            builder.setMeetingTitle(event.meetingTitle());
        }
        if (event.startTime() != null) {
            builder.setStartTime(event.startTime().toString());
        }
        if (event.endTime() != null) {
            builder.setEndTime(event.endTime().toString());
        }

        for (MeetingInvitationsDeletedEvent.InviteeInfo invitee : event.invitees()) {
            MeetingInvitationsDeleted.InviteeInfo.Builder inviteeBuilder =
                    MeetingInvitationsDeleted.InviteeInfo.newBuilder()
                            .setAccountId(invitee.accountId())
                            .setEmail(invitee.email())
                            .setDisplayName(invitee.displayName())
                            .setInviteeId(invitee.inviteeId().toString())
                            .setStatus(invitee.status());
            builder.addInvitees(inviteeBuilder.build());
        }

        return builder.build();
    }
}
