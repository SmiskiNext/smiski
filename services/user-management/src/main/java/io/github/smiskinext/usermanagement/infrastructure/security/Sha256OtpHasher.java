package io.github.smiskinext.usermanagement.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import io.github.smiskinext.usermanagement.domain.port.OtpHasher;
import org.springframework.stereotype.Component;

/**
 * Hashes OTPs using SHA-256 for secure storage.
 */
@Component
public class Sha256OtpHasher
        implements OtpHasher {

    private static final String ALGORITHM = "SHA-256";

    @Override
    public String hash(String otp) {
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            byte[] hashBytes = digest.digest(otp.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    @Override
    public boolean verify(String otp, String otpHash) {
        String computedHash = hash(otp);
        return MessageDigest.isEqual(
                computedHash.getBytes(StandardCharsets.UTF_8),
                otpHash.getBytes(StandardCharsets.UTF_8));
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
