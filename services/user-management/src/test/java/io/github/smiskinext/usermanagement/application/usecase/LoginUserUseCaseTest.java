package io.github.smiskinext.usermanagement.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.smiskinext.shared.domain.Result;
import io.github.smiskinext.shared.domain.valueobject.Email;
import io.github.smiskinext.shared.domain.valueobject.UserId;
import io.github.smiskinext.usermanagement.application.command.LoginCommand;
import io.github.smiskinext.usermanagement.application.helper.RefreshTokenIssuer;
import io.github.smiskinext.usermanagement.application.helper.UserPreferencesParser;
import io.github.smiskinext.usermanagement.application.response.LoginResponse;
import io.github.smiskinext.usermanagement.domain.AuthError;
import io.github.smiskinext.usermanagement.domain.model.RefreshToken;
import io.github.smiskinext.usermanagement.domain.model.User;
import io.github.smiskinext.usermanagement.domain.model.valueobject.FullName;
import io.github.smiskinext.usermanagement.domain.model.valueobject.HashedPassword;
import io.github.smiskinext.usermanagement.domain.port.PasswordHasher;
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
import org.springframework.context.ApplicationEventPublisher;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class LoginUserUseCaseTest {

    @Mock
    UserRepository userRepository;

    @Mock
    PasswordHasher passwordHasher;

    @Mock
    RefreshTokenRepository refreshTokenRepository;

    @Mock
    TokenProvider tokenProvider;

    @Mock
    ApplicationEventPublisher eventPublisher;

    LoginUserUseCase useCase;
    RefreshTokenIssuer refreshTokenIssuer;

    private User testUser;

    @BeforeEach
    void setUp() {
        refreshTokenIssuer = new RefreshTokenIssuer(refreshTokenRepository);
        useCase = new LoginUserUseCase(
                userRepository,
                passwordHasher,
                tokenProvider,
                refreshTokenIssuer,
                new UserPreferencesParser(new ObjectMapper()),
                2592000L,
                eventPublisher);
        testUser = User.reconstitute(
                UserId.of(UuidCreator.getTimeOrderedEpoch()),
                Email.of("alice@example.com"),
                HashedPassword.of("$argon2id$hash"),
                FullName.of("Alice"),
                null,
                null,
                null,
                "EMAIL",
                null,
                Instant.now(),
                Instant.now(),
                null);
    }

    @Test
    void successfulLogin() {
        when(userRepository.findByEmail(Email.of("alice@example.com")))
                .thenReturn(Optional.of(testUser));
        when(userRepository.findActiveByEmail(Email.of("alice@example.com")))
                .thenReturn(Optional.of(testUser));
        when(passwordHasher.verify("password123", testUser.getHashedPassword().orElseThrow()))
                .thenReturn(true);
        when(tokenProvider.generateAccessToken(any(), any())).thenReturn("access.token.here");
        when(tokenProvider.getAccessTokenExpirySeconds()).thenReturn(900L);
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = useCase.execute(new LoginCommand("alice@example.com", "password123"));

        assertThat(result).isInstanceOf(Result.Success.class);
        var response = (LoginResponse) ((Result.Success<?, ?>) result).value();
        assertThat(response.accessToken()).isEqualTo("access.token.here");
        assertThat(response.refreshToken()).isNotBlank();
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void wrongPasswordReturnsInvalidCredentials() {
        when(userRepository.findByEmail(any())).thenReturn(Optional.of(testUser));
        when(userRepository.findActiveByEmail(any())).thenReturn(Optional.of(testUser));
        when(passwordHasher.verify(any(), any())).thenReturn(false);

        var result = useCase.execute(new LoginCommand("alice@example.com", "wrong"));

        assertThat(((Result.Failure<?, AuthError>) result).error())
                .isInstanceOf(AuthError.InvalidCredentials.class);
        when(userRepository.findByEmail(any())).thenReturn(Optional.empty());
        when(userRepository.findActiveByEmail(any())).thenReturn(Optional.empty());

        result = useCase.execute(new LoginCommand("nobody@example.com", "pass"));

        assertThat(((Result.Failure<?, AuthError>) result).error())
                .isInstanceOf(AuthError.InvalidCredentials.class);
    }

    @Test
    void refreshTokenStoredAsHash() {
        when(userRepository.findByEmail(any())).thenReturn(Optional.of(testUser));
        when(userRepository.findActiveByEmail(any())).thenReturn(Optional.of(testUser));
        when(passwordHasher.verify(any(), any())).thenReturn(true);
        when(tokenProvider.generateAccessToken(any(), any())).thenReturn("tok");
        when(tokenProvider.getAccessTokenExpirySeconds()).thenReturn(900L);
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = useCase.execute(new LoginCommand("alice@example.com", "password123"));
        var response = (LoginResponse) ((Result.Success<?, ?>) result).value();

        // The raw token returned to client should NOT equal the stored hash
        String rawToken = response.refreshToken();
        String expectedHash = refreshTokenIssuer.hash(rawToken);

        verify(refreshTokenRepository)
                .save(argThat((RefreshToken rt) -> rt.getTokenHash().equals(expectedHash)));
    }

    @Test
    void googleOnlyAccountCannotLoginWithPassword() {
        var googleUser = User.reconstitute(
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

        when(userRepository.findByEmail(Email.of("google@example.com")))
                .thenReturn(Optional.of(googleUser));
        when(userRepository.findActiveByEmail(Email.of("google@example.com")))
                .thenReturn(Optional.of(googleUser));

        var result = useCase.execute(new LoginCommand("google@example.com", "anypassword"));

        assertThat(((Result.Failure<?, AuthError>) result).error())
                .isInstanceOf(AuthError.InvalidCredentials.class);
        verify(passwordHasher, never()).verify(any(), any());
    }
}
