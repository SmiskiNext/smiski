package io.github.smiskinext.usermanagement.application.usecase;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.phunguy65.zms.shared.domain.Result;
import io.github.phunguy65.zms.shared.domain.valueobject.Email;
import io.github.smiskinext.usermanagement.application.command.LoginCommand;
import io.github.smiskinext.usermanagement.application.helper.RefreshTokenIssuer;
import io.github.smiskinext.usermanagement.application.helper.UserPreferencesParser;
import io.github.smiskinext.usermanagement.application.response.LoginResponse;
import io.github.smiskinext.usermanagement.domain.AuthError;
import io.github.smiskinext.usermanagement.domain.event.UserLoggedInEvent;
import io.github.smiskinext.usermanagement.domain.port.PasswordHasher;
import io.github.smiskinext.usermanagement.domain.port.TokenProvider;
import io.github.smiskinext.usermanagement.domain.port.UserRepository;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoginUserUseCase {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final TokenProvider tokenProvider;
    private final RefreshTokenIssuer refreshTokenIssuer;
    private final UserPreferencesParser preferencesParser;
    private final long refreshTokenExpirySeconds;
    private final ApplicationEventPublisher eventPublisher;

    public LoginUserUseCase(
            UserRepository userRepository,
            PasswordHasher passwordHasher,
            TokenProvider tokenProvider,
            RefreshTokenIssuer refreshTokenIssuer,
            UserPreferencesParser preferencesParser,
            @Value("${app.jwt.refresh-token-expiry-seconds}") long refreshTokenExpirySeconds,
            ApplicationEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.tokenProvider = tokenProvider;
        this.refreshTokenIssuer = refreshTokenIssuer;
        this.preferencesParser = preferencesParser;
        this.refreshTokenExpirySeconds = refreshTokenExpirySeconds;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Result<LoginResponse, AuthError> execute(LoginCommand command) {
        var emailVo = Email.of(command.email());

        var anyUser = userRepository.findByEmail(emailVo);
        if (anyUser.isPresent() && anyUser.get().isDeleted()) {
            return Result.failure(new AuthError.UserDeleted());
        }

        var userOpt = userRepository.findActiveByEmail(emailVo);
        if (userOpt.isEmpty()) {
            return Result.failure(new AuthError.InvalidCredentials());
        }

        var user = userOpt.get();

        // Guard: Google-only accounts have no password
        if (!user.hasPassword()) {
            return Result.failure(new AuthError.InvalidCredentials());
        }

        if (!passwordHasher.verify(command.password(), user.getHashedPassword().orElseThrow())) {
            return Result.failure(new AuthError.InvalidCredentials());
        }

        String accessToken =
                tokenProvider.generateAccessToken(user.getId(), user.getEmail().value());
        String rawRefreshToken =
                refreshTokenIssuer.issueAndSave(user.getId(), refreshTokenExpirySeconds);

        Instant loginAt = Instant.now();
        eventPublisher.publishEvent(new UserLoggedInEvent(
                UuidCreator.getTimeOrderedEpoch(),
                user.getId().value(),
                user.getEmail().value(),
                loginAt));

        return Result.success(new LoginResponse(
                accessToken,
                rawRefreshToken,
                tokenProvider.getAccessTokenExpirySeconds(),
                preferencesParser.parseAsResponse(user.getPreferences())));
    }
}
