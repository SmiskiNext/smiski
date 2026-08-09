package io.github.smiskinext.meet.presentation.response;

import io.github.smiskinext.meet.application.result.ListMeetingsResult;
import io.github.smiskinext.meet.domain.model.MeetingStatus;
import io.github.smiskinext.meet.domain.model.MeetingType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * List item describing a single tenant meeting. Deliberately omits the tenant identifier.
 */
@Schema(name = "MeetingSummary", description = "Meeting summary for the tenant listing")
public record MeetingSummaryResponse(
        UUID id,
        String hostId,
        String shortCode,
        @Nullable String title,
        @Nullable String description,
        String issueId,
        String issueKey,
        String projectKey,
        MeetingType type,
        MeetingStatus status,

        @Schema(
                description = "Scheduled start time; null for meetings without one",
                nullable = true)
        @Nullable Instant startTime,

        @Nullable Instant endTime,
        @Nullable String organizerDisplayName,
        Instant createdAt,
        Settings settings) {

    @Schema(name = "MeetingSummarySettings", description = "Meeting room settings")
    public record Settings(
            String admissionPolicy,
            int maxParticipants,
            boolean allowScreenShare,
            boolean chatEnabled,
            boolean allowMicrophone,
            boolean allowVideo) {}

    public static MeetingSummaryResponse from(ListMeetingsResult.Item item) {
        ListMeetingsResult.Item.Settings s = item.settings();
        Settings settings = new Settings(
                s.admissionPolicy(),
                s.maxParticipants(),
                s.allowScreenShare(),
                s.chatEnabled(),
                s.allowMicrophone(),
                s.allowVideo());

        return new MeetingSummaryResponse(
                item.id(),
                item.hostId(),
                item.shortCode(),
                item.title(),
                item.description(),
                item.issueId(),
                item.issueKey(),
                item.projectKey(),
                item.type(),
                item.status(),
                item.startTime(),
                item.endTime(),
                item.organizerDisplayName(),
                item.createdAt(),
                settings);
    }
}
