package io.github.smiskinext.usermanagement.infrastructure.security;

import java.security.SecureRandom;

import io.github.smiskinext.usermanagement.domain.port.OtpGenerator;
import org.springframework.stereotype.Component;

/**
 * Generates 6-digit numeric OTPs using {@link SecureRandom}.
 */
@Component
public class SecureRandomOtpGenerator
        implements OtpGenerator {

    private static final int OTP_LENGTH = 6;
    private static final int OTP_BOUND = 1_000_000;

    private final SecureRandom secureRandom;

    public SecureRandomOtpGenerator() {
        this.secureRandom = new SecureRandom();
    }

    @Override
    public String generate() {
        int otp = secureRandom.nextInt(OTP_BOUND);
        return String.format("%0" + OTP_LENGTH + "d", otp);
    }
}
