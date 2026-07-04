package io.github.smiskinext.usermanagement.domain.port;

import io.github.smiskinext.usermanagement.domain.model.valueobject.HashedPassword;

/** Outbound port: password hashing and verification. */
public interface PasswordHasher {

    HashedPassword hash(String rawPassword);

    boolean verify(String rawPassword, HashedPassword hashedPassword);
}
