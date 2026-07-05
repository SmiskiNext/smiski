package io.github.smiskinext.meetingmanagement.application.service;

import io.github.smiskinext.meetingmanagement.domain.model.AdmissionPolicy;
import io.github.smiskinext.meetingmanagement.domain.model.InviteToken;
import io.github.smiskinext.meetingmanagement.domain.model.InviteTokenStatus;
import io.github.smiskinext.meetingmanagement.domain.model.Meeting;
import io.github.smiskinext.meetingmanagement.domain.model.MeetingStatus;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.InviteeId;
import io.github.smiskinext.meetingmanagement.domain.port.InviteTokenRepository;
import io.github.smiskinext.meetingmanagement.domain.port.MeetingRepository;
import io.github.smiskinext.shared.domain.valueobject.MeetingId;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Handles generation, signing, hashing, and validation of per-invitee invite tokens.
 *
 * <p>Token format: {@code base64url(HMAC-SHA256) | meetingId | inviteeId | expiryEpoch | nonce}
 * <br>All components are pipe-delimited. base64url encoding (no padding, no +/=) ensures the
 * pipe character does not appear in the signature component. The {@code nonce} is a random
 * hex string that guarantees uniqueness even when two tokens are generated within the same
 * second for the same meeting and invitee.
 *
 * <p>Token storage: only {@code SHA-256(rawToken)} is stored in the database. The raw token
 * is only ever transmitted to the invitee once via email.
 */
@Service
public class InviteTokenService {

    private static final Logger log = LoggerFactory.getLogger(InviteTokenService.class);
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String HASH_ALGORITHM = "SHA-256";
    private static final String DELIMITER = "|";
    /** Number of pipe-delimited parts in a well-formed raw token. */
    private static final int TOKEN_PARTS = 5;
    /** 60-second clock-skew grace period for expiry checks. */
    private static final long EXPIRY_GRACE_SECONDS = 60L;
    /** Bytes of randomness appended as a nonce to guarantee per-generation uniqueness. */
    private static final int NONCE_BYTES = 8;

    private final String tokenSecret;
    private final int tokenExpiryDays;
    private final InviteTokenRepository inviteTokenRepository;
    private final MeetingRepository meetingRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public InviteTokenService(
            @Value("${zms.invite.token-secret:change-me-replace-in-production}") String tokenSecret,
            @Value("${zms.invite.token-expiry-days:7}") int tokenExpiryDays,
            InviteTokenRepository inviteTokenRepository,
            MeetingRepository meetingRepository) {
        this.tokenSecret = tokenSecret;
        this.tokenExpiryDays = tokenExpiryDays;
        this.inviteTokenRepository = inviteTokenRepository;
        this.meetingRepository = meetingRepository;
    }

    /**
     * Generates a raw, URL-safe invite token string for the given meeting and invitee.
     *
     * @param meetingId  the meeting being invited to
     * @param inviteeId  the specific invitee
     * @return the raw token string to embed in the invite link
     */
    public String generateToken(MeetingId meetingId, InviteeId inviteeId) {
        Instant expiresAt = Instant.now().plus(tokenExpiryDays, ChronoUnit.DAYS);
        long expiryEpoch = expiresAt.getEpochSecond();

        byte[] nonceBytes = new byte[NONCE_BYTES];
        secureRandom.nextBytes(nonceBytes);
        String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(nonceBytes);

        String payload = meetingId.value()
                + DELIMITER
                + inviteeId.value()
                + DELIMITER
                + expiryEpoch
                + DELIMITER
                + nonce;
        String signature = computeHmac(payload);
        return signature + DELIMITER + payload;
    }

    /**
     * Computes the SHA-256 hash of the raw token for database storage.
     *
     * @param rawToken the raw token string returned from {@link #generateToken}
     * @return lowercase hex-encoded SHA-256 hash (64 characters)
     */
    public String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] hashBytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte b : hashBytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Extracts the expiry {@link Instant} from a raw token string without full validation.
     * Used when storing the token alongside the corresponding {@code InviteToken} record.
     *
     * @throws IllegalArgumentException if the token format is invalid
     */
    public Instant extractExpiresAt(String rawToken) {
        String[] parts = rawToken.split("\\|", TOKEN_PARTS);
        if (parts.length != TOKEN_PARTS) {
            throw new IllegalArgumentException(
                    "Malformed invite token: expected " + TOKEN_PARTS + " pipe-delimited parts");
        }
        try {
            long epochSeconds = Long.parseLong(parts[3]);
            return Instant.ofEpochSecond(epochSeconds);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Malformed invite token: expiryEpoch is not a number", e);
        }
    }

    /**
     * Validates an invite token string and returns the validation result.
     *
     * <p>Validation steps:
     * <ol>
     *   <li>Parse token components.</li>
     *   <li>Recompute HMAC and compare (constant-time).</li>
     *   <li>Check expiry with 60-second grace period.</li>
     *   <li>Look up token hash in DB to check revocation status.</li>
     *   <li>Verify the referenced meeting still exists and is accessible.</li>
     * </ol>
     *
     * @param rawToken the raw token string from the invite link
     * @return a {@link ValidationResult} describing the outcome
     */
    public ValidationResult validateToken(String rawToken) {
        String[] parts = rawToken.split("\\|", TOKEN_PARTS);
        if (parts.length != TOKEN_PARTS) {
            return ValidationResult.invalid("INVITE_TOKEN_INVALID");
        }

        String providedSignature = parts[0];
        String meetingIdStr = parts[1];
        String inviteeIdStr = parts[2];
        String expiryStr = parts[3];
        String nonce = parts[4];

        long expiryEpoch;
        try {
            expiryEpoch = Long.parseLong(expiryStr);
        } catch (NumberFormatException e) {
            return ValidationResult.invalid("INVITE_TOKEN_INVALID");
        }

        long nowEpoch = Instant.now().getEpochSecond();
        if (nowEpoch > expiryEpoch + EXPIRY_GRACE_SECONDS) {
            return ValidationResult.invalid("INVITE_TOKEN_EXPIRED");
        }

        String payload =
                meetingIdStr + DELIMITER + inviteeIdStr + DELIMITER + expiryStr + DELIMITER + nonce;
        String expectedSignature = computeHmac(payload);
        if (!constantTimeEquals(providedSignature, expectedSignature)) {
            return ValidationResult.invalid("INVITE_TOKEN_INVALID");
        }

        UUID meetingId;
        try {
            meetingId = UUID.fromString(meetingIdStr);
        } catch (IllegalArgumentException e) {
            return ValidationResult.invalid("INVITE_TOKEN_INVALID");
        }

        String tokenHash = hashToken(rawToken);
        Optional<InviteToken> storedToken = inviteTokenRepository.findByTokenHash(tokenHash);
        if (storedToken.isEmpty()) {
            return ValidationResult.invalid("INVITE_TOKEN_NOT_FOUND");
        }
        InviteTokenStatus dbStatus = storedToken.get().getStatus();
        if (dbStatus == InviteTokenStatus.REVOKED) {
            return ValidationResult.invalid("INVITE_TOKEN_REVOKED");
        }
        if (dbStatus == InviteTokenStatus.USED) {
            return ValidationResult.invalid("INVITE_TOKEN_USED");
        }
        if (dbStatus == InviteTokenStatus.EXPIRED) {
            return ValidationResult.invalid("INVITE_TOKEN_EXPIRED");
        }

        Optional<Meeting> meetingOpt = meetingRepository.findById(meetingId);
        if (meetingOpt.isEmpty()) {
            return ValidationResult.invalid("MEETING_NOT_FOUND");
        }
        Meeting meeting = meetingOpt.get();
        if (meeting.getStatus() == MeetingStatus.CANCELLED
                || meeting.getStatus() == MeetingStatus.ENDED) {
            return ValidationResult.invalid("MEETING_UNAVAILABLE");
        }

        boolean requiresJoinRequest =
                meeting.getSettings().admissionPolicy() == AdmissionPolicy.MANUAL_APPROVAL;

        return ValidationResult.valid(
                meetingId, meeting.getShortCode().value(), requiresJoinRequest);
    }

    private String computeHmac(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec =
                    new SecretKeySpec(tokenSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            mac.init(keySpec);
            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hmacBytes);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 computation failed", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Result of an invite token validation attempt.
     */
    public sealed interface ValidationResult {

        record Valid(UUID meetingId, String shortCode, boolean requiresJoinRequest)
                implements ValidationResult {}

        record Invalid(String errorCode) implements ValidationResult {}

        static ValidationResult valid(
                UUID meetingId, String shortCode, boolean requiresJoinRequest) {
            return new Valid(meetingId, shortCode, requiresJoinRequest);
        }

        static ValidationResult invalid(String errorCode) {
            return new Invalid(errorCode);
        }

        default boolean isValid() {
            return this instanceof Valid;
        }
    }
}
