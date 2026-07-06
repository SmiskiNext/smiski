package io.github.smiskinext.meet.domain.model.valueobject;

import io.github.smiskinext.meet.domain.model.AdmissionPolicy;
import io.github.smiskinext.shared.domain.ValueObject;

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
 * {@code allowMicrophone}, and {@code allowVideo}.
 */
public record MeetingSettings(
        AdmissionPolicy admissionPolicy,
        boolean allowGuest,
        int maxParticipants,
        boolean allowScreenShare,
        boolean chatEnabled,
        boolean allowMicrophone,
        boolean allowVideo)
        implements ValueObject {

    /**
     * Default settings.
     *
     * <p>Defaults: {@code allowScreenShare=true}, {@code allowMicrophone=true},
     * {@code allowVideo=true}, {@code chatEnabled=true}, {@code maxParticipants=100},
     * {@code allowGuest=true}.
     */
    public static MeetingSettings defaults() {
        return new MeetingSettings(
                AdmissionPolicy.MANUAL_APPROVAL, true, 100, true, true, true, true);
    }
}
