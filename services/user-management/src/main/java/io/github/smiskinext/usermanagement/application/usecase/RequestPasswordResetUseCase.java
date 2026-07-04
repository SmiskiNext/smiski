package io.github.smiskinext.usermanagement.application.usecase;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.phunguy65.zms.shared.domain.Result;
import io.github.phunguy65.zms.shared.domain.valueobject.Email;
import io.github.smiskinext.usermanagement.application.command.RequestPasswordResetCommand;
import io.github.smiskinext.usermanagement.domain.AuthError;
import io.github.smiskinext.usermanagement.domain.event.PasswordResetRequestedEvent;
import io.github.smiskinext.usermanagement.domain.model.PasswordResetToken;
import io.github.smiskinext.usermanagement.domain.port.CaptchaVerifier;
import io.github.smiskinext.usermanagement.domain.port.OtpGenerator;
import io.github.smiskinext.usermanagement.domain.port.OtpHasher;
import io.github.smiskinext.usermanagement.domain.port.PasswordResetAttemptTracker;
import io.github.smiskinext.usermanagement.domain.port.PasswordResetTokenRepository;
import io.github.smiskinext.usermanagement.domain.port.UserRepository;
import java.time.Duration;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use case for requesting a password reset OTP.
 *
 * <p>This use case:
 * <ul>
 *   <li>Requires CAPTCHA after repeated attempts</li>
 *   <li>Validates the email exists and has a password (not Google-only)</li>
 *   <li>Generates a 6-digit OTP and stores its hash</li>
 *   <li>Publishes a {@link PasswordResetRequestedEvent} for notification service</li>
 * </ul>
 *
 * <p>To prevent user enumeration, this returns success even if the email doesn't exist.
 * The notification service simply won't send an email in that case.
 */
@Service
public class RequestPasswordResetUseCase {

    /** OTP validity period: 20 minutes. */
    private static final Duration OTP_VALIDITY = Duration.ofMinutes(20);

    /** Resend cooldown period: 120 seconds. */
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(120);

    /** CAPTCHA required after this many failed attempts within 1 hour. */
    private static final int CAPTCHA_THRESHOLD = 2;

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final OtpGenerator otpGenerator;
    private final OtpHasher otpHasher;
    private final ApplicationEventPublisher eventPublisher;
    private final CaptchaVerifier
            captchaVerifier;
    private final PasswordResetAttemptTracker
            attemptTracker;

    public RequestPasswordResetUseCase(
            UserRepository userRepository,
            PasswordResetTokenRepository tokenRepository,
            OtpGenerator otpGenerator,
            OtpHasher otpHasher,
            ApplicationEventPublisher eventPublisher,
            CaptchaVerifier captchaVerifier,
            PasswordResetAttemptTracker
                    attemptTracker) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.otpGenerator = otpGenerator;
        this.otpHasher = otpHasher;
        this.eventPublisher = eventPublisher;
        this.captchaVerifier = captchaVerifier;
        this.attemptTracker = attemptTracker;
    }

    /**
     * Requests a password reset for the given email.
     *
     * @param command contains email and IP address
     * @return success (always, to prevent enumeration) or validation error
     */
    @Transactional
    public Result<Void, AuthError> execute(RequestPasswordResetCommand command) {
        String email = command.email().toLowerCase().trim();

        long attemptCount =
                attemptTracker.countAttemptsSince(email, Instant.now().minus(Duration.ofHours(1)));

        if (attemptCount >= CAPTCHA_THRESHOLD) {
            if (command.captchaToken() == null || command.captchaToken().isBlank()) {
                return Result.failure(new AuthError.CaptchaRequired());
            }

            if (!captchaVerifier.verifyToken(command.captchaToken())) {
                return Result.failure(new AuthError.CaptchaInvalid());
            }
        }

        attemptTracker.recordAttempt(email, command.ipAddress());

        var emailVo = Email.of(email);
        var userOpt = userRepository.findActiveByEmail(emailVo);

        if (userOpt.isEmpty()) {
            return Result.success(null);
        }

        var user = userOpt.get();

        if (!user.hasPassword()) {
            return Result.success(null);
        }

        var existingTokenOpt = tokenRepository.findValidByUserId(user.getId());
        if (existingTokenOpt.isPresent()) {
            var existingToken = existingTokenOpt.get();
            Duration timeSinceCreation =
                    Duration.between(existingToken.getCreatedAt(), Instant.now());

            if (timeSinceCreation.compareTo(RESEND_COOLDOWN) < 0) {
                long retryAfter = RESEND_COOLDOWN.minus(timeSinceCreation).getSeconds();
                return Result.failure(new AuthError.ResendTooSoon(Math.max(1, retryAfter)));
            }
        }

        tokenRepository.invalidateAllByUserId(user.getId());

        String otp = otpGenerator.generate();
        String otpHash = otpHasher.hash(otp);
        Instant expiresAt = Instant.now().plus(OTP_VALIDITY);

        PasswordResetToken token = PasswordResetToken.issue(user.getId(), otpHash, expiresAt);
        tokenRepository.save(token);

        eventPublisher.publishEvent(new PasswordResetRequestedEvent(
                UuidCreator.getTimeOrderedEpoch(),
                user.getId().value(),
                user.getEmail().value(),
                user.getFullName().value(),
                otp,
                expiresAt,
                Instant.now()));

        return Result.success(null);
    }
}
