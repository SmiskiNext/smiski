package io.github.smiskinext.usermanagement.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.smiskinext.shared.domain.Result;
import io.github.smiskinext.shared.domain.valueobject.Email;
import io.github.smiskinext.shared.domain.valueobject.UserId;
import io.github.smiskinext.usermanagement.application.command.GoogleLoginCommand;
import io.github.smiskinext.usermanagement.application.helper.RefreshTokenIssuer;
import io.github.smiskinext.usermanagement.application.helper.UserPreferencesParser;
import io.github.smiskinext.usermanagement.application.response.LoginResponse;
import io.github.smiskinext.usermanagement.domain.AuthError;
import io.github.smiskinext.usermanagement.domain.model.RefreshToken;
import io.github.smiskinext.usermanagement.domain.model.User;
import io.github.smiskinext.usermanagement.domain.model.valueobject.FullName;
import io.github.smiskinext.usermanagement.domain.model.valueobject.GoogleAuthClaims;
import io.github.smiskinext.usermanagement.domain.model.valueobject.HashedPassword;
import io.github.smiskinext.usermanagement.domain.port.GoogleAuthVerifier;
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
class LoginWithGoogleUseCaseTest {

    @Mock
    GoogleAuthVerifier googleAuthVerifier;

    @Mock
    UserRepository userRepository;

    @Mock
    RefreshTokenRepository refreshTokenRepository;

    @Mock
    TokenProvider tokenProvider;

    @Mock
    ApplicationEventPublisher eventPublisher;

    LoginWithGoogleUseCase useCase;

    private static final String GOOGLE_UID = "google-uid-123";
    private static final String EMAIL = "alice@example.com";
    private static final String ID_TOKEN = "firebase.id.token";
    private static final GoogleAuthClaims CLAIMS =
            new GoogleAuthClaims(GOOGLE_UID, EMAIL, "Alice", "https://photo.url");

    @BeforeEach
    void setUp() {
        var refreshTokenIssuer = new RefreshTokenIssuer(refreshTokenRepository);
        var preferencesParser = new UserPreferencesParser(new ObjectMapper());
        useCase = new LoginWithGoogleUseCase(
                googleAuthVerifier,
                userRepository,
                tokenProvider,
                refreshTokenIssuer,
                preferencesParser,
                eventPublisher,
                2592000L);
    }

    @Test
    void invalidTokenReturnsFailure() {
        when(googleAuthVerifier.verify(ID_TOKEN))
                .thenReturn(Result.failure(new AuthError.InvalidFirebaseToken()));

        var result = useCase.execute(new GoogleLoginCommand(ID_TOKEN));

        assertThat(((Result.Failure<?, AuthError>) result).error())
                .isInstanceOf(AuthError.InvalidFirebaseToken.class);
        verifyNoInteractions(userRepository);
    }

    @Test
    void firstTimeLoginCreatesNewUser() {
        when(googleAuthVerifier.verify(ID_TOKEN)).thenReturn(Result.success(CLAIMS));
        when(userRepository.findActiveByGoogleUid(GOOGLE_UID)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(Email.of(EMAIL))).thenReturn(Optional.empty());
        when(userRepository.existsActiveByUsername(any())).thenReturn(false);
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(tokenProvider.generateAccessToken(any(), any())).thenReturn("access.token");
        when(tokenProvider.getAccessTokenExpirySeconds()).thenReturn(900L);

        var result = useCase.execute(new GoogleLoginCommand(ID_TOKEN));

        assertThat(result).isInstanceOf(Result.Success.class);
        var response = (LoginResponse) ((Result.Success<?, ?>) result).value();
        assertThat(response.accessToken()).isEqualTo("access.token");
        verify(userRepository).save(any(User.class));
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void returningGoogleUserSkipsCreation() {
        var existingUser = User.reconstitute(
                UserId.of(UuidCreator.getTimeOrderedEpoch()),
                Email.of(EMAIL),
                null,
                FullName.of("Alice"),
                null,
                null,
                GOOGLE_UID,
                "GOOGLE",
                null,
                Instant.now(),
                Instant.now(),
                null);

        when(googleAuthVerifier.verify(ID_TOKEN)).thenReturn(Result.success(CLAIMS));
        when(userRepository.findActiveByGoogleUid(GOOGLE_UID))
                .thenReturn(Optional.of(existingUser));
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(tokenProvider.generateAccessToken(any(), any())).thenReturn("access.token");
        when(tokenProvider.getAccessTokenExpirySeconds()).thenReturn(900L);

        var result = useCase.execute(new GoogleLoginCommand(ID_TOKEN));

        assertThat(result).isInstanceOf(Result.Success.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void accountLinkingForExistingEmailUser() {
        var emailUser = User.reconstitute(
                UserId.of(UuidCreator.getTimeOrderedEpoch()),
                Email.of(EMAIL),
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

        when(googleAuthVerifier.verify(ID_TOKEN)).thenReturn(Result.success(CLAIMS));
        when(userRepository.findActiveByGoogleUid(GOOGLE_UID)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(Email.of(EMAIL))).thenReturn(Optional.of(emailUser));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(tokenProvider.generateAccessToken(any(), any())).thenReturn("access.token");
        when(tokenProvider.getAccessTokenExpirySeconds()).thenReturn(900L);

        var result = useCase.execute(new GoogleLoginCommand(ID_TOKEN));

        assertThat(result).isInstanceOf(Result.Success.class);
        verify(userRepository).save(argThat(u -> "BOTH".equals(u.getAuthProvider())));
    }

    @Test
    void deletedUserReturnsFailure() {
        var deletedUser = User.reconstitute(
                UserId.of(UuidCreator.getTimeOrderedEpoch()),
                Email.of(EMAIL),
                null,
                FullName.of("Alice"),
                null,
                null,
                null,
                "EMAIL",
                null,
                Instant.now(),
                Instant.now(),
                Instant.now()); // deleted

        when(googleAuthVerifier.verify(ID_TOKEN)).thenReturn(Result.success(CLAIMS));
        when(userRepository.findActiveByGoogleUid(GOOGLE_UID)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(Email.of(EMAIL))).thenReturn(Optional.of(deletedUser));

        var result = useCase.execute(new GoogleLoginCommand(ID_TOKEN));

        assertThat(((Result.Failure<?, AuthError>) result).error())
                .isInstanceOf(AuthError.UserDeleted.class);
    }

    @Test
    void newGoogleUser_getsAutoGeneratedUsername() {
        when(googleAuthVerifier.verify(ID_TOKEN)).thenReturn(Result.success(CLAIMS));
        when(userRepository.findActiveByGoogleUid(GOOGLE_UID)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(Email.of(EMAIL))).thenReturn(Optional.empty());
        when(userRepository.existsActiveByUsername(any())).thenReturn(false);
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(tokenProvider.generateAccessToken(any(), any())).thenReturn("access.token");
        when(tokenProvider.getAccessTokenExpirySeconds()).thenReturn(900L);

        useCase.execute(new GoogleLoginCommand(ID_TOKEN));

        // Verify a user was saved with a generated username (starts with "user_")
        verify(userRepository)
                .save(argThat(u -> u.getUsername().isPresent()
                        && u.getUsername().get().value().startsWith("user_")));
    }

    @Test
    void newGoogleUser_usernameCollision_retriesUntilUnique() {
        when(googleAuthVerifier.verify(ID_TOKEN)).thenReturn(Result.success(CLAIMS));
        when(userRepository.findActiveByGoogleUid(GOOGLE_UID)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(Email.of(EMAIL))).thenReturn(Optional.empty());
        // First two candidates collide, third is unique
        when(userRepository.existsActiveByUsername(any())).thenReturn(true, true, false);
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(tokenProvider.generateAccessToken(any(), any())).thenReturn("access.token");
        when(tokenProvider.getAccessTokenExpirySeconds()).thenReturn(900L);

        var result = useCase.execute(new GoogleLoginCommand(ID_TOKEN));

        assertThat(result).isInstanceOf(Result.Success.class);
        // existsActiveByUsername called 3 times (2 collisions + 1 success)
        verify(userRepository, times(3)).existsActiveByUsername(any());
        verify(userRepository).save(any(User.class));
    }
}
