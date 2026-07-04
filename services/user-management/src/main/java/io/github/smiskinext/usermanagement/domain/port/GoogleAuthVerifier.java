package io.github.smiskinext.usermanagement.domain.port;

import io.github.phunguy65.zms.shared.domain.Result;
import io.github.smiskinext.usermanagement.domain.AuthError;
import io.github.smiskinext.usermanagement.domain.model.valueobject.GoogleAuthClaims;

public interface GoogleAuthVerifier {

    Result<GoogleAuthClaims, AuthError> verify(String idToken);
}
