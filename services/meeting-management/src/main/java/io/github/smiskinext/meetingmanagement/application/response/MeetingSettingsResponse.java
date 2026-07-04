package io.github.smiskinext.meetingmanagement.application.response;

import io.github.smiskinext.meetingmanagement.domain.model.valueobject.MeetingSettings;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * API response DTO for meeting settings.
 *
 * <p>Exposes only the fields that are safe to return to clients.
 * The stored password/hash value from the domain object is intentionally
 * excluded — callers never need to see it.
 *
 * <p>{@code invalidatedInviteCount} and {@code resendInvitesRecommended} are populated only when a
 * password change caused existing invite tokens to be revoked. Both fields are zero/false when no
 * invalidation occurred.
 */
@Schema(description = "Meeting settings with optional invite token invalidation metadata")
public record MeetingSettingsResponse(
        @Schema(description = "Admission policy name (e.g. WAITING_ROOM, OPEN)")
        String admissionPolicy,

        @Schema(description = "Whether unauthenticated guests may join")
        boolean allowGuest,

        @Schema(description = "Maximum concurrent participants")
        int maxParticipants,

        @Schema(description = "Whether screen sharing is enabled")
        boolean allowScreenShare,

        @Schema(description = "Whether in-meeting chat is enabled")
        boolean chatEnabled,

        @Schema(description = "Whether participants may unmute their microphone")
        boolean allowMicrophone,

        @Schema(description = "Whether participants may enable their camera")
        boolean allowVideo,

        @Schema(description = "Whether a password is required to join")
        boolean requirePassword,

        @Schema(
                description =
                        "Number of invite tokens revoked due to a password change; 0 when no invalidation occurred")
        int invalidatedInviteCount,

        @Schema(
                description =
                        "True when the host should resend invite links because tokens were revoked")
        boolean resendInvitesRecommended) {

    /**
     * Builds a response from the domain value object with no invite invalidation.
     */
    public static MeetingSettingsResponse from(MeetingSettings settings) {
        return new MeetingSettingsResponse(
                settings.admissionPolicy().name(),
                settings.allowGuest(),
                settings.maxParticipants(),
                settings.allowScreenShare(),
                settings.chatEnabled(),
                settings.allowMicrophone(),
                settings.allowVideo(),
                settings.isPasswordProtected(),
                0,
                false);
    }

    /**
     * Builds a response from the domain value object with invite invalidation details.
     */
    public static MeetingSettingsResponse from(
            MeetingSettings settings, int invalidatedInviteCount) {
        return new MeetingSettingsResponse(
                settings.admissionPolicy().name(),
                settings.allowGuest(),
                settings.maxParticipants(),
                settings.allowScreenShare(),
                settings.chatEnabled(),
                settings.allowMicrophone(),
                settings.allowVideo(),
                settings.isPasswordProtected(),
                invalidatedInviteCount,
                invalidatedInviteCount > 0);
    }
}
