package io.github.smiskinext.meet.infrastructure.messaging;

import com.google.protobuf.Message;

import io.github.smiskinext.event.meet.v1.MeetingInvitationsCreated;
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
        MeetingInvitationsCreated.Builder builder = MeetingInvitationsCreated.newBuilder()
                .setMeetingId(event.meetingId().toString())
                .setTenantId(event.tenantId())
                .setMeetingShortCode(event.meetingShortCode())
                .setZoneId(event.zoneId())
                .setCalendarUid(event.calendarUid())
                .setCalendarSequence(event.calendarSequence())
                .setIssueId(event.issueId())
                .setIssueKey(event.issueKey())
                .setProjectKey(event.projectKey());

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

        for (MeetingInvitationsCreatedEvent.InviteeInfo invitee : event.invitees()) {
            MeetingInvitationsCreated.InviteeInfo.Builder inviteeBuilder =
                    MeetingInvitationsCreated.InviteeInfo.newBuilder()
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
