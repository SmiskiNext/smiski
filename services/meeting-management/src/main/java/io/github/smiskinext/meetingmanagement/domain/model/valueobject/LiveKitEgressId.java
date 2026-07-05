package io.github.smiskinext.meetingmanagement.domain.model.valueobject;

import io.github.smiskinext.shared.domain.ValueObject;
import java.util.Objects;

/**
 * LiveKit egress ID (e.g. {@code "EG_aBj5MmUSWBFK"}).
 *
 * <p>Assigned by LiveKit when an egress (recording) is started. Used to map
 * {@code egress_started} and {@code egress_ended} webhook events back to the
 * correct {@code recordings} row, and as an idempotency key for webhook retries.
 */
public record LiveKitEgressId(String value) implements ValueObject {

    public LiveKitEgressId {
        Objects.requireNonNull(value, "LiveKitEgressId must not be null");
        if (value.isBlank())
            throw new IllegalArgumentException("LiveKitEgressId must not be blank");
    }

    public static LiveKitEgressId of(String raw) {
        return new LiveKitEgressId(raw);
    }
}
