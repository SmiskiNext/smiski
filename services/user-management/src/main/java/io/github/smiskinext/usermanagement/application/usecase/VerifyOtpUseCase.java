package io.github.smiskinext.usermanagement.application.usecase;

import io.github.smiskinext.shared.domain.Result;
import io.github.smiskinext.shared.domain.valueobject.Email;
import io.github.smiskinext.usermanagement.application.command.VerifyOtpCommand;
import io.github.smiskinext.usermanagement.application.response.VerifyOtpResponse;
import io.github.smiskinext.usermanagement.domain.AuthError;
import io.github.smiskinext.usermanagement.domain.model.PasswordResetToken;
import io.github.smiskinext.usermanagement.domain.model.valueobject.TemporaryTokenPurpose;
import io.github.smiskinext.usermanagement.domain.port.OtpHasher;
import io.github.smiskinext.usermanagement.domain.port.PasswordResetTokenRepository;
import io.github.smiskinext.usermanagement.domain.port.TemporaryTokenProvider;
import io.github.smiskinext.usermanagement.domain.port.UserRepository;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use case for verifying OTP and returning a temporary token.
 *
 * <p>This use case:
 * <ul>
 *   <li>Validates the OTP against the stored hash</li>
 *   <li>Checks token validity (not expired, not used, not locked)</li>
 *   <li>Applies progressive delays based on attempt count</li>
 *   <li>Returns a temporary token for password reset</li>
 * </ul>
 */
@Service
public class VerifyOtpUseCase {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final OtpHasher otpHasher;
    private final TemporaryTokenProvider temporaryTokenProvider;

    public VerifyOtpUseCase(
            UserRepository userRepository,
            PasswordResetTokenRepository tokenRepository,
            OtpHasher otpHasher,
            TemporaryTokenProvider temporaryTokenProvider) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.otpHasher = otpHasher;
        this.temporaryTokenProvider = temporaryTokenProvider;
    }

    @Transactional
    public Result<VerifyOtpResponse, AuthError> execute(VerifyOtpCommand command) {
        String email = command.email().toLowerCase().trim();
        var emailVo = Email.of(email);

        var userOpt = userRepository.findActiveByEmail(emailVo);
        if (userOpt.isEmpty()) {
            return Result.failure(new AuthError.OtpInvalid());
        }

        var user = userOpt.get();

        if (!user.hasPassword()) {
            return Result.failure(new AuthError.GoogleOnlyAccount());
        }

        var tokenOpt = tokenRepository.findValidByUserId(user.getId());
        if (tokenOpt.isEmpty()) {
            return Result.failure(new AuthError.OtpInvalid());
        }

        PasswordResetToken token = tokenOpt.get();

        if (token.isExpired()) {
            return Result.failure(new AuthError.OtpExpired());
        }

        if (token.isUsed()) {
            return Result.failure(new AuthError.OtpAlreadyUsed());
        }

        if (token.isLocked()) {
            return Result.failure(new AuthError.OtpLocked());
        }

        if (token.getLastAttemptTimestamp().isPresent()) {
            long delaySeconds = calculateProgressiveDelay(token.getAttempts());
            Instant nextAllowedAttempt =
                    token.getLastAttemptTimestamp().get().plusSeconds(delaySeconds);

            if (Instant.now().isBefore(nextAllowedAttempt)) {
                long retryAfter =
                        Duration.between(Instant.now(), nextAllowedAttempt).getSeconds();
                return Result.failure(new AuthError.ResendTooSoon(Math.max(1, retryAfter)));
            }
        }

        if (!otpHasher.verify(command.otp(), token.getOtpHash())) {
            token.incrementAttempts();
            tokenRepository.save(token);

            if (token.isLocked()) {
                return Result.failure(new AuthError.OtpLocked());
            }

            return Result.failure(new AuthError.OtpInvalid());
        }

        token.markUsed();
        tokenRepository.save(token);

        var temporaryToken = temporaryTokenProvider.generateToken(
                user.getId(), TemporaryTokenPurpose.PASSWORD_RESET);

        return Result.success(new VerifyOtpResponse(temporaryToken.value()));
    }

    private long calculateProgressiveDelay(int attempts) {
        if (attempts == 0) {
            return 0;
        }
        return Math.min((long) Math.pow(2, attempts - 1), 16);
    }
}
