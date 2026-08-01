package io.github.smiskinext.meet.infrastructure.messaging;

import com.google.protobuf.Message;

import io.github.smiskinext.event.meet.v1.IssueLink;
import io.github.smiskinext.event.meet.v1.MeetingInfoSnapshot;
import io.github.smiskinext.event.meet.v1.MeetingInfoUpdated;
import io.github.smiskinext.meet.domain.event.MeetingInfoUpdatedEvent;
import io.github.smiskinext.shared.infrastructure.outbox.OutboxEventProtoMapper;

import org.springframework.stereotype.Component;

@Component
public class MeetingInfoUpdatedEventProtoMapper
        implements OutboxEventProtoMapper<MeetingInfoUpdatedEvent> {

    @Override
    public Class<MeetingInfoUpdatedEvent> eventType() {
        return MeetingInfoUpdatedEvent.class;
    }

    @Override
    public String dataSchema() {
        return "io.github.smiskinext.meet.v1.MeetingInfoUpdated";
    }

    @Override
    public Message toProto(MeetingInfoUpdatedEvent event) {
        MeetingInfoUpdated.Builder builder = MeetingInfoUpdated.newBuilder()
                .setMeetingId(event.meetingId().toString())
                .setTenantId(event.tenantId())
                .setHostId(event.hostId())
                .setUpdatedBy(event.updatedBy())
                .setStatus(event.meetingStatus().name())
                .setOldInfo(toProto(event.oldInfo()))
                .setNewInfo(toProto(event.newInfo()))
                .setUpdatedAt(event.updatedAt().toString());

        for (MeetingInfoUpdatedEvent.InviteeInfo invitee : event.invitees()) {
            builder.addInvitees(MeetingInfoUpdated.InviteeInfo.newBuilder()
                    .setAccountId(invitee.accountId())
                    .setEmail(invitee.email())
                    .setDisplayName(invitee.displayName())
                    .setInviteeId(invitee.inviteeId().toString())
                    .setStatus(invitee.status())
                    .build());
        }

        return builder.build();
    }

    private MeetingInfoSnapshot toProto(
            io.github.smiskinext.meet.domain.event.MeetingInfoSnapshot info) {
        MeetingInfoSnapshot.Builder builder = MeetingInfoSnapshot.newBuilder()
                .setTitle(info.title())
                .setDescription(info.description())
                .setZoneId(info.zoneId())
                .setCalendarUid(info.calendarUid())
                .setCalendarSequence(info.calendarSequence())
                .setIssueLink(IssueLink.newBuilder()
                        .setIssueId(info.issueLink().issueId())
                        .setIssueKey(info.issueLink().issueKey())
                        .setProjectKey(info.issueLink().projectKey())
                        .build());
        if (info.timeRange() != null) {
            builder.setStartTime(info.timeRange().start().toString())
                    .setEndTime(info.timeRange().end().toString());
        }
        if (info.organizerEmail() != null) builder.setOrganizerEmail(info.organizerEmail());
        if (info.organizerDisplayName() != null) {
            builder.setOrganizerDisplayName(info.organizerDisplayName());
        }
        return builder.build();
    }
}
