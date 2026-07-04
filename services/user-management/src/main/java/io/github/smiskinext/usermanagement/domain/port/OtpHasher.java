package io.github.smiskinext.usermanagement.domain.port;

/**
 * Outbound port: hashes and verifies one-time passwords (OTPs).
 */
public interface OtpHasher {

    /**
     * Computes a hash of the given OTP.
     *
     * @param otp the plaintext OTP
     * @return the hashed OTP (implementation-defined format)
     */
    String hash(String otp);

    /**
     * Verifies that a plaintext OTP matches the stored hash.
     *
     * @param otp      the plaintext OTP to verify
     * @param otpHash  the stored hash
     * @return {@code true} if the OTP matches
     */
    boolean verify(String otp, String otpHash);
}
