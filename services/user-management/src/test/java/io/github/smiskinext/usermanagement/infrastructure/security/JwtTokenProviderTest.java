package io.github.smiskinext.usermanagement.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.f4b6a3.uuid.UuidCreator;
import io.github.phunguy65.zms.shared.domain.valueobject.UserId;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

    private JwtTokenProvider provider;
    private static final String SECRET =
            "test-secret-key-must-be-at-least-sixty-four-bytes-long-for-hs512-algorithm!!";
    private static final long EXPIRY = 900L;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider(SECRET, EXPIRY);
    }

    @Test
    void generateAndValidateToken() {
        UserId userId = UserId.of(UuidCreator.getTimeOrderedEpoch());
        String token = provider.generateAccessToken(userId, "alice@example.com");

        assertThat(provider.validateToken(token)).isTrue();
    }

    @Test
    void extractUserIdFromToken() {
        UserId userId = UserId.of(UuidCreator.getTimeOrderedEpoch());
        String token = provider.generateAccessToken(userId, "alice@example.com");

        assertThat(provider.extractUserId(token)).isEqualTo(userId);
    }

    @Test
    void extractEmailFromToken() {
        UserId userId = UserId.of(UuidCreator.getTimeOrderedEpoch());
        String token = provider.generateAccessToken(userId, "alice@example.com");

        assertThat(provider.extractEmail(token)).isEqualTo("alice@example.com");
    }

    @Test
    void invalidTokenReturnsFalse() {
        assertThat(provider.validateToken("not.a.valid.token")).isFalse();
        assertThat(provider.validateToken("")).isFalse();
    }

    @Test
    void expiredTokenReturnsFalse() throws InterruptedException {
        JwtTokenProvider shortLived = new JwtTokenProvider(SECRET, 1L);
        String token = shortLived.generateAccessToken(
                UserId.of(UuidCreator.getTimeOrderedEpoch()), "test@example.com");

        Thread.sleep(1500);

        assertThat(shortLived.validateToken(token)).isFalse();
    }

    @Test
    void generatedTokenUsesHs512Algorithm() throws Exception {
        UserId userId = UserId.of(UuidCreator.getTimeOrderedEpoch());
        String token = provider.generateAccessToken(userId, "alice@example.com");

        String headerSegment = token.split("\\.")[0];
        byte[] headerBytes = Base64.getUrlDecoder().decode(headerSegment);
        JsonNode header = MAPPER.readTree(headerBytes);

        assertThat(header.get("alg").asText()).isEqualTo("HS512");
        assertThat(header.get("kid").asText()).isEqualTo("zms-user-management");
    }
}
