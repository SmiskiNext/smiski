package io.github.smiskinext.meet.domain.model.valueobject;

import io.github.smiskinext.shared.domain.ValueObject;

import java.util.Objects;

/**
 * LiveKit JWT {@code sub} (identity) claim for a participant.
 *
 * <p>Format: {@code "<accountId>:<deviceId>"}.
 *
 * <p>The identity is unique per room per device. Using {@code accountId:deviceId} allows the same
 * account to join from multiple devices simultaneously without triggering
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
     * Creates an identity for an account joining from a specific device.
     */
    public static LiveKitIdentity fromAccount(AccountId accountId, String deviceId) {
        Objects.requireNonNull(accountId, "accountId must not be null");
        Objects.requireNonNull(deviceId, "deviceId must not be null");
        return new LiveKitIdentity(accountId.value() + ":" + deviceId);
    }
}
