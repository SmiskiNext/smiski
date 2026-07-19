package io.github.smiskinext.meet.infrastructure.messaging;

import com.google.protobuf.Message;

import io.github.smiskinext.event.meet.v1.MeetingSettings;
import io.github.smiskinext.event.meet.v1.MeetingSettingsUpdated;
import io.github.smiskinext.meet.domain.event.MeetingSettingsUpdatedEvent;
import io.github.smiskinext.shared.infrastructure.outbox.OutboxEventProtoMapper;

import org.springframework.stereotype.Component;

@Component
public class MeetingSettingsUpdatedEventProtoMapper
        implements OutboxEventProtoMapper<MeetingSettingsUpdatedEvent> {

    @Override
    public Class<MeetingSettingsUpdatedEvent> eventType() {
        return MeetingSettingsUpdatedEvent.class;
    }

    @Override
    public String dataSchema() {
        return "io.github.smiskinext.event.meet.v1.MeetingSettingsUpdated";
    }

    @Override
    public Message toProto(MeetingSettingsUpdatedEvent event) {
        return MeetingSettingsUpdated.newBuilder()
                .setMeetingId(event.meetingId().toString())
                .setTenantId(event.tenantId())
                .setHostId(event.hostId())
                .setUpdatedBy(event.updatedBy())
                .setStatus(event.meetingStatus().name())
                .setOldSettings(toProto(event.oldSettings()))
                .setNewSettings(toProto(event.newSettings()))
                .setUpdatedAt(event.updatedAt().toString())
                .build();
    }

    private MeetingSettings toProto(
            io.github.smiskinext.meet.domain.model.valueobject.MeetingSettings settings) {
        return MeetingSettings.newBuilder()
                .setAdmissionPolicy(settings.admissionPolicy().name())
                .setMaxParticipants(settings.maxParticipants())
                .setAllowScreenShare(settings.allowScreenShare())
                .setChatEnabled(settings.chatEnabled())
                .setAllowMicrophone(settings.allowMicrophone())
                .setAllowVideo(settings.allowVideo())
                .build();
    }
}
