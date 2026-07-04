package io.github.smiskinext.usermanagement.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.phunguy65.zms.shared.domain.Result;
import io.github.phunguy65.zms.shared.domain.valueobject.UserId;
import io.github.smiskinext.usermanagement.application.command.LogoutCommand;
import io.github.smiskinext.usermanagement.application.helper.RefreshTokenIssuer;
import io.github.smiskinext.usermanagement.domain.AuthError;
import io.github.smiskinext.usermanagement.domain.model.RefreshToken;
import io.github.smiskinext.usermanagement.domain.model.valueobject.RefreshTokenId;
import io.github.smiskinext.usermanagement.domain.port.RefreshTokenRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class LogoutUserUseCaseTest {

    @Mock
    RefreshTokenRepository refreshTokenRepository;

    @Mock
    ApplicationEventPublisher eventPublisher;

    LogoutUserUseCase useCase;
    RefreshTokenIssuer refreshTokenIssuer;

    private static final String RAW_TOKEN = "someRawToken";

    @BeforeEach
    void setUp() {
        refreshTokenIssuer = new RefreshTokenIssuer(refreshTokenRepository);
        useCase = new LogoutUserUseCase(refreshTokenRepository, refreshTokenIssuer, eventPublisher);
    }

    @Test
    void unknownTokenReturnsFailure() {
        String tokenHash = refreshTokenIssuer.hash(RAW_TOKEN);
        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.empty());

        var result = useCase.execute(new LogoutCommand(RAW_TOKEN));

        assertThat(((Result.Failure<?, AuthError>) result).error())
                .isInstanceOf(AuthError.RefreshTokenNotFound.class);
    }

    @Test
    void alreadyRevokedIsIdempotentSuccess() {
        String tokenHash = refreshTokenIssuer.hash(RAW_TOKEN);
        var revoked = RefreshToken.reconstitute(
                RefreshTokenId.of(UuidCreator.getTimeOrderedEpoch()),
                UserId.of(UuidCreator.getTimeOrderedEpoch()),
                tokenHash,
                Instant.now().plusSeconds(3600),
                Instant.now().minusSeconds(10),
                Instant.now().minusSeconds(100));
        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(revoked));

        var result = useCase.execute(new LogoutCommand(RAW_TOKEN));

        assertThat(result).isInstanceOf(Result.Success.class);
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void successfulRevocation() {
        String tokenHash = refreshTokenIssuer.hash(RAW_TOKEN);
        var active = RefreshToken.reconstitute(
                RefreshTokenId.of(UuidCreator.getTimeOrderedEpoch()),
                UserId.of(UuidCreator.getTimeOrderedEpoch()),
                tokenHash,
                Instant.now().plusSeconds(3600),
                null,
                Instant.now().minusSeconds(10));
        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(active));
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = useCase.execute(new LogoutCommand(RAW_TOKEN));

        assertThat(result).isInstanceOf(Result.Success.class);
        assertThat(active.isRevoked()).isTrue();
        verify(refreshTokenRepository).save(active);
    }
}
