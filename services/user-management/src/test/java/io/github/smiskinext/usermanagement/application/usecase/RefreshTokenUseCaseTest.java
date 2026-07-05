package io.github.smiskinext.usermanagement.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.smiskinext.shared.domain.Result;
import io.github.smiskinext.shared.domain.valueobject.Email;
import io.github.smiskinext.shared.domain.valueobject.UserId;
import io.github.smiskinext.usermanagement.application.command.RefreshTokenCommand;
import io.github.smiskinext.usermanagement.application.helper.RefreshTokenIssuer;
import io.github.smiskinext.usermanagement.application.helper.UserPreferencesParser;
import io.github.smiskinext.usermanagement.domain.AuthError;
import io.github.smiskinext.usermanagement.domain.model.RefreshToken;
import io.github.smiskinext.usermanagement.domain.model.User;
import io.github.smiskinext.usermanagement.domain.model.valueobject.FullName;
import io.github.smiskinext.usermanagement.domain.model.valueobject.HashedPassword;
import io.github.smiskinext.usermanagement.domain.model.valueobject.RefreshTokenId;
import io.github.smiskinext.usermanagement.domain.port.RefreshTokenRepository;
import io.github.smiskinext.usermanagement.domain.port.TokenProvider;
import io.github.smiskinext.usermanagement.domain.port.UserRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class RefreshTokenUseCaseTest {

    @Mock
    RefreshTokenRepository refreshTokenRepository;

    @Mock
    UserRepository userRepository;

    @Mock
    TokenProvider tokenProvider;

    RefreshTokenUseCase useCase;
    RefreshTokenIssuer refreshTokenIssuer;

    private static final String RAW_TOKEN = "rawRefreshTokenValue";
    private static final UserId USER_ID = UserId.of(UuidCreator.getTimeOrderedEpoch());

    @BeforeEach
    void setUp() {
        refreshTokenIssuer = new RefreshTokenIssuer(refreshTokenRepository);
        useCase = new RefreshTokenUseCase(
                refreshTokenRepository,
                userRepository,
                tokenProvider,
                refreshTokenIssuer,
                new UserPreferencesParser(new ObjectMapper()),
                2592000L);
    }

    @Test
    void expiredTokenReturnsFailure() {
        String tokenHash = refreshTokenIssuer.hash(RAW_TOKEN);
        var expired = RefreshToken.reconstitute(
                RefreshTokenId.of(UuidCreator.getTimeOrderedEpoch()),
                USER_ID,
                tokenHash,
                Instant.now().minusSeconds(1),
                null,
                Instant.now().minusSeconds(100));
        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(expired));

        var result = useCase.execute(new RefreshTokenCommand(RAW_TOKEN));

        assertThat(((Result.Failure<?, AuthError>) result).error())
                .isInstanceOf(AuthError.RefreshTokenExpired.class);
    }

    @Test
    void revokedTokenTriggersReuseDetection() {
        String tokenHash = refreshTokenIssuer.hash(RAW_TOKEN);
        var revoked = RefreshToken.reconstitute(
                RefreshTokenId.of(UuidCreator.getTimeOrderedEpoch()),
                USER_ID,
                tokenHash,
                Instant.now().plusSeconds(3600),
                Instant.now().minusSeconds(10),
                Instant.now().minusSeconds(100));
        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(revoked));

        var result = useCase.execute(new RefreshTokenCommand(RAW_TOKEN));

        assertThat(((Result.Failure<?, AuthError>) result).error())
                .isInstanceOf(AuthError.RefreshTokenReuseDetected.class);
        verify(refreshTokenRepository).revokeAllByUserId(USER_ID);
    }

    @Test
    void validTokenRotation() {
        String tokenHash = refreshTokenIssuer.hash(RAW_TOKEN);
        var valid = RefreshToken.reconstitute(
                RefreshTokenId.of(UuidCreator.getTimeOrderedEpoch()),
                USER_ID,
                tokenHash,
                Instant.now().plusSeconds(3600),
                null,
                Instant.now().minusSeconds(10));
        var user = User.reconstitute(
                USER_ID,
                Email.of("alice@example.com"),
                HashedPassword.of("$argon2id$h"),
                FullName.of("Alice"),
                null,
                null,
                null,
                "EMAIL",
                null,
                Instant.now(),
                Instant.now(),
                null);

        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(valid));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(tokenProvider.generateAccessToken(any(), any())).thenReturn("new.access.token");
        when(tokenProvider.getAccessTokenExpirySeconds()).thenReturn(900L);
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = useCase.execute(new RefreshTokenCommand(RAW_TOKEN));

        assertThat(result).isInstanceOf(Result.Success.class);
        assertThat(valid.isRevoked()).isTrue();
        verify(refreshTokenRepository, times(2)).save(any(RefreshToken.class));
    }
}
