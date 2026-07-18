package io.github.smiskinext.meet.infrastructure.messaging;

import com.google.protobuf.Message;

import io.github.smiskinext.event.meet.v1.IssueLink;
import io.github.smiskinext.event.meet.v1.MeetingCreated;
import io.github.smiskinext.event.meet.v1.MeetingSettings;
import io.github.smiskinext.event.meet.v1.MeetingSnapshot;
import io.github.smiskinext.meet.domain.event.MeetingCreatedEvent;
import io.github.smiskinext.shared.infrastructure.outbox.OutboxEventProtoMapper;

import org.springframework.stereotype.Component;

@Component
public class MeetingCreatedEventProtoMapper implements OutboxEventProtoMapper<MeetingCreatedEvent> {

    @Override
    public Class<MeetingCreatedEvent> eventType() {
        return MeetingCreatedEvent.class;
    }

    @Override
    public String dataSchema() {
        return "io.github.smiskinext.event.meet.v1.MeetingCreated";
    }

    @Override
    public Message toProto(MeetingCreatedEvent event) {
        return MeetingCreated.newBuilder()
                .setSnapshot(buildSnapshot(event))
                .setCreatedAt(event.createdAt().toString())
                .build();
    }

    private MeetingSnapshot buildSnapshot(MeetingCreatedEvent event) {
        MeetingSnapshot.Builder builder = MeetingSnapshot.newBuilder()
                .setMeetingId(event.meetingId().toString())
                .setTenantId(event.tenantId())
                .setHostId(event.hostId())
                .setShortCode(event.shortCode())
                .setType(event.type())
                .setStatus(event.status())
                .setTitle(event.title())
                .setDescription(event.description())
                .setStartTime(event.startTime() != null ? event.startTime().toString() : "")
                .setEndTime(event.endTime() != null ? event.endTime().toString() : "")
                .setCreatedAt(event.createdAt().toString())
                .setZoneId(event.zoneId());

        builder.setIssueLink(IssueLink.newBuilder()
                .setIssueId(event.issueId())
                .setIssueKey(event.issueKey())
                .setProjectKey(event.projectKey())
                .build());

        io.github.smiskinext.meet.domain.model.valueobject.MeetingSettings s = event.settings();
        builder.setSettings(MeetingSettings.newBuilder()
                .setAdmissionPolicy(s.admissionPolicy().name())
                .setMaxParticipants(s.maxParticipants())
                .setAllowScreenShare(s.allowScreenShare())
                .setChatEnabled(s.chatEnabled())
                .setAllowMicrophone(s.allowMicrophone())
                .setAllowVideo(s.allowVideo())
                .build());

        return builder.build();
    }
}
