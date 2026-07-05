package io.github.smiskinext.usermanagement.domain.port;

import io.github.smiskinext.shared.domain.valueobject.UserId;
import io.github.smiskinext.usermanagement.domain.model.RefreshToken;
import java.util.Optional;

/** Outbound port: persistence operations for the {@link RefreshToken} aggregate. */
public interface RefreshTokenRepository {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    RefreshToken save(RefreshToken refreshToken);

    void revokeAllByUserId(UserId userId);
}
