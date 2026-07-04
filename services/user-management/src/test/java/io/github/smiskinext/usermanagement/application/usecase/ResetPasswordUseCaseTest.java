package io.github.smiskinext.usermanagement.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.phunguy65.zms.shared.domain.Result;
import io.github.phunguy65.zms.shared.domain.valueobject.Email;
import io.github.phunguy65.zms.shared.domain.valueobject.UserId;
import io.github.smiskinext.usermanagement.application.command.ResetPasswordCommand;
import io.github.smiskinext.usermanagement.domain.AuthError;
import io.github.smiskinext.usermanagement.domain.model.PasswordResetToken;
import io.github.smiskinext.usermanagement.domain.model.User;
import io.github.smiskinext.usermanagement.domain.model.valueobject.FullName;
import io.github.smiskinext.usermanagement.domain.model.valueobject.HashedPassword;
import io.github.smiskinext.usermanagement.domain.model.valueobject.PasswordResetTokenId;
import io.github.smiskinext.usermanagement.domain.port.*;
import io.github.smiskinext.usermanagement.domain.port.PasswordHasher;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import io.github.smiskinext.usermanagement.domain.model.valueobject.TemporaryToken;
import io.github.smiskinext.usermanagement.domain.model.valueobject.TemporaryTokenPurpose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ResetPasswordUseCaseTest {

    @Mock
    UserRepository userRepository;

    @Mock
    PasswordResetTokenRepository tokenRepository;

    @Mock
    RefreshTokenRepository refreshTokenRepository;

    @Mock
    PasswordHasher passwordHasher;

    @Mock
    OtpHasher otpHasher;

    @Mock
    TemporaryTokenProvider
            temporaryTokenProvider;

    ResetPasswordUseCase useCase;

    private User testUser;
    private User googleOnlyUser;
    private PasswordResetToken validToken;

    @BeforeEach
    void setUp() {
        useCase = new ResetPasswordUseCase(
                userRepository,
                tokenRepository,
                refreshTokenRepository,
                passwordHasher,
                otpHasher,
                temporaryTokenProvider);

        testUser = User.reconstitute(
                UserId.of(UuidCreator.getTimeOrderedEpoch()),
                Email.of("alice@example.com"),
                HashedPassword.of("$argon2id$oldhash"),
                FullName.of("Alice Smith"),
                null,
                null,
                null,
                "EMAIL",
                null,
                Instant.now(),
                Instant.now(),
                null);

        googleOnlyUser = User.reconstitute(
                UserId.of(UuidCreator.getTimeOrderedEpoch()),
                Email.of("google@example.com"),
                null, // no password
                FullName.of("Google User"),
                null,
                null,
                "google-uid-abc",
                "GOOGLE",
                null,
                Instant.now(),
                Instant.now(),
                null);

        validToken = PasswordResetToken.reconstitute(
                PasswordResetTokenId.of(UUID.randomUUID()),
                testUser.getId(),
                "sha256hash",
                Instant.now().plusSeconds(600),
                null,
                0,
                null,
                Instant.now().minusSeconds(60));
    }

    @Nested
    class WhenValidOtpAndPassword {

        @BeforeEach
        void setUp() {
            when(userRepository.findActiveByEmail(Email.of("alice@example.com")))
                    .thenReturn(Optional.of(testUser));
            when(tokenRepository.findValidByUserId(testUser.getId()))
                    .thenReturn(Optional.of(validToken));
            when(otpHasher.verify("123456", "sha256hash")).thenReturn(true);
            when(passwordHasher.hash("NewPassword123"))
                    .thenReturn(HashedPassword.of("$argon2id$newhash"));
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        }

        @Test
        void returnsSuccess() {
            var result = useCase.execute(new ResetPasswordCommand(
                    "alice@example.com", "123456", null, "NewPassword123"));

            assertThat(result).isInstanceOf(Result.Success.class);
        }

        @Test
        void hashesNewPassword() {
            useCase.execute(new ResetPasswordCommand(
                    "alice@example.com", "123456", null, "NewPassword123"));

            verify(passwordHasher).hash("NewPassword123");
        }

        @Test
        void updatesUserPassword() {
            useCase.execute(new ResetPasswordCommand(
                    "alice@example.com", "123456", null, "NewPassword123"));

            verify(userRepository).save(argThat((User u) -> u.getHashedPassword()
                    .orElseThrow()
                    .value()
                    .equals("$argon2id$newhash")));
        }

        @Test
        void revokesAllRefreshTokensForSecurity() {
            useCase.execute(new ResetPasswordCommand(
                    "alice@example.com", "123456", null, "NewPassword123"));

            verify(refreshTokenRepository).revokeAllByUserId(testUser.getId());
        }

        @Test
        void marksTokenAsUsed() {
            useCase.execute(new ResetPasswordCommand(
                    "alice@example.com", "123456", null, "NewPassword123"));

            verify(tokenRepository).save(argThat((PasswordResetToken t) -> t.isUsed()));
        }

        @Test
        void performsOperationsInCorrectOrder() {
            useCase.execute(new ResetPasswordCommand(
                    "alice@example.com", "123456", null, "NewPassword123"));

            InOrder inOrder = inOrder(userRepository, refreshTokenRepository, tokenRepository);
            inOrder.verify(userRepository).save(any());
            inOrder.verify(refreshTokenRepository).revokeAllByUserId(any());
            inOrder.verify(tokenRepository).save(any());
        }
    }

    @Nested
    class WhenInvalidOtp {

        @BeforeEach
        void setUp() {
            when(userRepository.findActiveByEmail(Email.of("alice@example.com")))
                    .thenReturn(Optional.of(testUser));
            when(tokenRepository.findValidByUserId(testUser.getId()))
                    .thenReturn(Optional.of(validToken));
            when(otpHasher.verify("wrongotp", "sha256hash")).thenReturn(false);
            when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        }

        @Test
        void returnsOtpInvalidError() {
            var result = useCase.execute(new ResetPasswordCommand(
                    "alice@example.com", "wrongotp", null, "NewPassword123"));

            assertThat(result).isInstanceOf(Result.Failure.class);
            assertThat(((Result.Failure<?, AuthError>) result).error())
                    .isInstanceOf(AuthError.OtpInvalid.class);
        }

        @Test
        void incrementsAttemptCounter() {
            useCase.execute(new ResetPasswordCommand(
                    "alice@example.com", "wrongotp", null, "NewPassword123"));

            verify(tokenRepository).save(argThat((PasswordResetToken t) -> t.getAttempts() == 1));
        }

        @Test
        void doesNotUpdatePassword() {
            useCase.execute(new ResetPasswordCommand(
                    "alice@example.com", "wrongotp", null, "NewPassword123"));

            verify(userRepository, never()).save(any());
            verify(passwordHasher, never()).hash(any());
        }

        @Test
        void doesNotRevokeRefreshTokens() {
            useCase.execute(new ResetPasswordCommand(
                    "alice@example.com", "wrongotp", null, "NewPassword123"));

            verify(refreshTokenRepository, never()).revokeAllByUserId(any());
        }
    }

    @Nested
    class WhenOtpLockedAfter5Attempts {

        @BeforeEach
        void setUp() {
            PasswordResetToken almostLockedToken = PasswordResetToken.reconstitute(
                    PasswordResetTokenId.of(UUID.randomUUID()),
                    testUser.getId(),
                    "sha256hash",
                    Instant.now().plusSeconds(600),
                    null,
                    4,
                    null,
                    Instant.now().minusSeconds(60));

            when(userRepository.findActiveByEmail(Email.of("alice@example.com")))
                    .thenReturn(Optional.of(testUser));
            when(tokenRepository.findValidByUserId(testUser.getId()))
                    .thenReturn(Optional.of(almostLockedToken));
            when(otpHasher.verify("wrongotp", "sha256hash")).thenReturn(false);
            when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        }

        @Test
        void returnsOtpLockedErrorOnFifthAttempt() {
            var result = useCase.execute(new ResetPasswordCommand(
                    "alice@example.com", "wrongotp", null, "NewPassword123"));

            assertThat(result).isInstanceOf(Result.Failure.class);
            assertThat(((Result.Failure<?, AuthError>) result).error())
                    .isInstanceOf(AuthError.OtpLocked.class);
        }

        @Test
        void savesTokenWithFiveAttempts() {
            useCase.execute(new ResetPasswordCommand(
                    "alice@example.com", "wrongotp", null, "NewPassword123"));

            verify(tokenRepository)
                    .save(argThat((PasswordResetToken t) ->
                            t.getAttempts() == PasswordResetToken.MAX_ATTEMPTS && t.isLocked()));
        }
    }

    @Nested
    class WhenOtpExpired {

        @Test
        void returnsOtpExpiredError() {
            PasswordResetToken expiredToken = PasswordResetToken.reconstitute(
                    PasswordResetTokenId.of(UUID.randomUUID()),
                    testUser.getId(),
                    "sha256hash",
                    Instant.now().minusSeconds(60),
                    null,
                    0,
                    null,
                    Instant.now().minusSeconds(900));

            when(userRepository.findActiveByEmail(Email.of("alice@example.com")))
                    .thenReturn(Optional.of(testUser));
            when(tokenRepository.findValidByUserId(testUser.getId()))
                    .thenReturn(Optional.of(expiredToken));

            var result = useCase.execute(new ResetPasswordCommand(
                    "alice@example.com", "123456", null, "NewPassword123"));

            assertThat(result).isInstanceOf(Result.Failure.class);
            assertThat(((Result.Failure<?, AuthError>) result).error())
                    .isInstanceOf(AuthError.OtpExpired.class);
        }

        @Test
        void doesNotIncrementAttempts() {
            PasswordResetToken expiredToken = PasswordResetToken.reconstitute(
                    PasswordResetTokenId.of(UUID.randomUUID()),
                    testUser.getId(),
                    "sha256hash",
                    Instant.now().minusSeconds(60),
                    null,
                    0,
                    null,
                    Instant.now().minusSeconds(900));

            when(userRepository.findActiveByEmail(Email.of("alice@example.com")))
                    .thenReturn(Optional.of(testUser));
            when(tokenRepository.findValidByUserId(testUser.getId()))
                    .thenReturn(Optional.of(expiredToken));

            useCase.execute(new ResetPasswordCommand(
                    "alice@example.com", "123456", null, "NewPassword123"));

            verify(tokenRepository, never()).save(any());
        }
    }

    @Nested
    class WhenOtpAlreadyUsed {

        @Test
        void returnsOtpAlreadyUsedError() {
            PasswordResetToken usedToken = PasswordResetToken.reconstitute(
                    PasswordResetTokenId.of(UUID.randomUUID()),
                    testUser.getId(),
                    "sha256hash",
                    Instant.now().plusSeconds(600),
                    Instant.now().minusSeconds(30),
                    0,
                    null,
                    Instant.now().minusSeconds(300));

            when(userRepository.findActiveByEmail(Email.of("alice@example.com")))
                    .thenReturn(Optional.of(testUser));
            when(tokenRepository.findValidByUserId(testUser.getId()))
                    .thenReturn(Optional.of(usedToken));

            var result = useCase.execute(new ResetPasswordCommand(
                    "alice@example.com", "123456", null, "NewPassword123"));

            assertThat(result).isInstanceOf(Result.Failure.class);
            assertThat(((Result.Failure<?, AuthError>) result).error())
                    .isInstanceOf(AuthError.OtpAlreadyUsed.class);
        }
    }

    @Nested
    class WhenTokenAlreadyLocked {

        @Test
        void returnsOtpLockedError() {
            PasswordResetToken lockedToken = PasswordResetToken.reconstitute(
                    PasswordResetTokenId.of(UUID.randomUUID()),
                    testUser.getId(),
                    "sha256hash",
                    Instant.now().plusSeconds(600),
                    null,
                    PasswordResetToken.MAX_ATTEMPTS,
                    null,
                    Instant.now().minusSeconds(60));

            when(userRepository.findActiveByEmail(Email.of("alice@example.com")))
                    .thenReturn(Optional.of(testUser));
            when(tokenRepository.findValidByUserId(testUser.getId()))
                    .thenReturn(Optional.of(lockedToken));

            var result = useCase.execute(new ResetPasswordCommand(
                    "alice@example.com", "123456", null, "NewPassword123"));

            assertThat(result).isInstanceOf(Result.Failure.class);
            assertThat(((Result.Failure<?, AuthError>) result).error())
                    .isInstanceOf(AuthError.OtpLocked.class);
        }
    }

    @Nested
    class WhenUserNotFound {

        @Test
        void returnsOtpInvalidErrorToPreventEnumeration() {
            when(userRepository.findActiveByEmail(any())).thenReturn(Optional.empty());

            var result = useCase.execute(new ResetPasswordCommand(
                    "nobody@example.com", "123456", null, "NewPassword123"));

            assertThat(result).isInstanceOf(Result.Failure.class);
            assertThat(((Result.Failure<?, AuthError>) result).error())
                    .isInstanceOf(AuthError.OtpInvalid.class);
        }
    }

    @Nested
    class WhenGoogleOnlyAccount {

        @Test
        void returnsGoogleOnlyAccountError() {
            when(userRepository.findActiveByEmail(Email.of("google@example.com")))
                    .thenReturn(Optional.of(googleOnlyUser));

            var result = useCase.execute(new ResetPasswordCommand(
                    "google@example.com", "123456", null, "NewPassword123"));

            assertThat(result).isInstanceOf(Result.Failure.class);
            assertThat(((Result.Failure<?, AuthError>) result).error())
                    .isInstanceOf(AuthError.GoogleOnlyAccount.class);
        }

        @Test
        void doesNotLookUpToken() {
            when(userRepository.findActiveByEmail(Email.of("google@example.com")))
                    .thenReturn(Optional.of(googleOnlyUser));

            useCase.execute(new ResetPasswordCommand(
                    "google@example.com", "123456", null, "NewPassword123"));

            verify(tokenRepository, never()).findValidByUserId(any());
        }
    }

    @Nested
    class WhenNoValidTokenFound {

        @Test
        void returnsOtpInvalidError() {
            when(userRepository.findActiveByEmail(Email.of("alice@example.com")))
                    .thenReturn(Optional.of(testUser));
            when(tokenRepository.findValidByUserId(testUser.getId())).thenReturn(Optional.empty());

            var result = useCase.execute(new ResetPasswordCommand(
                    "alice@example.com", "123456", null, "NewPassword123"));

            assertThat(result).isInstanceOf(Result.Failure.class);
            assertThat(((Result.Failure<?, AuthError>) result).error())
                    .isInstanceOf(AuthError.OtpInvalid.class);
        }
    }

    @Nested
    class WhenUsingTemporaryToken {

        @BeforeEach
        void setUp() {
            lenient()
                    .when(userRepository.findActiveByEmail(Email.of("alice@example.com")))
                    .thenReturn(Optional.of(testUser));
            lenient()
                    .when(passwordHasher.hash("NewPassword123"))
                    .thenReturn(HashedPassword.of("$argon2id$newhash"));
            lenient().when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        }

        @Test
        void returnsSuccessWithValidToken() {
            when(temporaryTokenProvider.validateToken(
                            "valid-temp-token",
                            TemporaryTokenPurpose.PASSWORD_RESET))
                    .thenReturn(
                            new TemporaryToken(
                                    "valid-temp-token",
                                    testUser.getId(),
                                    TemporaryTokenPurpose.PASSWORD_RESET,
                                    Instant.now().plusSeconds(300)));

            var result = useCase.execute(new ResetPasswordCommand(
                    "alice@example.com", null, "valid-temp-token", "NewPassword123"));

            assertThat(result).isInstanceOf(Result.Success.class);
        }

        @Test
        void updatesPasswordWithValidToken() {
            when(temporaryTokenProvider.validateToken(any(), any()))
                    .thenReturn(
                            new TemporaryToken(
                                    "valid-temp-token",
                                    testUser.getId(),
                                    TemporaryTokenPurpose.PASSWORD_RESET,
                                    Instant.now().plusSeconds(300)));

            useCase.execute(new ResetPasswordCommand(
                    "alice@example.com", null, "valid-temp-token", "NewPassword123"));

            verify(userRepository).save(argThat((User u) -> u.getHashedPassword()
                    .orElseThrow()
                    .value()
                    .equals("$argon2id$newhash")));
        }

        @Test
        void revokesRefreshTokensWithValidToken() {
            when(temporaryTokenProvider.validateToken(any(), any()))
                    .thenReturn(
                            new TemporaryToken(
                                    "valid-temp-token",
                                    testUser.getId(),
                                    TemporaryTokenPurpose.PASSWORD_RESET,
                                    Instant.now().plusSeconds(300)));

            useCase.execute(new ResetPasswordCommand(
                    "alice@example.com", null, "valid-temp-token", "NewPassword123"));

            verify(refreshTokenRepository).revokeAllByUserId(testUser.getId());
        }

        @Test
        void doesNotMarkOtpTokenAsUsed() {
            when(temporaryTokenProvider.validateToken(any(), any()))
                    .thenReturn(
                            new TemporaryToken(
                                    "valid-temp-token",
                                    testUser.getId(),
                                    TemporaryTokenPurpose.PASSWORD_RESET,
                                    Instant.now().plusSeconds(300)));

            useCase.execute(new ResetPasswordCommand(
                    "alice@example.com", null, "valid-temp-token", "NewPassword123"));

            verify(tokenRepository, never()).save(any());
        }

        @Test
        void returnsTokenExpiredErrorWhenExpired() {
            when(temporaryTokenProvider.validateToken(any(), any()))
                    .thenReturn(
                            new TemporaryToken(
                                    "expired-token",
                                    testUser.getId(),
                                    TemporaryTokenPurpose.PASSWORD_RESET,
                                    Instant.now().minusSeconds(60)));

            var result = useCase.execute(new ResetPasswordCommand(
                    "alice@example.com", null, "expired-token", "NewPassword123"));

            assertThat(result).isInstanceOf(Result.Failure.class);
            assertThat(((Result.Failure<?, AuthError>) result).error())
                    .isInstanceOf(AuthError.TokenExpired.class);
        }

        @Test
        void returnsTokenInvalidErrorWhenInvalidSignature() {
            when(temporaryTokenProvider.validateToken(any(), any()))
                    .thenThrow(new IllegalArgumentException("Invalid token"));

            var result = useCase.execute(new ResetPasswordCommand(
                    "alice@example.com", null, "invalid-token", "NewPassword123"));

            assertThat(result).isInstanceOf(Result.Failure.class);
            assertThat(((Result.Failure<?, AuthError>) result).error())
                    .isInstanceOf(AuthError.TokenInvalid.class);
        }

        @Test
        void returnsTokenInvalidErrorWhenUserIdMismatch() {
            UserId differentUserId = UserId.of(UuidCreator.getTimeOrderedEpoch());
            when(temporaryTokenProvider.validateToken(any(), any()))
                    .thenReturn(
                            new TemporaryToken(
                                    "token-for-different-user",
                                    differentUserId,
                                    TemporaryTokenPurpose.PASSWORD_RESET,
                                    Instant.now().plusSeconds(300)));

            var result = useCase.execute(new ResetPasswordCommand(
                    "alice@example.com", null, "token-for-different-user", "NewPassword123"));

            assertThat(result).isInstanceOf(Result.Failure.class);
            assertThat(((Result.Failure<?, AuthError>) result).error())
                    .isInstanceOf(AuthError.TokenInvalid.class);
        }
    }

    @Nested
    class WhenProgressiveDelaysWithOtp {

        @Test
        void appliesNoDelayOnFirstAttempt() {
            PasswordResetToken tokenWithNoAttempts = PasswordResetToken.reconstitute(
                    PasswordResetTokenId.of(UUID.randomUUID()),
                    testUser.getId(),
                    "sha256hash",
                    Instant.now().plusSeconds(1200),
                    null,
                    0,
                    null,
                    Instant.now().minusSeconds(60));

            when(userRepository.findActiveByEmail(Email.of("alice@example.com")))
                    .thenReturn(Optional.of(testUser));
            when(tokenRepository.findValidByUserId(testUser.getId()))
                    .thenReturn(Optional.of(tokenWithNoAttempts));
            when(otpHasher.verify("123456", "sha256hash")).thenReturn(true);
            when(passwordHasher.hash(any())).thenReturn(HashedPassword.of("$hash"));
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            var result = useCase.execute(new ResetPasswordCommand(
                    "alice@example.com", "123456", null, "NewPassword123"));

            assertThat(result).isInstanceOf(Result.Success.class);
        }

        @Test
        void applies1SecondDelayAfter1FailedAttempt() {
            PasswordResetToken tokenWith1Attempt = PasswordResetToken.reconstitute(
                    PasswordResetTokenId.of(UUID.randomUUID()),
                    testUser.getId(),
                    "sha256hash",
                    Instant.now().plusSeconds(1200),
                    null,
                    1,
                    Instant.now().minusMillis(100),
                    Instant.now().minusSeconds(60));

            when(userRepository.findActiveByEmail(Email.of("alice@example.com")))
                    .thenReturn(Optional.of(testUser));
            when(tokenRepository.findValidByUserId(testUser.getId()))
                    .thenReturn(Optional.of(tokenWith1Attempt));

            var result = useCase.execute(new ResetPasswordCommand(
                    "alice@example.com", "123456", null, "NewPassword123"));

            assertThat(result).isInstanceOf(Result.Failure.class);
            var error = ((Result.Failure<?, AuthError>) result).error();
            assertThat(error).isInstanceOf(AuthError.ResendTooSoon.class);
            assertThat(((AuthError.ResendTooSoon) error).retryAfterSeconds()).isEqualTo(1);
        }

        @Test
        void applies2SecondDelayAfter2FailedAttempts() {
            PasswordResetToken tokenWith2Attempts = PasswordResetToken.reconstitute(
                    PasswordResetTokenId.of(UUID.randomUUID()),
                    testUser.getId(),
                    "sha256hash",
                    Instant.now().plusSeconds(1200),
                    null,
                    2,
                    Instant.now().minusMillis(100),
                    Instant.now().minusSeconds(60));

            when(userRepository.findActiveByEmail(Email.of("alice@example.com")))
                    .thenReturn(Optional.of(testUser));
            when(tokenRepository.findValidByUserId(testUser.getId()))
                    .thenReturn(Optional.of(tokenWith2Attempts));

            var result = useCase.execute(new ResetPasswordCommand(
                    "alice@example.com", "123456", null, "NewPassword123"));

            assertThat(result).isInstanceOf(Result.Failure.class);
            var error = ((Result.Failure<?, AuthError>) result).error();
            assertThat(error).isInstanceOf(AuthError.ResendTooSoon.class);
            assertThat(((AuthError.ResendTooSoon) error).retryAfterSeconds()).isBetween(1L, 2L);
        }

        @Test
        void applies4SecondDelayAfter3FailedAttempts() {
            PasswordResetToken tokenWith3Attempts = PasswordResetToken.reconstitute(
                    PasswordResetTokenId.of(UUID.randomUUID()),
                    testUser.getId(),
                    "sha256hash",
                    Instant.now().plusSeconds(1200),
                    null,
                    3,
                    Instant.now().minusMillis(100),
                    Instant.now().minusSeconds(60));

            when(userRepository.findActiveByEmail(Email.of("alice@example.com")))
                    .thenReturn(Optional.of(testUser));
            when(tokenRepository.findValidByUserId(testUser.getId()))
                    .thenReturn(Optional.of(tokenWith3Attempts));

            var result = useCase.execute(new ResetPasswordCommand(
                    "alice@example.com", "123456", null, "NewPassword123"));

            assertThat(result).isInstanceOf(Result.Failure.class);
            var error = ((Result.Failure<?, AuthError>) result).error();
            assertThat(error).isInstanceOf(AuthError.ResendTooSoon.class);
            assertThat(((AuthError.ResendTooSoon) error).retryAfterSeconds()).isBetween(3L, 4L);
        }

        @Test
        void applies8SecondDelayAfter4FailedAttempts() {
            PasswordResetToken tokenWith4Attempts = PasswordResetToken.reconstitute(
                    PasswordResetTokenId.of(UUID.randomUUID()),
                    testUser.getId(),
                    "sha256hash",
                    Instant.now().plusSeconds(1200),
                    null,
                    4,
                    Instant.now().minusMillis(100),
                    Instant.now().minusSeconds(60));

            when(userRepository.findActiveByEmail(Email.of("alice@example.com")))
                    .thenReturn(Optional.of(testUser));
            when(tokenRepository.findValidByUserId(testUser.getId()))
                    .thenReturn(Optional.of(tokenWith4Attempts));

            var result = useCase.execute(new ResetPasswordCommand(
                    "alice@example.com", "123456", null, "NewPassword123"));

            assertThat(result).isInstanceOf(Result.Failure.class);
            var error = ((Result.Failure<?, AuthError>) result).error();
            assertThat(error).isInstanceOf(AuthError.ResendTooSoon.class);
            assertThat(((AuthError.ResendTooSoon) error).retryAfterSeconds()).isBetween(7L, 8L);
        }

        @Test
        void returnsOtpLockedAfter5OrMoreFailedAttempts() {
            PasswordResetToken tokenWith5Attempts = PasswordResetToken.reconstitute(
                    PasswordResetTokenId.of(UUID.randomUUID()),
                    testUser.getId(),
                    "sha256hash",
                    Instant.now().plusSeconds(1200),
                    null,
                    5,
                    Instant.now().minusMillis(100),
                    Instant.now().minusSeconds(60));

            when(userRepository.findActiveByEmail(Email.of("alice@example.com")))
                    .thenReturn(Optional.of(testUser));
            when(tokenRepository.findValidByUserId(testUser.getId()))
                    .thenReturn(Optional.of(tokenWith5Attempts));

            var result = useCase.execute(new ResetPasswordCommand(
                    "alice@example.com", "123456", null, "NewPassword123"));

            assertThat(result).isInstanceOf(Result.Failure.class);
            var error = ((Result.Failure<?, AuthError>) result).error();
            assertThat(error).isInstanceOf(AuthError.OtpLocked.class);
        }

        @Test
        void allowsAttemptAfterDelayHasElapsed() {
            PasswordResetToken tokenWith1AttemptOld = PasswordResetToken.reconstitute(
                    PasswordResetTokenId.of(UUID.randomUUID()),
                    testUser.getId(),
                    "sha256hash",
                    Instant.now().plusSeconds(1200),
                    null,
                    1,
                    Instant.now().minusSeconds(2),
                    Instant.now().minusSeconds(60));

            when(userRepository.findActiveByEmail(Email.of("alice@example.com")))
                    .thenReturn(Optional.of(testUser));
            when(tokenRepository.findValidByUserId(testUser.getId()))
                    .thenReturn(Optional.of(tokenWith1AttemptOld));
            when(otpHasher.verify("123456", "sha256hash")).thenReturn(true);
            when(passwordHasher.hash(any())).thenReturn(HashedPassword.of("$hash"));
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            var result = useCase.execute(new ResetPasswordCommand(
                    "alice@example.com", "123456", null, "NewPassword123"));

            assertThat(result).isInstanceOf(Result.Success.class);
        }
    }

    @Nested
    class EmailNormalization {

        @Test
        void normalizesEmailToLowercase() {
            when(userRepository.findActiveByEmail(Email.of("alice@example.com")))
                    .thenReturn(Optional.of(testUser));
            when(tokenRepository.findValidByUserId(testUser.getId()))
                    .thenReturn(Optional.of(validToken));
            when(otpHasher.verify("123456", "sha256hash")).thenReturn(true);
            when(passwordHasher.hash(any())).thenReturn(HashedPassword.of("$hash"));
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            useCase.execute(new ResetPasswordCommand(
                    "ALICE@EXAMPLE.COM", "123456", null, "NewPassword123"));

            verify(userRepository).findActiveByEmail(Email.of("alice@example.com"));
        }
    }
}
