package io.github.smiskinext.usermanagement.application.usecase;

import io.github.smiskinext.shared.domain.Result;
import io.github.smiskinext.shared.domain.valueobject.Email;
import io.github.smiskinext.usermanagement.application.command.ResetPasswordCommand;
import io.github.smiskinext.usermanagement.domain.AuthError;
import io.github.smiskinext.usermanagement.domain.model.PasswordResetToken;
import io.github.smiskinext.usermanagement.domain.model.valueobject.HashedPassword;
import io.github.smiskinext.usermanagement.domain.model.valueobject.TemporaryTokenPurpose;
import io.github.smiskinext.usermanagement.domain.port.OtpHasher;
import io.github.smiskinext.usermanagement.domain.port.PasswordHasher;
import io.github.smiskinext.usermanagement.domain.port.PasswordResetTokenRepository;
import io.github.smiskinext.usermanagement.domain.port.RefreshTokenRepository;
import io.github.smiskinext.usermanagement.domain.port.TemporaryTokenProvider;
import io.github.smiskinext.usermanagement.domain.port.UserRepository;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use case for resetting a password using an OTP or temporary token.
 *
 * <p>This use case:
 * <ul>
 *   <li>Validates the OTP (legacy) or temporary token (new)</li>
 *   <li>Checks token validity (not expired, not used, not locked)</li>
 *   <li>Applies progressive delays for OTP verification failures</li>
 *   <li>Updates the user's password</li>
 *   <li>Revokes all existing refresh tokens (security measure)</li>
 *   <li>Marks the token as used</li>
 * </ul>
 */
@Service
public class ResetPasswordUseCase {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordHasher passwordHasher;
    private final OtpHasher otpHasher;
    private final TemporaryTokenProvider temporaryTokenProvider;

    public ResetPasswordUseCase(
            UserRepository userRepository,
            PasswordResetTokenRepository tokenRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordHasher passwordHasher,
            OtpHasher otpHasher,
            TemporaryTokenProvider temporaryTokenProvider) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordHasher = passwordHasher;
        this.otpHasher = otpHasher;
        this.temporaryTokenProvider = temporaryTokenProvider;
    }

    /**
     * Resets the user's password using an OTP or temporary token.
     *
     * @param command contains email, OTP/token, and new password
     * @return success or the appropriate error
     */
    @Transactional
    public Result<Void, AuthError> execute(ResetPasswordCommand command) {
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

        if (command.temporaryToken() != null && !command.temporaryToken().isBlank()) {
            try {
                var tempToken = temporaryTokenProvider.validateToken(
                        command.temporaryToken(), TemporaryTokenPurpose.PASSWORD_RESET);

                if (tempToken.isExpired()) {
                    return Result.failure(new AuthError.TokenExpired());
                }

                if (!tempToken.userId().equals(user.getId())) {
                    return Result.failure(new AuthError.TokenInvalid());
                }

                HashedPassword newHashedPassword = passwordHasher.hash(command.newPassword());
                user.updatePassword(newHashedPassword);
                userRepository.save(user);

                refreshTokenRepository.revokeAllByUserId(user.getId());

                return Result.success(null);

            } catch (IllegalArgumentException e) {
                return Result.failure(new AuthError.TokenInvalid());
            }
        }

        if (command.otp() != null && !command.otp().isBlank()) {
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

            HashedPassword newHashedPassword = passwordHasher.hash(command.newPassword());
            user.updatePassword(newHashedPassword);
            userRepository.save(user);

            refreshTokenRepository.revokeAllByUserId(user.getId());

            token.markUsed();
            tokenRepository.save(token);

            return Result.success(null);
        }

        return Result.failure(new AuthError.OtpInvalid());
    }

    private long calculateProgressiveDelay(int attempts) {
        if (attempts == 0) {
            return 0;
        }
        return Math.min((long) Math.pow(2, attempts - 1), 16);
    }
}
