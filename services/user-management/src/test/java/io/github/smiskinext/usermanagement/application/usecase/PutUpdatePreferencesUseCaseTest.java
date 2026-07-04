package io.github.smiskinext.usermanagement.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.phunguy65.zms.shared.domain.Result;
import io.github.phunguy65.zms.shared.domain.valueobject.Email;
import io.github.phunguy65.zms.shared.domain.valueobject.UserId;
import io.github.smiskinext.usermanagement.application.command.PutPreferencesCommand;
import io.github.smiskinext.usermanagement.application.helper.UserPreferencesSerializer;
import io.github.smiskinext.usermanagement.application.response.UserPreferencesResponse;
import io.github.smiskinext.usermanagement.domain.AuthError;
import io.github.smiskinext.usermanagement.domain.model.User;
import io.github.smiskinext.usermanagement.domain.model.valueobject.FullName;
import io.github.smiskinext.usermanagement.domain.port.UserRepository;
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
class PutUpdatePreferencesUseCaseTest {

    @Mock
    UserRepository userRepository;

    PutUpdatePreferencesUseCase useCase;

    private static final UserId USER_ID = UserId.of(UuidCreator.getTimeOrderedEpoch());

    @BeforeEach
    void setUp() {
        useCase = new PutUpdatePreferencesUseCase(
                userRepository, new UserPreferencesSerializer(new ObjectMapper()));
    }

    private User buildUser(String prefsJson) {
        return User.reconstitute(
                USER_ID,
                Email.of("alice@example.com"),
                null,
                FullName.of("Alice"),
                null,
                null,
                null,
                "EMAIL",
                prefsJson,
                Instant.now(),
                Instant.now(),
                null);
    }

    @Test
    void execute_replacesExistingPreferences() {
        var user = buildUser("{\"theme\":\"dark\",\"fontSize\":14}");
        when(userRepository.findActiveById(USER_ID)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = useCase.execute(USER_ID, new PutPreferencesCommand(Map.of("lang", "vi")));

        assertThat(result).isInstanceOf(Result.Success.class);
        var prefs = (UserPreferencesResponse) ((Result.Success<?, ?>) result).value();
        assertThat(prefs.settings()).containsExactlyEntriesOf(Map.of("lang", "vi"));
        assertThat(user.getPreferences()).contains("{\"lang\":\"vi\"}");
    }

    @Test
    void execute_emptyObject_clearsStoredPreferences() {
        var user = buildUser("{\"theme\":\"dark\"}");
        when(userRepository.findActiveById(USER_ID)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = useCase.execute(USER_ID, new PutPreferencesCommand(Map.of()));

        assertThat(result).isInstanceOf(Result.Success.class);
        var prefs = (UserPreferencesResponse) ((Result.Success<?, ?>) result).value();
        assertThat(prefs.settings()).isEmpty();
        assertThat(user.getPreferences()).isEmpty();
    }

    @Test
    void execute_arbitraryNestedKeys_areStoredAsIs() {
        var user = buildUser(null);
        when(userRepository.findActiveById(USER_ID)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var replacement = Map.<String, Object>of(
                "customKey", "customValue", "nested", Map.of("a", 1), "flag", true);

        var result = useCase.execute(USER_ID, new PutPreferencesCommand(replacement));

        assertThat(result).isInstanceOf(Result.Success.class);
        var prefs = (UserPreferencesResponse) ((Result.Success<?, ?>) result).value();
        assertThat(prefs.settings()).containsExactlyEntriesOf(replacement);
    }

    @Test
    void execute_preservesNullValuesInsidePreferences() {
        var user = buildUser(null);
        when(userRepository.findActiveById(USER_ID)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> replacement = new java.util.LinkedHashMap<>();
        replacement.put("theme", "dark");
        replacement.put("customConfig", null);
        replacement.put("nested", new java.util.LinkedHashMap<>(Map.of("inner", "value")));
        ((Map<String, Object>) replacement.get("nested")).put("nullable", null);

        var result = useCase.execute(USER_ID, new PutPreferencesCommand(replacement));

        assertThat(result).isInstanceOf(Result.Success.class);
        var prefs = (UserPreferencesResponse) ((Result.Success<?, ?>) result).value();
        assertThat(prefs.settings()).containsEntry("customConfig", null);
        @SuppressWarnings("unchecked")
        var nested = (Map<String, Object>) prefs.settings().get("nested");
        assertThat(nested).containsEntry("nullable", null);
    }

    @Test
    void execute_serializationFailure_returnsFailure() throws Exception {
        var user = buildUser(null);
        when(userRepository.findActiveById(USER_ID)).thenReturn(Optional.of(user));

        var serializer = org.mockito.Mockito.mock(UserPreferencesSerializer.class);
        doThrow(new RuntimeException("boom")).when(serializer).serialize(any());
        useCase = new PutUpdatePreferencesUseCase(userRepository, serializer);

        var result = useCase.execute(USER_ID, new PutPreferencesCommand(Map.of("theme", "dark")));

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<?, AuthError>) result).error())
                .isInstanceOf(AuthError.PreferencesSerializationError.class);
    }

    @Test
    void execute_userNotFound_returnsFailure() {
        when(userRepository.findActiveById(USER_ID)).thenReturn(Optional.empty());

        var result = useCase.execute(USER_ID, new PutPreferencesCommand(Map.of()));

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<?, AuthError>) result).error())
                .isInstanceOf(AuthError.UserNotFound.class);
    }
}
