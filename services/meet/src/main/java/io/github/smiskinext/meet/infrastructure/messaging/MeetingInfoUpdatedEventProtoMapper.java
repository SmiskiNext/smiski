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
        return MeetingInfoUpdated.newBuilder()
                .setMeetingId(event.meetingId().toString())
                .setTenantId(event.tenantId())
                .setHostId(event.hostId())
                .setUpdatedBy(event.updatedBy())
                .setStatus(event.meetingStatus().name())
                .setOldInfo(toProto(event.oldInfo()))
                .setNewInfo(toProto(event.newInfo()))
                .setUpdatedAt(event.updatedAt().toString())
                .build();
    }

    private MeetingInfoSnapshot toProto(
            io.github.smiskinext.meet.domain.event.MeetingInfoSnapshot info) {
        MeetingInfoSnapshot.Builder builder = MeetingInfoSnapshot.newBuilder()
                .setTitle(info.title())
                .setDescription(info.description())
                .setZoneId(info.zoneId())
                .setIssueLink(IssueLink.newBuilder()
                        .setIssueId(info.issueLink().issueId())
                        .setIssueKey(info.issueLink().issueKey())
                        .setProjectKey(info.issueLink().projectKey())
                        .build());
        if (info.timeRange() != null) {
            builder.setStartTime(info.timeRange().start().toString())
                    .setEndTime(info.timeRange().end().toString());
        }
        return builder.build();
    }
}
