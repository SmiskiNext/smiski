package io.github.smiskinext.meetingmanagement.presentation.request;

import io.github.smiskinext.meetingmanagement.application.command.PutMeetingSettingsCommand;
import io.github.smiskinext.meetingmanagement.domain.model.AdmissionPolicy;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.MeetingSettings;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * API request DTO for meeting settings.
 *
 * <p>Carries client-supplied settings for creating, scheduling, or replacing a meeting,
 * including an optional plain-text password. Validation is enforced here at
 * the presentation boundary; business-rule validation (e.g. system ceiling on
 * participants) remains in the use-case layer.
 *
 * <p>Simplified field set: {@code admissionPolicy}, {@code allowGuest},
 * {@code maxParticipants}, {@code allowScreenShare}, {@code chatEnabled},
 * {@code allowMicrophone}, {@code allowVideo}, and nullable {@code password}.
 */
public record MeetingSettingsRequest(
        @NotBlank @Pattern(
                regexp = "ALLOW_ALL|MANUAL_APPROVAL",
                message = "admissionPolicy must be one of: ALLOW_ALL, MANUAL_APPROVAL")
        String admissionPolicy,

        @NotNull Boolean allowGuest,
        @NotNull @Min(1) @Max(1000) Integer maxParticipants,
        @NotNull Boolean allowScreenShare,
        @NotNull Boolean chatEnabled,
        @NotNull Boolean allowMicrophone,
        @NotNull Boolean allowVideo,

        @Nullable @Size(max = 128) @Pattern(regexp = ".*\\S.*", message = "password must not be blank") String password) {

    /**
     * Maps this request to a domain {@link MeetingSettings} with no password.
     * The raw {@code password} is returned separately so the use-case layer can hash it.
     */
    public MeetingSettings toDomain() {
        return new MeetingSettings(
                AdmissionPolicy.valueOf(admissionPolicy),
                allowGuest,
                maxParticipants,
                allowScreenShare,
                chatEnabled,
                allowMicrophone,
                allowVideo,
                null);
    }

    public PutMeetingSettingsCommand toCommand(UUID meetingId, UUID requesterId) {
        return new PutMeetingSettingsCommand(meetingId, requesterId, toDomain(), password);
    }
}
