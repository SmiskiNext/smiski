package io.github.smiskinext.meetingmanagement.domain.model.valueobject;

import io.github.phunguy65.zms.shared.domain.ValueObject;
import io.github.phunguy65.zms.shared.domain.valueobject.UserId;
import java.util.Objects;

/**
 * LiveKit JWT {@code sub} (identity) claim for a participant.
 *
 * <p>Format:
 * <ul>
 *   <li>Authenticated user: {@code "<userId>:<deviceId>"}
 *   <li>Guest: {@code "guest:<deviceId>"}
 * </ul>
 *
 * <p>The identity is unique per room per device. Using {@code userId:deviceId} allows the same
 * user to join from multiple devices simultaneously without triggering
 * {@code DUPLICATE_IDENTITY} disconnects.
 */
public record LiveKitIdentity(String value) implements ValueObject {

    public LiveKitIdentity {
        Objects.requireNonNull(value, "LiveKitIdentity must not be null");
        if (value.isBlank())
            throw new IllegalArgumentException("LiveKitIdentity must not be blank");
    }

    public static LiveKitIdentity of(String raw) {
        return new LiveKitIdentity(raw);
    }

    /**
     * Creates an identity for an authenticated user joining from a specific device.
     */
    public static LiveKitIdentity fromUser(UserId userId, String deviceId) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(deviceId, "deviceId must not be null");
        return new LiveKitIdentity(userId.value() + ":" + deviceId);
    }

    /**
     * Creates an identity for a guest joining from a specific device.
     */
    public static LiveKitIdentity forGuest(String deviceId) {
        Objects.requireNonNull(deviceId, "deviceId must not be null");
        return new LiveKitIdentity("guest:" + deviceId);
    }

    public boolean isGuest() {
        return value.startsWith("guest:");
    }
}
