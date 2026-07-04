package io.github.smiskinext.usermanagement.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.phunguy65.zms.shared.domain.Result;
import io.github.phunguy65.zms.shared.domain.valueobject.Email;
import io.github.phunguy65.zms.shared.domain.valueobject.UserId;
import io.github.smiskinext.usermanagement.application.command.VerifyOtpCommand;
import io.github.smiskinext.usermanagement.application.response.VerifyOtpResponse;
import io.github.smiskinext.usermanagement.domain.AuthError;
import io.github.smiskinext.usermanagement.domain.model.PasswordResetToken;
import io.github.smiskinext.usermanagement.domain.model.User;
import io.github.smiskinext.usermanagement.domain.model.valueobject.FullName;
import io.github.smiskinext.usermanagement.domain.model.valueobject.HashedPassword;
import io.github.smiskinext.usermanagement.domain.model.valueobject.TemporaryToken;
import io.github.smiskinext.usermanagement.domain.model.valueobject.TemporaryTokenPurpose;
import io.github.smiskinext.usermanagement.domain.port.OtpHasher;
import io.github.smiskinext.usermanagement.domain.port.PasswordResetTokenRepository;
import io.github.smiskinext.usermanagement.domain.port.TemporaryTokenProvider;
import io.github.smiskinext.usermanagement.domain.port.UserRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VerifyOtpUseCaseTest {

    @Mock
    UserRepository userRepository;

    @Mock
    PasswordResetTokenRepository tokenRepository;

    @Mock
    OtpHasher otpHasher;

    @Mock
    TemporaryTokenProvider temporaryTokenProvider;

    VerifyOtpUseCase useCase;

    private User testUser;
    private User googleOnlyUser;
    private PasswordResetToken validToken;

    @BeforeEach
    void setUp() {
        useCase = new VerifyOtpUseCase(
                userRepository, tokenRepository, otpHasher, temporaryTokenProvider);

        testUser = User.reconstitute(
                UserId.of(UuidCreator.getTimeOrderedEpoch()),
                Email.of("alice@example.com"),
                HashedPassword.of("$argon2id$hash"),
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
                null,
                FullName.of("Google User"),
                null,
                null,
                "google-uid-abc",
                "GOOGLE",
                null,
                Instant.now(),
                Instant.now(),
                null);

        validToken = PasswordResetToken.issue(
                testUser.getId(), "sha256hash", Instant.now().plusSeconds(1200));
    }

    @Nested
    class WhenValidOtp {

        @BeforeEach
        void setUp() {
            when(userRepository.findActiveByEmail(Email.of("alice@example.com")))
                    .thenReturn(Optional.of(testUser));
            when(tokenRepository.findValidByUserId(testUser.getId()))
                    .thenReturn(Optional.of(validToken));
            when(otpHasher.verify("123456", "sha256hash")).thenReturn(true);
            when(temporaryTokenProvider.generateToken(
                            testUser.getId(), TemporaryTokenPurpose.PASSWORD_RESET))
                    .thenReturn(new TemporaryToken(
                            "temp-token-jwt",
                            testUser.getId(),
                            TemporaryTokenPurpose.PASSWORD_RESET,
                            Instant.now().plusSeconds(300)));
        }

        @Test
        void returnsSuccessWithTemporaryToken() {
            var result = useCase.execute(new VerifyOtpCommand("alice@example.com", "123456"));

            assertThat(result).isInstanceOf(Result.Success.class);
            var response = ((Result.Success<VerifyOtpResponse, ?>) result).value();
            assertThat(response.temporaryToken()).isEqualTo("temp-token-jwt");
        }

        @Test
        void generatesTemporaryTokenWithPasswordResetPurpose() {
            useCase.execute(new VerifyOtpCommand("alice@example.com", "123456"));

            verify(temporaryTokenProvider)
                    .generateToken(testUser.getId(), TemporaryTokenPurpose.PASSWORD_RESET);
        }

        @Test
        void doesNotIncrementAttemptCounter() {
            when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            useCase.execute(new VerifyOtpCommand("alice@example.com", "123456"));

            assertThat(validToken.getAttempts()).isZero();
        }

        @Test
        void marksTokenAsUsed() {
            when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            useCase.execute(new VerifyOtpCommand("alice@example.com", "123456"));

            ArgumentCaptor<PasswordResetToken> captor =
                    ArgumentCaptor.forClass(PasswordResetToken.class);
            verify(tokenRepository).save(captor.capture());

            PasswordResetToken savedToken = captor.getValue();
            assertThat(savedToken.isUsed()).isTrue();
            assertThat(savedToken.getUsedAt()).isPresent();
        }

        @Test
        void normalizesEmailToLowercase() {
            useCase.execute(new VerifyOtpCommand("ALICE@EXAMPLE.COM  ", "123456"));

            verify(userRepository).findActiveByEmail(Email.of("alice@example.com"));
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
            when(otpHasher.verify("999999", "sha256hash")).thenReturn(false);
            when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        }

        @Test
        void returnsOtpInvalidError() {
            var result = useCase.execute(new VerifyOtpCommand("alice@example.com", "999999"));

            assertThat(result).isInstanceOf(Result.Failure.class);
            assertThat(((Result.Failure<?, AuthError>) result).error())
                    .isInstanceOf(AuthError.OtpInvalid.class);
        }

        @Test
        void incrementsAttemptCounter() {
            useCase.execute(new VerifyOtpCommand("alice@example.com", "999999"));

            ArgumentCaptor<PasswordResetToken> captor =
                    ArgumentCaptor.forClass(PasswordResetToken.class);
            verify(tokenRepository).save(captor.capture());

            PasswordResetToken savedToken = captor.getValue();
            assertThat(savedToken.getAttempts()).isEqualTo(1);
            assertThat(savedToken.getLastAttemptTimestamp()).isPresent();
        }

        @Test
        void doesNotGenerateTemporaryToken() {
            useCase.execute(new VerifyOtpCommand("alice@example.com", "999999"));

            verify(temporaryTokenProvider, never()).generateToken(any(), any());
        }
    }

    @Nested
    class WhenExpiredOtp {

        @BeforeEach
        void setUp() {
            PasswordResetToken expiredToken = PasswordResetToken.issue(
                    testUser.getId(), "sha256hash", Instant.now().minusSeconds(60));

            when(userRepository.findActiveByEmail(Email.of("alice@example.com")))
                    .thenReturn(Optional.of(testUser));
            when(tokenRepository.findValidByUserId(testUser.getId()))
                    .thenReturn(Optional.of(expiredToken));
        }

        @Test
        void returnsOtpExpiredError() {
            var result = useCase.execute(new VerifyOtpCommand("alice@example.com", "123456"));

            assertThat(result).isInstanceOf(Result.Failure.class);
            assertThat(((Result.Failure<?, AuthError>) result).error())
                    .isInstanceOf(AuthError.OtpExpired.class);
        }

        @Test
        void doesNotVerifyOtpHash() {
            useCase.execute(new VerifyOtpCommand("alice@example.com", "123456"));

            verify(otpHasher, never()).verify(any(), any());
        }
    }

    @Nested
    class WhenProgressiveDelays {

        @Test
        void appliesNoDelayOnFirstAttempt() {
            PasswordResetToken tokenWithNoAttempts = PasswordResetToken.reconstitute(
                    validToken.getId(),
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
            when(temporaryTokenProvider.generateToken(any(), any()))
                    .thenReturn(new TemporaryToken(
                            "temp-token",
                            testUser.getId(),
                            TemporaryTokenPurpose.PASSWORD_RESET,
                            Instant.now().plusSeconds(300)));

            var result = useCase.execute(new VerifyOtpCommand("alice@example.com", "123456"));

            assertThat(result).isInstanceOf(Result.Success.class);
        }

        @Test
        void applies1SecondDelayAfter1FailedAttempt() {
            PasswordResetToken tokenWith1Attempt = PasswordResetToken.reconstitute(
                    validToken.getId(),
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

            var result = useCase.execute(new VerifyOtpCommand("alice@example.com", "123456"));

            assertThat(result).isInstanceOf(Result.Failure.class);
            var error = ((Result.Failure<?, AuthError>) result).error();
            assertThat(error).isInstanceOf(AuthError.ResendTooSoon.class);
            assertThat(((AuthError.ResendTooSoon) error).retryAfterSeconds()).isEqualTo(1);
        }

        @Test
        void applies2SecondDelayAfter2FailedAttempts() {
            PasswordResetToken tokenWith2Attempts = PasswordResetToken.reconstitute(
                    validToken.getId(),
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

            var result = useCase.execute(new VerifyOtpCommand("alice@example.com", "123456"));

            assertThat(result).isInstanceOf(Result.Failure.class);
            var error = ((Result.Failure<?, AuthError>) result).error();
            assertThat(error).isInstanceOf(AuthError.ResendTooSoon.class);
            assertThat(((AuthError.ResendTooSoon) error).retryAfterSeconds()).isBetween(1L, 2L);
        }

        @Test
        void applies4SecondDelayAfter3FailedAttempts() {
            PasswordResetToken tokenWith3Attempts = PasswordResetToken.reconstitute(
                    validToken.getId(),
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

            var result = useCase.execute(new VerifyOtpCommand("alice@example.com", "123456"));

            assertThat(result).isInstanceOf(Result.Failure.class);
            var error = ((Result.Failure<?, AuthError>) result).error();
            assertThat(error).isInstanceOf(AuthError.ResendTooSoon.class);
            assertThat(((AuthError.ResendTooSoon) error).retryAfterSeconds()).isBetween(3L, 4L);
        }

        @Test
        void applies8SecondDelayAfter4FailedAttempts() {
            PasswordResetToken tokenWith4Attempts = PasswordResetToken.reconstitute(
                    validToken.getId(),
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

            var result = useCase.execute(new VerifyOtpCommand("alice@example.com", "123456"));

            assertThat(result).isInstanceOf(Result.Failure.class);
            var error = ((Result.Failure<?, AuthError>) result).error();
            assertThat(error).isInstanceOf(AuthError.ResendTooSoon.class);
            assertThat(((AuthError.ResendTooSoon) error).retryAfterSeconds()).isBetween(7L, 8L);
        }

        @Test
        void returnsOtpLockedAfter5OrMoreFailedAttempts() {
            PasswordResetToken tokenWith5Attempts = PasswordResetToken.reconstitute(
                    validToken.getId(),
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

            var result = useCase.execute(new VerifyOtpCommand("alice@example.com", "123456"));

            assertThat(result).isInstanceOf(Result.Failure.class);
            var error = ((Result.Failure<?, AuthError>) result).error();
            assertThat(error).isInstanceOf(AuthError.OtpLocked.class);
        }

        @Test
        void allowsAttemptAfterDelayHasElapsed() {
            PasswordResetToken tokenWith1AttemptOld = PasswordResetToken.reconstitute(
                    validToken.getId(),
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
            when(temporaryTokenProvider.generateToken(any(), any()))
                    .thenReturn(new TemporaryToken(
                            "temp-token",
                            testUser.getId(),
                            TemporaryTokenPurpose.PASSWORD_RESET,
                            Instant.now().plusSeconds(300)));

            var result = useCase.execute(new VerifyOtpCommand("alice@example.com", "123456"));

            assertThat(result).isInstanceOf(Result.Success.class);
        }

        @Test
        void persistsAttemptCounterAfterEachFailure() {
            PasswordResetToken tokenWith2Attempts = PasswordResetToken.reconstitute(
                    validToken.getId(),
                    testUser.getId(),
                    "sha256hash",
                    Instant.now().plusSeconds(1200),
                    null,
                    2,
                    Instant.now().minusSeconds(3),
                    Instant.now().minusSeconds(60));

            when(userRepository.findActiveByEmail(Email.of("alice@example.com")))
                    .thenReturn(Optional.of(testUser));
            when(tokenRepository.findValidByUserId(testUser.getId()))
                    .thenReturn(Optional.of(tokenWith2Attempts));
            when(otpHasher.verify("999999", "sha256hash")).thenReturn(false);
            when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            useCase.execute(new VerifyOtpCommand("alice@example.com", "999999"));

            ArgumentCaptor<PasswordResetToken> captor =
                    ArgumentCaptor.forClass(PasswordResetToken.class);
            verify(tokenRepository).save(captor.capture());

            PasswordResetToken savedToken = captor.getValue();
            assertThat(savedToken.getAttempts()).isEqualTo(3);
            assertThat(savedToken.getLastAttemptTimestamp()).isPresent();
        }
    }

    @Nested
    class WhenTokenLocked {

        @BeforeEach
        void setUp() {
            PasswordResetToken lockedToken = PasswordResetToken.reconstitute(
                    validToken.getId(),
                    testUser.getId(),
                    "sha256hash",
                    Instant.now().plusSeconds(1200),
                    null,
                    5,
                    Instant.now().minusSeconds(60),
                    Instant.now().minusSeconds(120));

            when(userRepository.findActiveByEmail(Email.of("alice@example.com")))
                    .thenReturn(Optional.of(testUser));
            when(tokenRepository.findValidByUserId(testUser.getId()))
                    .thenReturn(Optional.of(lockedToken));
        }

        @Test
        void returnsOtpLockedError() {
            var result = useCase.execute(new VerifyOtpCommand("alice@example.com", "123456"));

            assertThat(result).isInstanceOf(Result.Failure.class);
            assertThat(((Result.Failure<?, AuthError>) result).error())
                    .isInstanceOf(AuthError.OtpLocked.class);
        }

        @Test
        void doesNotVerifyOtpHash() {
            useCase.execute(new VerifyOtpCommand("alice@example.com", "123456"));

            verify(otpHasher, never()).verify(any(), any());
        }
    }

    @Nested
    class WhenTokenAlreadyUsed {

        @BeforeEach
        void setUp() {
            PasswordResetToken usedToken = PasswordResetToken.reconstitute(
                    validToken.getId(),
                    testUser.getId(),
                    "sha256hash",
                    Instant.now().plusSeconds(1200),
                    Instant.now().minusSeconds(30),
                    0,
                    null,
                    Instant.now().minusSeconds(120));

            when(userRepository.findActiveByEmail(Email.of("alice@example.com")))
                    .thenReturn(Optional.of(testUser));
            when(tokenRepository.findValidByUserId(testUser.getId()))
                    .thenReturn(Optional.of(usedToken));
        }

        @Test
        void returnsOtpAlreadyUsedError() {
            var result = useCase.execute(new VerifyOtpCommand("alice@example.com", "123456"));

            assertThat(result).isInstanceOf(Result.Failure.class);
            assertThat(((Result.Failure<?, AuthError>) result).error())
                    .isInstanceOf(AuthError.OtpAlreadyUsed.class);
        }
    }

    @Nested
    class WhenUserNotFound {

        @BeforeEach
        void setUp() {
            when(userRepository.findActiveByEmail(any())).thenReturn(Optional.empty());
        }

        @Test
        void returnsOtpInvalidErrorToPreventEnumeration() {
            var result = useCase.execute(new VerifyOtpCommand("nobody@example.com", "123456"));

            assertThat(result).isInstanceOf(Result.Failure.class);
            assertThat(((Result.Failure<?, AuthError>) result).error())
                    .isInstanceOf(AuthError.OtpInvalid.class);
        }

        @Test
        void doesNotQueryTokenRepository() {
            useCase.execute(new VerifyOtpCommand("nobody@example.com", "123456"));

            verify(tokenRepository, never()).findValidByUserId(any());
        }
    }

    @Nested
    class WhenGoogleOnlyAccount {

        @BeforeEach
        void setUp() {
            when(userRepository.findActiveByEmail(Email.of("google@example.com")))
                    .thenReturn(Optional.of(googleOnlyUser));
        }

        @Test
        void returnsGoogleOnlyAccountError() {
            var result = useCase.execute(new VerifyOtpCommand("google@example.com", "123456"));

            assertThat(result).isInstanceOf(Result.Failure.class);
            assertThat(((Result.Failure<?, AuthError>) result).error())
                    .isInstanceOf(AuthError.GoogleOnlyAccount.class);
        }
    }

    @Nested
    class WhenNoTokenExists {

        @BeforeEach
        void setUp() {
            when(userRepository.findActiveByEmail(Email.of("alice@example.com")))
                    .thenReturn(Optional.of(testUser));
            when(tokenRepository.findValidByUserId(testUser.getId())).thenReturn(Optional.empty());
        }

        @Test
        void returnsOtpInvalidError() {
            var result = useCase.execute(new VerifyOtpCommand("alice@example.com", "123456"));

            assertThat(result).isInstanceOf(Result.Failure.class);
            assertThat(((Result.Failure<?, AuthError>) result).error())
                    .isInstanceOf(AuthError.OtpInvalid.class);
        }
    }
}
