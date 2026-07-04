package io.github.smiskinext.usermanagement.domain.port;

import io.github.phunguy65.zms.shared.domain.valueobject.UserId;
import io.github.smiskinext.usermanagement.domain.model.valueobject.TemporaryToken;
import io.github.smiskinext.usermanagement.domain.model.valueobject.TemporaryTokenPurpose;

public interface TemporaryTokenProvider {

    TemporaryToken generateToken(UserId userId, TemporaryTokenPurpose purpose);

    TemporaryToken validateToken(String token, TemporaryTokenPurpose expectedPurpose);
}
