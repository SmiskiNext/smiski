package io.github.smiskinext.usermanagement.domain.port;

public interface CaptchaVerifier {

    boolean verifyToken(String token);
}
