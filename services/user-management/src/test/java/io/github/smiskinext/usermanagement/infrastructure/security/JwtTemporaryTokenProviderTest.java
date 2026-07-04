package io.github.smiskinext.usermanagement.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.phunguy65.zms.shared.domain.valueobject.UserId;
import io.github.smiskinext.usermanagement.domain.model.valueobject.TemporaryTokenPurpose;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class JwtTemporaryTokenProviderTest {

    private static final String TEST_SECRET =
            "test-secret-key-must-be-at-least-512-bits-long-for-HS512-algorithm-so-this-is-a-very-long-secret";

    JwtTemporaryTokenProvider provider;

    UserId testUserId;

    @BeforeEach
    void setUp() {
        provider = new JwtTemporaryTokenProvider(TEST_SECRET);
        testUserId = UserId.of(UuidCreator.getTimeOrderedEpoch());
    }

    @Nested
    class GenerateToken {

        @Test
        void generatesNonBlankToken() {
            var token = provider.generateToken(testUserId, TemporaryTokenPurpose.PASSWORD_RESET);

            assertThat(token.value()).isNotBlank();
        }

        @Test
        void generatesTokenWithCorrectUserId() {
            var token = provider.generateToken(testUserId, TemporaryTokenPurpose.PASSWORD_RESET);

            assertThat(token.userId()).isEqualTo(testUserId);
        }

        @Test
        void generatesTokenWithCorrectPurpose() {
            var token = provider.generateToken(testUserId, TemporaryTokenPurpose.PASSWORD_RESET);

            assertThat(token.purpose()).isEqualTo(TemporaryTokenPurpose.PASSWORD_RESET);
        }

        @Test
        void generatesTokenWith5MinuteExpiry() {
            Instant before = Instant.now().plus(Duration.ofMinutes(5)).minusSeconds(1);
            var token = provider.generateToken(testUserId, TemporaryTokenPurpose.PASSWORD_RESET);
            Instant after = Instant.now().plus(Duration.ofMinutes(5)).plusSeconds(1);

            assertThat(token.expiresAt()).isBetween(before, after);
        }

        @Test
        void generatesTokenWithTimeToLiveApproximately5Minutes() {
            var token = provider.generateToken(testUserId, TemporaryTokenPurpose.PASSWORD_RESET);

            Duration ttl = token.timeToLive();
            assertThat(ttl.toSeconds()).isBetween(299L, 301L);
        }

        @Test
        void generatesUniqueTokensForSameUser() throws InterruptedException {
            var token1 = provider.generateToken(testUserId, TemporaryTokenPurpose.PASSWORD_RESET);
            Thread.sleep(1001);
            var token2 = provider.generateToken(testUserId, TemporaryTokenPurpose.PASSWORD_RESET);

            assertThat(token1.value()).isNotEqualTo(token2.value());
        }
    }

    @Nested
    class ValidateToken {

        @Test
        void validatesValidToken() {
            var generatedToken =
                    provider.generateToken(testUserId, TemporaryTokenPurpose.PASSWORD_RESET);

            var validatedToken = provider.validateToken(
                    generatedToken.value(), TemporaryTokenPurpose.PASSWORD_RESET);

            assertThat(validatedToken.userId()).isEqualTo(testUserId);
            assertThat(validatedToken.purpose()).isEqualTo(TemporaryTokenPurpose.PASSWORD_RESET);
        }

        @Test
        void extractsCorrectUserIdFromToken() {
            var generatedToken =
                    provider.generateToken(testUserId, TemporaryTokenPurpose.PASSWORD_RESET);

            var validatedToken = provider.validateToken(
                    generatedToken.value(), TemporaryTokenPurpose.PASSWORD_RESET);

            assertThat(validatedToken.userId()).isEqualTo(testUserId);
        }

        @Test
        void extractsCorrectPurposeFromToken() {
            var generatedToken =
                    provider.generateToken(testUserId, TemporaryTokenPurpose.PASSWORD_RESET);

            var validatedToken = provider.validateToken(
                    generatedToken.value(), TemporaryTokenPurpose.PASSWORD_RESET);

            assertThat(validatedToken.purpose()).isEqualTo(TemporaryTokenPurpose.PASSWORD_RESET);
        }

        @Test
        void extractsCorrectExpiryFromToken() {
            var generatedToken =
                    provider.generateToken(testUserId, TemporaryTokenPurpose.PASSWORD_RESET);

            var validatedToken = provider.validateToken(
                    generatedToken.value(), TemporaryTokenPurpose.PASSWORD_RESET);

            assertThat(validatedToken.expiresAt().getEpochSecond())
                    .isEqualTo(generatedToken.expiresAt().getEpochSecond());
        }

        @Test
        void throwsExceptionForInvalidSignature() {
            String invalidToken =
                    "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4g"
                            + "RG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.invalid-signature";

            assertThatThrownBy(() -> provider.validateToken(
                            invalidToken, TemporaryTokenPurpose.PASSWORD_RESET))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid temporary token");
        }

        @Test
        void throwsExceptionForMalformedToken() {
            String malformedToken = "not-a-jwt-token";

            assertThatThrownBy(() -> provider.validateToken(
                            malformedToken, TemporaryTokenPurpose.PASSWORD_RESET))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid temporary token");
        }

        @Test
        void throwsExceptionForBlankToken() {
            assertThatThrownBy(
                            () -> provider.validateToken("", TemporaryTokenPurpose.PASSWORD_RESET))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void throwsExceptionForNullToken() {
            assertThatThrownBy(() ->
                            provider.validateToken(null, TemporaryTokenPurpose.PASSWORD_RESET))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class PurposeValidation {

        @Test
        void throwsExceptionWhenPurposeMismatch() {
            var generatedToken =
                    provider.generateToken(testUserId, TemporaryTokenPurpose.PASSWORD_RESET);

            assertThatThrownBy(() -> provider.validateToken(
                            generatedToken.value(), TemporaryTokenPurpose.EMAIL_VERIFICATION))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Token purpose mismatch");
        }

        @Test
        void acceptsTokenWithMatchingPurpose() {
            var generatedToken =
                    provider.generateToken(testUserId, TemporaryTokenPurpose.PASSWORD_RESET);

            var validatedToken = provider.validateToken(
                    generatedToken.value(), TemporaryTokenPurpose.PASSWORD_RESET);

            assertThat(validatedToken.purpose()).isEqualTo(TemporaryTokenPurpose.PASSWORD_RESET);
        }
    }

    @Nested
    class ExpiryHandling {

        @Test
        void validatesNonExpiredToken() {
            var generatedToken =
                    provider.generateToken(testUserId, TemporaryTokenPurpose.PASSWORD_RESET);

            var validatedToken = provider.validateToken(
                    generatedToken.value(), TemporaryTokenPurpose.PASSWORD_RESET);

            assertThat(validatedToken.isExpired()).isFalse();
        }

        @Test
        void tokenIsNotExpiredImmediatelyAfterGeneration() {
            var token = provider.generateToken(testUserId, TemporaryTokenPurpose.PASSWORD_RESET);

            assertThat(token.isExpired()).isFalse();
        }

        @Test
        void tokenHasPositiveTimeToLive() {
            var token = provider.generateToken(testUserId, TemporaryTokenPurpose.PASSWORD_RESET);

            assertThat(token.timeToLive().toSeconds()).isPositive();
        }
    }

    @Nested
    class TokenRoundTrip {

        @Test
        void canValidateGeneratedToken() {
            var generatedToken =
                    provider.generateToken(testUserId, TemporaryTokenPurpose.PASSWORD_RESET);

            var validatedToken = provider.validateToken(
                    generatedToken.value(), TemporaryTokenPurpose.PASSWORD_RESET);

            assertThat(validatedToken.value()).isEqualTo(generatedToken.value());
            assertThat(validatedToken.userId()).isEqualTo(generatedToken.userId());
            assertThat(validatedToken.purpose()).isEqualTo(generatedToken.purpose());
            assertThat(validatedToken.expiresAt().getEpochSecond())
                    .isEqualTo(generatedToken.expiresAt().getEpochSecond());
        }

        @Test
        void multipleRoundTripsProduceSameResult() {
            var generatedToken =
                    provider.generateToken(testUserId, TemporaryTokenPurpose.PASSWORD_RESET);

            var validated1 = provider.validateToken(
                    generatedToken.value(), TemporaryTokenPurpose.PASSWORD_RESET);
            var validated2 = provider.validateToken(
                    generatedToken.value(), TemporaryTokenPurpose.PASSWORD_RESET);

            assertThat(validated1.userId()).isEqualTo(validated2.userId());
            assertThat(validated1.purpose()).isEqualTo(validated2.purpose());
            assertThat(validated1.expiresAt().getEpochSecond())
                    .isEqualTo(validated2.expiresAt().getEpochSecond());
        }
    }

    @Nested
    class DifferentSecrets {

        @Test
        void tokenGeneratedWithDifferentSecretCannotBeValidated() {
            var generatedToken =
                    provider.generateToken(testUserId, TemporaryTokenPurpose.PASSWORD_RESET);

            String differentSecret =
                    "different-secret-key-must-be-at-least-512-bits-long-for-HS512-algorithm-so-this-is-another-long-secret";
            var differentProvider = new JwtTemporaryTokenProvider(differentSecret);

            assertThatThrownBy(() -> differentProvider.validateToken(
                            generatedToken.value(), TemporaryTokenPurpose.PASSWORD_RESET))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid temporary token");
        }
    }
}
