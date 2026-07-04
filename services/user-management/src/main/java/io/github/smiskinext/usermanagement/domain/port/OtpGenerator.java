package io.github.smiskinext.usermanagement.domain.port;

/**
 * Outbound port: generates one-time passwords (OTPs).
 */
public interface OtpGenerator {

    /**
     * Generates a secure 6-digit numeric OTP.
     *
     * @return a 6-digit OTP string (zero-padded, e.g., "048271")
     */
    String generate();
}
