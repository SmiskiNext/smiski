package io.github.smiskinext.usermanagement.infrastructure.security;

import io.github.smiskinext.usermanagement.domain.port.CaptchaVerifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * No-op CAPTCHA verifier for development/testing environments.
 * Always returns true when reCAPTCHA is disabled.
 */
@Component
@ConditionalOnProperty(name = "app.recaptcha.enabled", havingValue = "false", matchIfMissing = true)
public class NoOpCaptchaVerifier implements CaptchaVerifier {

    @Override
    public boolean verifyToken(String token) {
        return true;
    }
}
