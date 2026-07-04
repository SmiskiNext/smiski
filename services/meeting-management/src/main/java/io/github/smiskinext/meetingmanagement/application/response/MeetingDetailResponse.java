package io.github.smiskinext.meetingmanagement.application.response;

import io.github.smiskinext.meetingmanagement.domain.model.MeetingStatus;
import io.github.smiskinext.meetingmanagement.domain.model.MeetingType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record MeetingDetailResponse(
        UUID id,
        UUID hostId,
        String shortCode,
        @Nullable String title,
        @Nullable String description,
        @Nullable Instant startTime,
        @Nullable Instant endTime,
        MeetingType type,
        MeetingStatus status,
        MeetingSettingsResponse settings,
        Instant createdAt,
        List<MeetingParticipantResponse> participants,
        List<RecordingResponse> recordings,
        List<InviteeResponse> invitees) {}
