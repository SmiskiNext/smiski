package io.github.smiskinext.meetingmanagement.application.response;

import io.github.smiskinext.meetingmanagement.domain.model.Meeting;
import io.github.smiskinext.meetingmanagement.domain.model.MeetingInvitee;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.MeetingTitle;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Schema(description = "Pending invitation visible to an invitee")
public record PendingInvitationResponse(
        @Schema(description = "Invitee ID") UUID inviteeId,
        @Schema(description = "Meeting ID") UUID meetingId,
        @Schema(description = "Meeting title") @Nullable String meetingTitle,
        @Schema(description = "Meeting short code") String meetingShortCode,

        @Schema(description = "Meeting start time") @Nullable Instant startTime,

        @Schema(description = "Meeting host display name") String hostDisplayName,

        @Schema(description = "When the invitation was sent")
        Instant invitedAt) {

    public static PendingInvitationResponse from(
            MeetingInvitee invitee, Meeting meeting, String hostDisplayName) {
        return new PendingInvitationResponse(
                invitee.getId().value(),
                meeting.getId().value(),
                meeting.getTitle().map(MeetingTitle::value).orElse(null),
                meeting.getShortCode().value(),
                meeting.getStartTime().orElse(null),
                hostDisplayName,
                invitee.getInvitedAt());
    }
}
