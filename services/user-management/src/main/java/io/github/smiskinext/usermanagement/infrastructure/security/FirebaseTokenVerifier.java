package io.github.smiskinext.usermanagement.infrastructure.security;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import io.github.smiskinext.shared.domain.Result;
import io.github.smiskinext.usermanagement.domain.AuthError;
import io.github.smiskinext.usermanagement.domain.model.valueobject.GoogleAuthClaims;
import io.github.smiskinext.usermanagement.domain.port.GoogleAuthVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

/**
 * Firebase-backed implementation of {@link GoogleAuthVerifier}.
 * Only registered as a bean when {@link FirebaseAuth} is available in the context.
 * In tests, mock this bean directly via {@code @MockBean}.
 */
@Component
@ConditionalOnBean(FirebaseAuth.class)
public class FirebaseTokenVerifier implements GoogleAuthVerifier {

    private static final Logger log = LoggerFactory.getLogger(FirebaseTokenVerifier.class);

    private final FirebaseAuth firebaseAuth;

    public FirebaseTokenVerifier(FirebaseAuth firebaseAuth) {
        this.firebaseAuth = firebaseAuth;
    }

    @Override
    public Result<GoogleAuthClaims, AuthError> verify(String idToken) {
        try {
            var decoded = firebaseAuth.verifyIdToken(idToken);
            var claims = new GoogleAuthClaims(
                    decoded.getUid(), decoded.getEmail(), decoded.getName(), decoded.getPicture());
            return Result.success(claims);
        } catch (FirebaseAuthException e) {
            log.warn("Firebase token verification failed: {}", e.getMessage());
            return Result.failure(new AuthError.InvalidFirebaseToken());
        } catch (Exception e) {
            log.error("Unexpected error during Firebase token verification", e);
            return Result.failure(new AuthError.FirebaseAuthError());
        }
    }
}
