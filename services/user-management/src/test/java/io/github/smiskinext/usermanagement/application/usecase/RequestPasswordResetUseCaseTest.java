package io.github.smiskinext.usermanagement.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.smiskinext.shared.domain.Result;
import io.github.smiskinext.shared.domain.valueobject.Email;
import io.github.smiskinext.shared.domain.valueobject.UserId;
import io.github.smiskinext.usermanagement.application.command.RequestPasswordResetCommand;
import io.github.smiskinext.usermanagement.domain.AuthError;
import io.github.smiskinext.usermanagement.domain.event.PasswordResetRequestedEvent;
import io.github.smiskinext.usermanagement.domain.model.PasswordResetToken;
import io.github.smiskinext.usermanagement.domain.model.User;
import io.github.smiskinext.usermanagement.domain.model.valueobject.FullName;
import io.github.smiskinext.usermanagement.domain.model.valueobject.HashedPassword;
import io.github.smiskinext.usermanagement.domain.port.OtpGenerator;
import io.github.smiskinext.usermanagement.domain.port.CaptchaVerifier;
import io.github.smiskinext.usermanagement.domain.port.OtpHasher;
import io.github.smiskinext.usermanagement.domain.port.PasswordResetTokenRepository;
import io.github.smiskinext.usermanagement.domain.port.UserRepository;
import java.time.Instant;
import java.util.Optional;

import io.github.smiskinext.usermanagement.domain.model.valueobject.PasswordResetTokenId;
import io.github.smiskinext.usermanagement.domain.port.PasswordResetAttemptTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class RequestPasswordResetUseCaseTest {

    @Mock
    UserRepository userRepository;

    @Mock
    PasswordResetTokenRepository tokenRepository;

    @Mock
    OtpGenerator otpGenerator;

    @Mock
    OtpHasher otpHasher;

    @Mock
    ApplicationEventPublisher eventPublisher;

    @Mock
    CaptchaVerifier captchaVerifier;

    @Mock
    PasswordResetAttemptTracker attemptTracker;

    RequestPasswordResetUseCase useCase;

    private User testUser;
    private User googleOnlyUser;

    @BeforeEach
    void setUp() {
        useCase = new RequestPasswordResetUseCase(
                userRepository,
                tokenRepository,
                otpGenerator,
                otpHasher,
                eventPublisher,
                captchaVerifier,
                attemptTracker);

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
    }

    @Nested
    class WhenValidEmailWithPasswordAccount {

        @BeforeEach
        void setUp() {
            when(attemptTracker.countAttemptsSince(any(), any())).thenReturn(0L);
            when(userRepository.findActiveByEmail(Email.of("alice@example.com")))
                    .thenReturn(Optional.of(testUser));
            when(otpGenerator.generate()).thenReturn("123456");
            when(otpHasher.hash("123456")).thenReturn("sha256hash");
            when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        }

        @Test
        void returnsSuccess() {
            var result = useCase.execute(
                    new RequestPasswordResetCommand("alice@example.com", "192.168.1.1", null));

            assertThat(result).isInstanceOf(Result.Success.class);
        }

        @Test
        void generates6DigitOtp() {
            useCase.execute(
                    new RequestPasswordResetCommand("alice@example.com", "192.168.1.1", null));

            verify(otpGenerator).generate();
        }

        @Test
        void hashesOtpWithSha256() {
            useCase.execute(
                    new RequestPasswordResetCommand("alice@example.com", "192.168.1.1", null));

            verify(otpHasher).hash("123456");
        }

        @Test
        void savesPasswordResetTokenWith20MinuteExpiry() {
            useCase.execute(
                    new RequestPasswordResetCommand("alice@example.com", "192.168.1.1", null));

            ArgumentCaptor<PasswordResetToken> captor =
                    ArgumentCaptor.forClass(PasswordResetToken.class);
            verify(tokenRepository).save(captor.capture());

            PasswordResetToken savedToken = captor.getValue();
            assertThat(savedToken.getUserId()).isEqualTo(testUser.getId());
            assertThat(savedToken.getOtpHash()).isEqualTo("sha256hash");
            assertThat(savedToken.getAttempts()).isZero();
            assertThat(savedToken.getExpiresAt())
                    .isBetween(
                            Instant.now().plusSeconds(19 * 60), Instant.now().plusSeconds(21 * 60));
        }

        @Test
        void publishesPasswordResetRequestedEvent() {
            useCase.execute(
                    new RequestPasswordResetCommand("alice@example.com", "192.168.1.1", null));

            ArgumentCaptor<PasswordResetRequestedEvent> captor =
                    ArgumentCaptor.forClass(PasswordResetRequestedEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());

            PasswordResetRequestedEvent event = captor.getValue();
            assertThat(event.userId()).isEqualTo(testUser.getId().value());
            assertThat(event.email()).isEqualTo("alice@example.com");
            assertThat(event.fullName()).isEqualTo("Alice Smith");
            assertThat(event.otp()).isEqualTo("123456"); // Plaintext OTP for email
        }

        @Test
        void invalidatesExistingTokensBeforeCreatingNew() {
            useCase.execute(
                    new RequestPasswordResetCommand("alice@example.com", "192.168.1.1", null));

            verify(tokenRepository).invalidateAllByUserId(testUser.getId());
            verify(tokenRepository).save(any(PasswordResetToken.class));
        }

        @Test
        void recordsAttemptWithEmailAndIp() {
            useCase.execute(
                    new RequestPasswordResetCommand("alice@example.com", "192.168.1.1", null));

            verify(attemptTracker).recordAttempt("alice@example.com", "192.168.1.1");
        }

        @Test
        void normalizesEmailToLowercase() {
            when(userRepository.findActiveByEmail(Email.of("alice@example.com")))
                    .thenReturn(Optional.of(testUser));

            useCase.execute(
                    new RequestPasswordResetCommand("ALICE@EXAMPLE.COM", "192.168.1.1", null));

            verify(userRepository).findActiveByEmail(Email.of("alice@example.com"));
        }
    }

    @Nested
    class WhenGoogleOnlyAccount {

        @BeforeEach
        void setUp() {
            when(attemptTracker.countAttemptsSince(any(), any())).thenReturn(0L);
            when(userRepository.findActiveByEmail(Email.of("google@example.com")))
                    .thenReturn(Optional.of(googleOnlyUser));
        }

        @Test
        void returnsSuccessWithoutSendingOtp() {
            var result = useCase.execute(
                    new RequestPasswordResetCommand("google@example.com", "192.168.1.1", null));

            assertThat(result).isInstanceOf(Result.Success.class);
            verify(eventPublisher, never()).publishEvent(any());
            verify(tokenRepository, never()).save(any());
        }
    }

    @Nested
    class WhenEmailNotFound {

        @BeforeEach
        void setUp() {
            when(attemptTracker.countAttemptsSince(any(), any())).thenReturn(0L);
            when(userRepository.findActiveByEmail(any())).thenReturn(Optional.empty());
        }

        @Test
        void returnsSuccessToPreventEnumeration() {
            var result = useCase.execute(
                    new RequestPasswordResetCommand("nobody@example.com", "192.168.1.1", null));

            assertThat(result).isInstanceOf(Result.Success.class);
        }

        @Test
        void recordsAttemptBeforeUserLookup() {
            useCase.execute(
                    new RequestPasswordResetCommand("nobody@example.com", "192.168.1.1", null));

            verify(attemptTracker).recordAttempt("nobody@example.com", "192.168.1.1");
        }
    }

    @Nested
    class WhenCaptchaRequired {

        @BeforeEach
        void setUp() {
            when(attemptTracker.countAttemptsSince(any(), any())).thenReturn(2L);
        }

        @Test
        void returnsCaptchaRequiredErrorWhenTokenMissing() {
            var result = useCase.execute(
                    new RequestPasswordResetCommand("alice@example.com", "192.168.1.1", null));

            assertThat(result).isInstanceOf(Result.Failure.class);
            assertThat(((Result.Failure<?, AuthError>) result).error())
                    .isInstanceOf(AuthError.CaptchaRequired.class);
        }

        @Test
        void returnsCaptchaRequiredErrorWhenTokenBlank() {
            var result = useCase.execute(
                    new RequestPasswordResetCommand("alice@example.com", "192.168.1.1", ""));

            assertThat(result).isInstanceOf(Result.Failure.class);
            assertThat(((Result.Failure<?, AuthError>) result).error())
                    .isInstanceOf(AuthError.CaptchaRequired.class);
        }

        @Test
        void doesNotProceedWhenCaptchaMissing() {
            useCase.execute(
                    new RequestPasswordResetCommand("alice@example.com", "192.168.1.1", null));

            verify(userRepository, never()).findActiveByEmail(any());
            verify(tokenRepository, never()).save(any());
            verify(eventPublisher, never()).publishEvent(any());
        }
    }

    @Nested
    class WhenCaptchaValid {

        @BeforeEach
        void setUp() {
            when(attemptTracker.countAttemptsSince(any(), any())).thenReturn(2L);
            when(captchaVerifier.verifyToken("valid-captcha-token")).thenReturn(true);
            when(userRepository.findActiveByEmail(Email.of("alice@example.com")))
                    .thenReturn(Optional.of(testUser));
            when(otpGenerator.generate()).thenReturn("123456");
            when(otpHasher.hash("123456")).thenReturn("sha256hash");
            when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        }

        @Test
        void returnsSuccessAndProceedsWithOtpGeneration() {
            var result = useCase.execute(new RequestPasswordResetCommand(
                    "alice@example.com", "192.168.1.1", "valid-captcha-token"));

            assertThat(result).isInstanceOf(Result.Success.class);
            verify(tokenRepository).save(any(PasswordResetToken.class));
            verify(eventPublisher).publishEvent(any(PasswordResetRequestedEvent.class));
        }

        @Test
        void verifiesCaptchaToken() {
            useCase.execute(new RequestPasswordResetCommand(
                    "alice@example.com", "192.168.1.1", "valid-captcha-token"));

            verify(captchaVerifier).verifyToken("valid-captcha-token");
        }
    }

    @Nested
    class WhenCaptchaInvalid {

        @BeforeEach
        void setUp() {
            when(attemptTracker.countAttemptsSince(any(), any())).thenReturn(2L);
            when(captchaVerifier.verifyToken("invalid-captcha-token")).thenReturn(false);
        }

        @Test
        void returnsCaptchaInvalidError() {
            var result = useCase.execute(new RequestPasswordResetCommand(
                    "alice@example.com", "192.168.1.1", "invalid-captcha-token"));

            assertThat(result).isInstanceOf(Result.Failure.class);
            assertThat(((Result.Failure<?, AuthError>) result).error())
                    .isInstanceOf(AuthError.CaptchaInvalid.class);
        }

        @Test
        void doesNotProceedWithOtpGeneration() {
            useCase.execute(new RequestPasswordResetCommand(
                    "alice@example.com", "192.168.1.1", "invalid-captcha-token"));

            verify(userRepository, never()).findActiveByEmail(any());
            verify(tokenRepository, never()).save(any());
            verify(eventPublisher, never()).publishEvent(any());
        }
    }

    @Nested
    class WhenResendCooldown {

        @BeforeEach
        void setUp() {
            when(attemptTracker.countAttemptsSince(any(), any())).thenReturn(0L);
            when(userRepository.findActiveByEmail(Email.of("alice@example.com")))
                    .thenReturn(Optional.of(testUser));
        }

        @Test
        void returnsResendTooSoonErrorWhenCooldownNotElapsed() {
            PasswordResetToken recentToken = PasswordResetToken.issue(
                    testUser.getId(), "sha256hash", Instant.now().plusSeconds(1200));

            when(tokenRepository.findValidByUserId(testUser.getId()))
                    .thenReturn(Optional.of(recentToken));

            var result = useCase.execute(
                    new RequestPasswordResetCommand("alice@example.com", "192.168.1.1", null));

            assertThat(result).isInstanceOf(Result.Failure.class);
            var error = ((Result.Failure<?, AuthError>) result).error();
            assertThat(error).isInstanceOf(AuthError.ResendTooSoon.class);
            assertThat(((AuthError.ResendTooSoon) error).retryAfterSeconds())
                    .isGreaterThan(0)
                    .isLessThanOrEqualTo(120);
        }

        @Test
        void allowsResendAfter120Seconds() {
            PasswordResetToken oldToken = PasswordResetToken.reconstitute(
                    PasswordResetTokenId.of(java.util.UUID.randomUUID()),
                    testUser.getId(),
                    "sha256hash",
                    Instant.now().plusSeconds(1200),
                    null,
                    0,
                    null,
                    Instant.now().minusSeconds(121));

            when(tokenRepository.findValidByUserId(testUser.getId()))
                    .thenReturn(Optional.of(oldToken));
            when(otpGenerator.generate()).thenReturn("123456");
            when(otpHasher.hash("123456")).thenReturn("sha256hash");
            when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            var result = useCase.execute(
                    new RequestPasswordResetCommand("alice@example.com", "192.168.1.1", null));

            assertThat(result).isInstanceOf(Result.Success.class);
            verify(tokenRepository).invalidateAllByUserId(testUser.getId());
            verify(tokenRepository).save(any(PasswordResetToken.class));
        }

        @Test
        void doesNotSendOtpWhenCooldownNotElapsed() {
            PasswordResetToken recentToken = PasswordResetToken.issue(
                    testUser.getId(), "sha256hash", Instant.now().plusSeconds(1200));

            when(tokenRepository.findValidByUserId(testUser.getId()))
                    .thenReturn(Optional.of(recentToken));

            useCase.execute(
                    new RequestPasswordResetCommand("alice@example.com", "192.168.1.1", null));

            verify(eventPublisher, never()).publishEvent(any());
            verify(tokenRepository, never()).invalidateAllByUserId(any());
        }
    }
}
