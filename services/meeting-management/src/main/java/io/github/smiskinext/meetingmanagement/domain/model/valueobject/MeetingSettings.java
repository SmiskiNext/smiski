package io.github.smiskinext.meetingmanagement.domain.model.valueobject;

import io.github.smiskinext.meetingmanagement.domain.model.AdmissionPolicy;
import io.github.phunguy65.zms.shared.domain.ValueObject;
import org.jspecify.annotations.Nullable;

/**
 * Meeting room settings stored as JSONB in the database.
 *
 * <p>Mapped via {@code @JdbcTypeCode(SqlTypes.JSON)} on the JPA entity.
 * This is a pure domain value object — it carries no Jackson or persistence
 * annotations. Serialization concerns are handled by dedicated DTOs:
 * <ul>
 *   <li>{@code MeetingSettingsJson} — persistence layer (JSONB)</li>
 *   <li>{@code MeetingSettingsRequest} — API input</li>
 *   <li>{@code MeetingSettingsResponse} — API output</li>
 * </ul>
 *
 * <p>Simplified field set: {@code admissionPolicy}, {@code allowGuest},
 * {@code maxParticipants}, {@code allowScreenShare}, {@code chatEnabled},
 * {@code allowMicrophone}, {@code allowVideo}, and nullable {@code password}.
 */
public record MeetingSettings(
        AdmissionPolicy admissionPolicy,
        boolean allowGuest,
        int maxParticipants,
        boolean allowScreenShare,
        boolean chatEnabled,
        boolean allowMicrophone,
        boolean allowVideo,
        @Nullable String password)
        implements ValueObject {

    /**
     * Returns {@code true} if this meeting requires a password to join.
     */
    public boolean isPasswordProtected() {
        return password != null && !password.isBlank();
    }

    /**
     * Default settings (no password).
     *
     * <p>Defaults: {@code allowScreenShare=true}, {@code allowMicrophone=true},
     * {@code allowVideo=true}, {@code chatEnabled=true}, {@code maxParticipants=100},
     * {@code allowGuest=true}.
     */
    public static MeetingSettings defaults() {
        return new MeetingSettings(
                AdmissionPolicy.MANUAL_APPROVAL,
                true, // allowGuest
                100, // maxParticipants
                true, // allowScreenShare
                true, // chatEnabled
                true, // allowMicrophone
                true, // allowVideo
                null); // password
    }
}
