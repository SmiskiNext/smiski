package io.github.smiskinext.usermanagement.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.smiskinext.shared.domain.Result;
import io.github.smiskinext.shared.domain.valueobject.UserId;
import io.github.smiskinext.usermanagement.application.helper.UserPreferencesParser;
import io.github.smiskinext.usermanagement.application.response.UserPreferencesResponse;
import io.github.smiskinext.usermanagement.domain.AuthError;
import io.github.smiskinext.usermanagement.domain.port.UserRepository;
import io.github.smiskinext.usermanagement.domain.projection.UserSummary;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class UserPreferencesUseCaseTest {

    @Mock
    UserRepository userRepository;

    GetUserPreferencesUseCase getUseCase;

    private static final UserId USER_ID = UserId.of(UuidCreator.getTimeOrderedEpoch());

    @BeforeEach
    void setUp() {
        var preferencesParser = new UserPreferencesParser(new ObjectMapper());
        getUseCase = new GetUserPreferencesUseCase(userRepository, preferencesParser);
    }

    private UserSummary userWithPrefs(String prefsJson) {
        Instant now = Instant.now();
        return new UserSummary(
                USER_ID.value(),
                "alice@example.com",
                "Alice",
                null,
                null,
                "GOOGLE",
                prefsJson,
                now,
                now);
    }

    @Test
    void getPreferences_nullPrefs_returnsEmpty() {
        when(userRepository.findSummaryActiveById(USER_ID))
                .thenReturn(Optional.of(userWithPrefs(null)));

        var result = getUseCase.execute(USER_ID);

        assertThat(result).isInstanceOf(Result.Success.class);
        var prefs = (UserPreferencesResponse) ((Result.Success<?, ?>) result).value();
        assertThat(prefs.settings()).isEmpty();
    }

    @Test
    void getPreferences_storedPrefs_returnsStoredAsIs() throws Exception {
        String json = new ObjectMapper()
                .writeValueAsString(Map.of("theme", "dark", "fontSize", 14, "lang", "vi"));
        when(userRepository.findSummaryActiveById(USER_ID))
                .thenReturn(Optional.of(userWithPrefs(json)));

        var result = getUseCase.execute(USER_ID);

        assertThat(result).isInstanceOf(Result.Success.class);
        var prefs = (UserPreferencesResponse) ((Result.Success<?, ?>) result).value();
        assertThat(prefs.settings()).containsEntry("theme", "dark");
        assertThat(prefs.settings()).containsEntry("fontSize", 14);
        assertThat(prefs.settings()).containsEntry("lang", "vi");
    }

    @Test
    void getPreferences_arbitraryKeys_accepted() throws Exception {
        String json = new ObjectMapper()
                .writeValueAsString(Map.of("customKey", "customValue", "nested", Map.of("a", 1)));
        when(userRepository.findSummaryActiveById(USER_ID))
                .thenReturn(Optional.of(userWithPrefs(json)));

        var result = getUseCase.execute(USER_ID);

        assertThat(result).isInstanceOf(Result.Success.class);
        var prefs = (UserPreferencesResponse) ((Result.Success<?, ?>) result).value();
        assertThat(prefs.settings()).containsKey("customKey");
        assertThat(prefs.settings()).containsKey("nested");
    }

    @Test
    void getPreferences_userNotFound_returnsFailure() {
        when(userRepository.findSummaryActiveById(USER_ID)).thenReturn(Optional.empty());

        var result = getUseCase.execute(USER_ID);

        assertThat(((Result.Failure<?, AuthError>) result).error())
                .isInstanceOf(AuthError.UserNotFound.class);
    }
}
