package io.github.smiskinext.usermanagement.domain.port;

import io.github.phunguy65.zms.shared.domain.valueobject.UserId;

public interface TokenProvider {

    String generateAccessToken(UserId userId, String email);

    long getAccessTokenExpirySeconds();

    boolean validateToken(String token);

    UserId extractUserId(String token);

    String extractEmail(String token);
}
