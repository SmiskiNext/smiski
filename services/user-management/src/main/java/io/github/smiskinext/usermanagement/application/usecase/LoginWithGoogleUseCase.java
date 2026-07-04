package io.github.smiskinext.usermanagement.application.usecase;

import io.github.phunguy65.zms.shared.domain.Result;
import io.github.phunguy65.zms.shared.domain.valueobject.Email;
import io.github.smiskinext.usermanagement.application.command.GoogleLoginCommand;
import io.github.smiskinext.usermanagement.application.helper.RefreshTokenIssuer;
import io.github.smiskinext.usermanagement.application.helper.UserPreferencesParser;
import io.github.smiskinext.usermanagement.application.response.LoginResponse;
import io.github.smiskinext.usermanagement.domain.AuthError;
import io.github.smiskinext.usermanagement.domain.PublishableEvent;
import io.github.smiskinext.usermanagement.domain.model.User;
import io.github.smiskinext.usermanagement.domain.model.valueobject.FullName;
import io.github.smiskinext.usermanagement.domain.model.valueobject.Username;
import io.github.smiskinext.usermanagement.domain.port.GoogleAuthVerifier;
import io.github.smiskinext.usermanagement.domain.port.TokenProvider;
import io.github.smiskinext.usermanagement.domain.port.UserRepository;
import io.github.smiskinext.usermanagement.domain.model.valueobject.GoogleAuthClaims;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoginWithGoogleUseCase {

    private final GoogleAuthVerifier googleAuthVerifier;
    private final UserRepository userRepository;
    private final TokenProvider tokenProvider;
    private final RefreshTokenIssuer refreshTokenIssuer;
    private final UserPreferencesParser preferencesParser;
    private final ApplicationEventPublisher eventPublisher;
    private final long refreshTokenExpirySeconds;

    public LoginWithGoogleUseCase(
            GoogleAuthVerifier googleAuthVerifier,
            UserRepository userRepository,
            TokenProvider tokenProvider,
            RefreshTokenIssuer refreshTokenIssuer,
            UserPreferencesParser preferencesParser,
            ApplicationEventPublisher eventPublisher,
            @Value("${app.jwt.refresh-token-expiry-seconds}") long refreshTokenExpirySeconds) {
        this.googleAuthVerifier = googleAuthVerifier;
        this.userRepository = userRepository;
        this.tokenProvider = tokenProvider;
        this.refreshTokenIssuer = refreshTokenIssuer;
        this.preferencesParser = preferencesParser;
        this.eventPublisher = eventPublisher;
        this.refreshTokenExpirySeconds = refreshTokenExpirySeconds;
    }

    @Transactional
    public Result<LoginResponse, AuthError> execute(GoogleLoginCommand command) {
        var verifyResult = googleAuthVerifier.verify(command.idToken());
        if (verifyResult instanceof Result.Failure<?, AuthError>(AuthError error)) {
            return Result.failure(error);
        }
        var claims = ((Result.Success<
                GoogleAuthClaims,
                                AuthError>)
                        verifyResult)
                .value();

        User user;
        var byGoogleUid = userRepository.findActiveByGoogleUid(claims.uid());
        if (byGoogleUid.isPresent()) {
            user = byGoogleUid.get();
        } else {
            var byEmail = userRepository.findByEmail(Email.of(claims.email()));
            if (byEmail.isPresent()) {
                var existing = byEmail.get();
                if (existing.isDeleted()) {
                    return Result.failure(new AuthError.UserDeleted());
                }
                existing.linkGoogle(claims.uid());
                user = userRepository.save(existing);
            } else {
                String displayName =
                        claims.displayName() != null ? claims.displayName() : claims.email();

                Username username = Username.generateForGoogle();
                while (userRepository.existsActiveByUsername(username)) {
                    username = Username.generateForGoogle();
                }

                var newUser = User.registerWithGoogle(
                        Email.of(claims.email()),
                        claims.uid(),
                        FullName.of(displayName),
                        claims.photoUrl(),
                        username);
                user = userRepository.save(newUser);
                user.getDomainEvents().stream()
                        .filter(e -> e instanceof PublishableEvent)
                        .map(e -> (PublishableEvent) e)
                        .forEach(eventPublisher::publishEvent);
                user.clearDomainEvents();
            }
        }

        String accessToken =
                tokenProvider.generateAccessToken(user.getId(), user.getEmail().value());
        String rawRefreshToken =
                refreshTokenIssuer.issueAndSave(user.getId(), refreshTokenExpirySeconds);

        return Result.success(new LoginResponse(
                accessToken,
                rawRefreshToken,
                tokenProvider.getAccessTokenExpirySeconds(),
                preferencesParser.parseAsResponse(user.getPreferences())));
    }
}
