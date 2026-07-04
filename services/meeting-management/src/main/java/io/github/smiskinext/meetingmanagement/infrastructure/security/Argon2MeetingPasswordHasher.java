package io.github.smiskinext.meetingmanagement.infrastructure.security;

import io.github.smiskinext.meetingmanagement.domain.port.PasswordHasher;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Argon2id password hasher for meeting room passwords.
 *
 * <p>Uses lighter parameters than user-account passwords since meeting PINs are lower-value
 * targets: 16 MB memory, 1 iteration, 1 thread (~50 ms on modern hardware).
 */
@Component
public class Argon2MeetingPasswordHasher implements PasswordHasher {

    private final Argon2PasswordEncoder encoder = new Argon2PasswordEncoder(16, 32, 1, 16384, 1);

    @Override
    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    @Override
    public boolean verify(String rawPassword, String hashedPassword) {
        return encoder.matches(rawPassword, hashedPassword);
    }
}
