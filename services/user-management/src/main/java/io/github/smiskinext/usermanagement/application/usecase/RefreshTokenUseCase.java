package io.github.smiskinext.usermanagement.application.usecase;

import io.github.smiskinext.shared.domain.Result;
import io.github.smiskinext.usermanagement.application.command.RefreshTokenCommand;
import io.github.smiskinext.usermanagement.application.helper.RefreshTokenIssuer;
import io.github.smiskinext.usermanagement.application.helper.UserPreferencesParser;
import io.github.smiskinext.usermanagement.application.response.LoginResponse;
import io.github.smiskinext.usermanagement.domain.AuthError;
import io.github.smiskinext.usermanagement.domain.model.RefreshToken;
import io.github.smiskinext.usermanagement.domain.port.RefreshTokenRepository;
import io.github.smiskinext.usermanagement.domain.port.TokenProvider;
import io.github.smiskinext.usermanagement.domain.port.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenUseCase {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final TokenProvider tokenProvider;
    private final RefreshTokenIssuer refreshTokenIssuer;
    private final UserPreferencesParser preferencesParser;
    private final long refreshTokenExpirySeconds;

    public RefreshTokenUseCase(
            RefreshTokenRepository refreshTokenRepository,
            UserRepository userRepository,
            TokenProvider tokenProvider,
            RefreshTokenIssuer refreshTokenIssuer,
            UserPreferencesParser preferencesParser,
            @Value("${app.jwt.refresh-token-expiry-seconds}") long refreshTokenExpirySeconds) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.tokenProvider = tokenProvider;
        this.refreshTokenIssuer = refreshTokenIssuer;
        this.preferencesParser = preferencesParser;
        this.refreshTokenExpirySeconds = refreshTokenExpirySeconds;
    }

    @Transactional
    public Result<LoginResponse, AuthError> execute(RefreshTokenCommand command) {
        String tokenHash = refreshTokenIssuer.hash(command.refreshToken());

        var tokenOpt = refreshTokenRepository.findByTokenHash(tokenHash);
        if (tokenOpt.isEmpty()) {
            return Result.failure(new AuthError.RefreshTokenNotFound());
        }

        RefreshToken token = tokenOpt.get();

        if (token.isRevoked()) {
            refreshTokenRepository.revokeAllByUserId(token.getUserId());
            return Result.failure(new AuthError.RefreshTokenReuseDetected());
        }

        if (token.isExpired()) {
            return Result.failure(new AuthError.RefreshTokenExpired());
        }

        token.revoke();
        refreshTokenRepository.save(token);

        var userOpt = userRepository.findById(token.getUserId());
        if (userOpt.isEmpty()) {
            return Result.failure(new AuthError.UserNotFound());
        }
        var user = userOpt.get();

        String newAccessToken =
                tokenProvider.generateAccessToken(user.getId(), user.getEmail().value());
        String newRawRefreshToken =
                refreshTokenIssuer.issueAndSave(user.getId(), refreshTokenExpirySeconds);

        return Result.success(new LoginResponse(
                newAccessToken,
                newRawRefreshToken,
                tokenProvider.getAccessTokenExpirySeconds(),
                preferencesParser.parseAsResponse(user.getPreferences())));
    }
}
