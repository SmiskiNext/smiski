package io.github.smiskinext.meet.domain.model.valueobject;

import io.github.smiskinext.shared.domain.ValueObject;

import java.time.ZoneId;
import java.util.Objects;
import java.util.Set;

/**
 * Host time zone for a meeting, stored as an IANA region-based zone id.
 *
 * <p>Rejects unknown zone ids and bare UTC offsets such as {@code +07:00}.
 * Only region-based ids present in {@link ZoneId#getAvailableZoneIds()} are accepted.
 */
public record MeetingTimeZone(String value) implements ValueObject {

    private static final Set<String> AVAILABLE_ZONE_IDS = ZoneId.getAvailableZoneIds();

    public MeetingTimeZone {
        Objects.requireNonNull(value, "MeetingTimeZone value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("MeetingTimeZone must not be blank");
        }
        if (!AVAILABLE_ZONE_IDS.contains(value)) {
            throw new IllegalArgumentException("Unknown IANA time zone id: " + value);
        }
    }

    public static MeetingTimeZone of(String raw) {
        return new MeetingTimeZone(raw.strip());
    }
}
