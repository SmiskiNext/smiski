package io.github.smiskinext.usermanagement.infrastructure.security;

import io.github.smiskinext.usermanagement.domain.port.CaptchaVerifier;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

/**
 * Google reCAPTCHA v2 verifier implementation.
 * Verifies CAPTCHA tokens by calling Google's siteverify API.
 */
@Component
@ConditionalOnProperty(name = "app.recaptcha.enabled", havingValue = "true", matchIfMissing = false)
public class GoogleRecaptchaVerifier implements CaptchaVerifier {

    private static final Logger log = LoggerFactory.getLogger(GoogleRecaptchaVerifier.class);
    private static final String RECAPTCHA_VERIFY_URL =
            "https://www.google.com/recaptcha/api/siteverify";

    private final String secretKey;
    private final RestTemplate restTemplate;

    public GoogleRecaptchaVerifier(
            @Value("${app.recaptcha.secret-key}") String secretKey, RestTemplate restTemplate) {
        this.secretKey = secretKey;
        this.restTemplate = restTemplate;
    }

    @Override
    public boolean verifyToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("secret", secretKey);
            params.add("response", token);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

            @SuppressWarnings("unchecked")
            Map<String, Object> response =
                    restTemplate.postForObject(RECAPTCHA_VERIFY_URL, request, Map.class);

            if (response == null) {
                log.warn("reCAPTCHA verification returned null response");
                return false;
            }

            Boolean success = (Boolean) response.get("success");
            if (success == null || !success) {
                log.debug("reCAPTCHA verification failed: {}", response.get("error-codes"));
                return false;
            }

            return true;

        } catch (Exception e) {
            log.error("reCAPTCHA verification failed with exception", e);
            return false;
        }
    }
}
