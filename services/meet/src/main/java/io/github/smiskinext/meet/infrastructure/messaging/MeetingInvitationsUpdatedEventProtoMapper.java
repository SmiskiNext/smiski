package io.github.smiskinext.meet.infrastructure.messaging;

import com.google.protobuf.Message;

import io.github.smiskinext.event.meet.v1.MeetingInvitationsUpdated;
import io.github.smiskinext.meet.domain.event.MeetingInvitationsUpdatedEvent;
import io.github.smiskinext.shared.infrastructure.outbox.OutboxEventProtoMapper;

import org.springframework.stereotype.Component;

@Component
public class MeetingInvitationsUpdatedEventProtoMapper
        implements OutboxEventProtoMapper<MeetingInvitationsUpdatedEvent> {

    @Override
    public Class<MeetingInvitationsUpdatedEvent> eventType() {
        return MeetingInvitationsUpdatedEvent.class;
    }

    @Override
    public String dataSchema() {
        return "io.github.smiskinext.event.meet.v1.MeetingInvitationsUpdated";
    }

    @Override
    public Message toProto(MeetingInvitationsUpdatedEvent event) {
        MeetingInvitationsUpdated.Builder builder = MeetingInvitationsUpdated.newBuilder()
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

        for (MeetingInvitationsUpdatedEvent.InviteeInfo invitee : event.invitees()) {
            MeetingInvitationsUpdated.InviteeInfo.Builder inviteeBuilder =
                    MeetingInvitationsUpdated.InviteeInfo.newBuilder()
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
