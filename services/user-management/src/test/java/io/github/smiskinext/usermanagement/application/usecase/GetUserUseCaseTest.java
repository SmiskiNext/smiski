package io.github.smiskinext.usermanagement.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.smiskinext.shared.domain.Result;
import io.github.smiskinext.shared.domain.valueobject.UserId;
import io.github.smiskinext.usermanagement.application.helper.UserPreferencesParser;
import io.github.smiskinext.usermanagement.application.response.UserResponse;
import io.github.smiskinext.usermanagement.domain.AuthError;
import io.github.smiskinext.usermanagement.domain.port.UserRepository;
import io.github.smiskinext.usermanagement.domain.projection.UserSummary;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class GetUserUseCaseTest {

    @Mock
    UserRepository userRepository;

    GetUserUseCase useCase;

    private static final UserId USER_ID = UserId.of(UuidCreator.getTimeOrderedEpoch());

    @BeforeEach
    void setUp() {
        useCase = new GetUserUseCase(userRepository, new UserPreferencesParser(new ObjectMapper()));
    }

    private UserSummary buildUser() {
        Instant now = Instant.now();
        return new UserSummary(
                USER_ID.value(),
                "alice@example.com",
                "Alice",
                null,
                "https://example.com/avatar.png",
                "EMAIL",
                null,
                now,
                now);
    }

    @Test
    void execute_userFound_returnsUserResponse() {
        when(userRepository.findSummaryActiveById(USER_ID)).thenReturn(Optional.of(buildUser()));

        var result = useCase.execute(USER_ID);

        assertThat(result).isInstanceOf(Result.Success.class);
        var response = (UserResponse) ((Result.Success<?, ?>) result).value();
        assertThat(response.id()).isEqualTo(USER_ID.value());
        assertThat(response.email()).isEqualTo("alice@example.com");
        assertThat(response.fullName()).isEqualTo("Alice");
        assertThat(response.avatarUrl()).isEqualTo("https://example.com/avatar.png");
        assertThat(response.authProvider()).isEqualTo("EMAIL");
        // Preferences are empty when null in DB
        assertThat(response.preferences().settings()).isEmpty();
    }

    @Test
    void execute_userNotFound_returnsFailure() {
        when(userRepository.findSummaryActiveById(USER_ID)).thenReturn(Optional.empty());

        var result = useCase.execute(USER_ID);

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<?, AuthError>) result).error())
                .isInstanceOf(AuthError.UserNotFound.class);
    }
}
