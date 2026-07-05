package io.github.smiskinext.usermanagement.domain.port;

import io.github.smiskinext.shared.domain.valueobject.UserId;

public interface TokenProvider {

    String generateAccessToken(UserId userId, String email);

    long getAccessTokenExpirySeconds();

    boolean validateToken(String token);

    UserId extractUserId(String token);

    String extractEmail(String token);
}
