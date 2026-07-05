package io.github.smiskinext.usermanagement.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.smiskinext.shared.domain.valueobject.UserId;
import io.github.smiskinext.usermanagement.domain.model.valueobject.PasswordResetTokenId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PasswordResetTokenTest {

    private static final UserId USER_ID = UserId.of(UUID.randomUUID());
    private static final String OTP_HASH = "sha256hashvalue";

    @Nested
    class IssueFactoryMethod {

        @Test
        void createsTokenWithZeroAttempts() {
            Instant expiresAt = Instant.now().plusSeconds(900);
            PasswordResetToken token = PasswordResetToken.issue(USER_ID, OTP_HASH, expiresAt);

            assertThat(token.getAttempts()).isZero();
            assertThat(token.getId()).isNotNull();
            assertThat(token.getUserId()).isEqualTo(USER_ID);
            assertThat(token.getOtpHash()).isEqualTo(OTP_HASH);
            assertThat(token.getExpiresAt()).isEqualTo(expiresAt);
            assertThat(token.getUsedAt()).isEmpty();
            assertThat(token.getCreatedAt()).isNotNull();
        }

        @Test
        void createsTokenWithUniqueIds() {
            Instant expiresAt = Instant.now().plusSeconds(900);
            PasswordResetToken token1 = PasswordResetToken.issue(USER_ID, OTP_HASH, expiresAt);
            PasswordResetToken token2 = PasswordResetToken.issue(USER_ID, OTP_HASH, expiresAt);

            assertThat(token1.getId()).isNotEqualTo(token2.getId());
        }
    }

    @Nested
    class IsExpired {

        @Test
        void returnsTrueWhenCurrentTimeIsAfterExpiresAt() {
            Instant pastExpiry = Instant.now().minusSeconds(60);
            PasswordResetToken token = PasswordResetToken.reconstitute(
                    PasswordResetTokenId.of(UUID.randomUUID()),
                    USER_ID,
                    OTP_HASH,
                    pastExpiry,
                    null,
                    0,
                    null,
                    Instant.now().minusSeconds(600));

            assertThat(token.isExpired()).isTrue();
        }

        @Test
        void returnsFalseWhenCurrentTimeIsBeforeExpiresAt() {
            Instant futureExpiry = Instant.now().plusSeconds(600);
            PasswordResetToken token = PasswordResetToken.reconstitute(
                    PasswordResetTokenId.of(UUID.randomUUID()),
                    USER_ID,
                    OTP_HASH,
                    futureExpiry,
                    null,
                    0,
                    null,
                    Instant.now());

            assertThat(token.isExpired()).isFalse();
        }
    }

    @Nested
    class IsUsed {

        @Test
        void returnsTrueWhenUsedAtIsNotNull() {
            PasswordResetToken token = PasswordResetToken.reconstitute(
                    PasswordResetTokenId.of(UUID.randomUUID()),
                    USER_ID,
                    OTP_HASH,
                    Instant.now().plusSeconds(600),
                    Instant.now(),
                    0,
                    null,
                    Instant.now().minusSeconds(60));

            assertThat(token.isUsed()).isTrue();
        }

        @Test
        void returnsFalseWhenUsedAtIsNull() {
            PasswordResetToken token = PasswordResetToken.reconstitute(
                    PasswordResetTokenId.of(UUID.randomUUID()),
                    USER_ID,
                    OTP_HASH,
                    Instant.now().plusSeconds(600),
                    null,
                    0,
                    null,
                    Instant.now());

            assertThat(token.isUsed()).isFalse();
        }
    }

    @Nested
    class IsLocked {

        @Test
        void returnsTrueWhenAttemptsEqualsMaxAttempts() {
            PasswordResetToken token = PasswordResetToken.reconstitute(
                    PasswordResetTokenId.of(UUID.randomUUID()),
                    USER_ID,
                    OTP_HASH,
                    Instant.now().plusSeconds(600),
                    null,
                    PasswordResetToken.MAX_ATTEMPTS,
                    null,
                    Instant.now());

            assertThat(token.isLocked()).isTrue();
        }

        @Test
        void returnsTrueWhenAttemptsExceedsMaxAttempts() {
            PasswordResetToken token = PasswordResetToken.reconstitute(
                    PasswordResetTokenId.of(UUID.randomUUID()),
                    USER_ID,
                    OTP_HASH,
                    Instant.now().plusSeconds(600),
                    null,
                    PasswordResetToken.MAX_ATTEMPTS + 1,
                    null,
                    Instant.now());

            assertThat(token.isLocked()).isTrue();
        }

        @Test
        void returnsFalseWhenAttemptsBelowMaxAttempts() {
            PasswordResetToken token = PasswordResetToken.reconstitute(
                    PasswordResetTokenId.of(UUID.randomUUID()),
                    USER_ID,
                    OTP_HASH,
                    Instant.now().plusSeconds(600),
                    null,
                    PasswordResetToken.MAX_ATTEMPTS - 1,
                    null,
                    Instant.now());

            assertThat(token.isLocked()).isFalse();
        }

        @Test
        void returnsFalseWhenAttemptsIsZero() {
            PasswordResetToken token = PasswordResetToken.reconstitute(
                    PasswordResetTokenId.of(UUID.randomUUID()),
                    USER_ID,
                    OTP_HASH,
                    Instant.now().plusSeconds(600),
                    null,
                    0,
                    null,
                    Instant.now());

            assertThat(token.isLocked()).isFalse();
        }
    }

    @Nested
    class IsValid {

        @Test
        void returnsTrueWhenNotExpiredNotUsedNotLocked() {
            PasswordResetToken token = PasswordResetToken.reconstitute(
                    PasswordResetTokenId.of(UUID.randomUUID()),
                    USER_ID,
                    OTP_HASH,
                    Instant.now().plusSeconds(600),
                    null,
                    0,
                    null,
                    Instant.now());

            assertThat(token.isValid()).isTrue();
        }

        @Test
        void returnsFalseWhenExpired() {
            PasswordResetToken token = PasswordResetToken.reconstitute(
                    PasswordResetTokenId.of(UUID.randomUUID()),
                    USER_ID,
                    OTP_HASH,
                    Instant.now().minusSeconds(60),
                    null,
                    0,
                    null,
                    Instant.now().minusSeconds(600));

            assertThat(token.isValid()).isFalse();
        }

        @Test
        void returnsFalseWhenUsed() {
            PasswordResetToken token = PasswordResetToken.reconstitute(
                    PasswordResetTokenId.of(UUID.randomUUID()),
                    USER_ID,
                    OTP_HASH,
                    Instant.now().plusSeconds(600),
                    Instant.now(),
                    0,
                    null,
                    Instant.now().minusSeconds(60));

            assertThat(token.isValid()).isFalse();
        }

        @Test
        void returnsFalseWhenLocked() {
            PasswordResetToken token = PasswordResetToken.reconstitute(
                    PasswordResetTokenId.of(UUID.randomUUID()),
                    USER_ID,
                    OTP_HASH,
                    Instant.now().plusSeconds(600),
                    null,
                    PasswordResetToken.MAX_ATTEMPTS,
                    null,
                    Instant.now());

            assertThat(token.isValid()).isFalse();
        }
    }

    @Nested
    class IncrementAttempts {

        @Test
        void incrementsAttemptsCounter() {
            PasswordResetToken token =
                    PasswordResetToken.issue(USER_ID, OTP_HASH, Instant.now().plusSeconds(600));

            assertThat(token.getAttempts()).isZero();

            token.incrementAttempts();
            assertThat(token.getAttempts()).isEqualTo(1);

            token.incrementAttempts();
            assertThat(token.getAttempts()).isEqualTo(2);
        }

        @Test
        void locksTokenAfterMaxAttempts() {
            PasswordResetToken token = PasswordResetToken.reconstitute(
                    PasswordResetTokenId.of(UUID.randomUUID()),
                    USER_ID,
                    OTP_HASH,
                    Instant.now().plusSeconds(600),
                    null,
                    PasswordResetToken.MAX_ATTEMPTS - 1,
                    null,
                    Instant.now());

            assertThat(token.isLocked()).isFalse();

            token.incrementAttempts(); // 5th attempt
            assertThat(token.isLocked()).isTrue();
        }
    }

    @Nested
    class MarkUsed {

        @Test
        void setsUsedAtToCurrentInstant() {
            PasswordResetToken token =
                    PasswordResetToken.issue(USER_ID, OTP_HASH, Instant.now().plusSeconds(600));

            assertThat(token.getUsedAt()).isEmpty();
            assertThat(token.isUsed()).isFalse();

            token.markUsed();

            assertThat(token.getUsedAt()).isPresent();
            assertThat(token.isUsed()).isTrue();
            // Should be approximately now (within a few seconds)
            assertThat(token.getUsedAt().get())
                    .isBetween(Instant.now().minusSeconds(2), Instant.now().plusSeconds(2));
        }
    }

    @Nested
    class MaxAttemptsConstant {

        @Test
        void maxAttemptsIsFive() {
            assertThat(PasswordResetToken.MAX_ATTEMPTS).isEqualTo(5);
        }
    }
}
