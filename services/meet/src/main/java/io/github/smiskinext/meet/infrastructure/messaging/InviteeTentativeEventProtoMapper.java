package io.github.smiskinext.meet.infrastructure.messaging;

import com.google.protobuf.Message;

import io.github.smiskinext.event.meet.v1.InviteeTentative;
import io.github.smiskinext.meet.domain.event.InviteeTentativeEvent;
import io.github.smiskinext.shared.infrastructure.outbox.OutboxEventProtoMapper;

import org.springframework.stereotype.Component;

@Component
public class InviteeTentativeEventProtoMapper
        implements OutboxEventProtoMapper<InviteeTentativeEvent> {
    @Override
    public Class<InviteeTentativeEvent> eventType() {
        return InviteeTentativeEvent.class;
    }

    @Override
    public String dataSchema() {
        return "io.github.smiskinext.event.meet.v1.InviteeTentative";
    }

    @Override
    public Message toProto(InviteeTentativeEvent event) {
        InviteeTentative.Builder builder = InviteeTentative.newBuilder()
                .setEventId(event.eventId().toString())
                .setTenantId(event.tenantId())
                .setMeetingId(event.meetingId().toString())
                .setInviterId(event.inviterId())
                .setInviteeId(event.inviteeId().toString())
                .setInviteeEmail(event.inviteeEmail())
                .setStatus(event.status())
                .setTentativeAt(event.tentativeAt().toString())
                .setZoneId(event.zoneId())
                .setOrganizerEmail(event.organizerEmail())
                .setOrganizerDisplayName(event.organizerDisplayName())
                .setInviteeDisplayName(event.inviteeDisplayName())
                .setCalendarUid(event.calendarUid())
                .setCalendarSequence(event.calendarSequence())
                .setIssueId(event.issueId())
                .setIssueKey(event.issueKey())
                .setProjectKey(event.projectKey())
                .setShortCode(event.shortCode());

        if (event.meetingTitle() != null) {
            builder.setMeetingTitle(event.meetingTitle());
        }
        if (event.startTime() != null) {
            builder.setStartTime(event.startTime().toString());
        }
        if (event.endTime() != null) {
            builder.setEndTime(event.endTime().toString());
        }

        return builder.build();
    }
}
