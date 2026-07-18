package io.github.smiskinext.meet.infrastructure.messaging;

import com.google.protobuf.Message;

import io.github.smiskinext.event.meet.v1.IssueLink;
import io.github.smiskinext.event.meet.v1.MeetingSettings;
import io.github.smiskinext.event.meet.v1.MeetingSnapshot;
import io.github.smiskinext.event.meet.v1.MeetingStarted;
import io.github.smiskinext.meet.domain.event.MeetingStartedEvent;
import io.github.smiskinext.shared.infrastructure.outbox.OutboxEventProtoMapper;

import org.springframework.stereotype.Component;

@Component
public class MeetingStartedEventProtoMapper implements OutboxEventProtoMapper<MeetingStartedEvent> {

    @Override
    public Class<MeetingStartedEvent> eventType() {
        return MeetingStartedEvent.class;
    }

    @Override
    public String dataSchema() {
        return "io.github.smiskinext.event.meet.v1.MeetingStarted";
    }

    @Override
    public Message toProto(MeetingStartedEvent event) {
        return MeetingStarted.newBuilder()
                .setSnapshot(buildSnapshot(event))
                .setLivekitRoomName(event.liveKitRoomName())
                .setStartedAt(event.startedAt().toString())
                .build();
    }

    private MeetingSnapshot buildSnapshot(MeetingStartedEvent event) {
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
