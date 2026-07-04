package io.github.smiskinext.meetingmanagement.domain.port;

/**
 * Outbound port: password hashing and verification for meeting rooms.
 */
public interface PasswordHasher {

    String hash(String rawPassword);

    boolean verify(String rawPassword, String hashedPassword);
}
