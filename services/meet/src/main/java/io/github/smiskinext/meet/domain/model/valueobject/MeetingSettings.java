package io.github.smiskinext.meet.domain.model.valueobject;

import io.github.smiskinext.meet.domain.model.AdmissionPolicy;
import io.github.smiskinext.shared.domain.ValueObject;

/**
 * Meeting room settings stored as JSONB in the database.
 *
 * <p>Mapped via {@code @JdbcTypeCode(SqlTypes.JSON)} on the JPA entity.
 * Jackson serializes the {@link AdmissionPolicy} enum to its name automatically.
 *
 * <p>Simplified field set: {@code admissionPolicy}, {@code maxParticipants},
 * {@code allowScreenShare}, {@code chatEnabled}, {@code allowMicrophone},
 * and {@code allowVideo}.
 */
public record MeetingSettings(
        AdmissionPolicy admissionPolicy,
        int maxParticipants,
        boolean allowScreenShare,
        boolean chatEnabled,
        boolean allowMicrophone,
        boolean allowVideo)
        implements ValueObject {

    public static final int MIN_PARTICIPANTS = 2;
    public static final int MAX_PARTICIPANTS = 100;

    public MeetingSettings {
        if (maxParticipants < MIN_PARTICIPANTS || maxParticipants > MAX_PARTICIPANTS) {
            throw new IllegalArgumentException("maxParticipants must be between " + MIN_PARTICIPANTS
                    + " and " + MAX_PARTICIPANTS);
        }
    }

    /**
     * Default settings.
     *
     * <p>Defaults: {@code allowScreenShare=true}, {@code allowMicrophone=true},
     * {@code allowVideo=true}, {@code chatEnabled=true}, {@code maxParticipants=100}.
     */
    public static MeetingSettings defaults() {
        return new MeetingSettings(AdmissionPolicy.MANUAL_APPROVAL, 100, true, true, true, true);
    }
}
