package io.github.smiskinext.usermanagement.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.smiskinext.shared.domain.Result;
import io.github.smiskinext.shared.domain.valueobject.Email;
import io.github.smiskinext.shared.domain.valueobject.UserId;
import io.github.smiskinext.usermanagement.application.command.PutUserCommand;
import io.github.smiskinext.usermanagement.application.helper.UserPreferencesParser;
import io.github.smiskinext.usermanagement.application.response.UserResponse;
import io.github.smiskinext.usermanagement.domain.AuthError;
import io.github.smiskinext.usermanagement.domain.event.UserUpdatedEvent;
import io.github.smiskinext.usermanagement.domain.model.User;
import io.github.smiskinext.usermanagement.domain.model.valueobject.FullName;
import io.github.smiskinext.usermanagement.domain.model.valueobject.Username;
import io.github.smiskinext.usermanagement.domain.port.UserRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class UpdateUserUseCaseTest {

    @Mock
    UserRepository userRepository;

    @Mock
    ApplicationEventPublisher eventPublisher;

    UpdateUserUseCase useCase;

    private static final UserId USER_ID = UserId.of(UuidCreator.getTimeOrderedEpoch());

    @BeforeEach
    void setUp() {
        useCase = new UpdateUserUseCase(
                userRepository, new UserPreferencesParser(new ObjectMapper()), eventPublisher);
    }

    private User buildUser(String avatarUrl, Username username) {
        return User.reconstitute(
                USER_ID,
                Email.of("alice@example.com"),
                null,
                FullName.of("Alice"),
                username,
                avatarUrl,
                null,
                "EMAIL",
                null,
                Instant.now(),
                Instant.now(),
                null);
    }

    @Test
    void execute_updatesAllFields() {
        var user = buildUser("https://old.com/avatar.png", Username.of("alice"));
        when(userRepository.findActiveById(USER_ID)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var cmd = new PutUserCommand("New Name", "newusername", "https://new.com/avatar.png");
        var result = useCase.execute(USER_ID, cmd);

        assertThat(result).isInstanceOf(Result.Success.class);
        var response = (UserResponse) ((Result.Success<?, ?>) result).value();
        assertThat(response.fullName()).isEqualTo("New Name");
        assertThat(response.username()).isEqualTo("newusername");
        assertThat(response.avatarUrl()).isEqualTo("https://new.com/avatar.png");
        verify(eventPublisher).publishEvent(any(UserUpdatedEvent.class));
    }

    @Test
    void execute_nullAvatarUrl_clearsAvatar() {
        var user = buildUser("https://example.com/old.png", Username.of("alice"));
        when(userRepository.findActiveById(USER_ID)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var cmd = new PutUserCommand("Alice", "alice", null);
        var result = useCase.execute(USER_ID, cmd);

        assertThat(result).isInstanceOf(Result.Success.class);
        var response = (UserResponse) ((Result.Success<?, ?>) result).value();
        assertThat(response.avatarUrl()).isNull();
    }

    @Test
    void execute_userNotFound_returnsFailure() {
        when(userRepository.findActiveById(USER_ID)).thenReturn(Optional.empty());

        var cmd = new PutUserCommand("Name", "username", null);
        var result = useCase.execute(USER_ID, cmd);

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<?, AuthError>) result).error())
                .isInstanceOf(AuthError.UserNotFound.class);
    }

    @Test
    void execute_duplicateUsername_returnsFailure() {
        var user = buildUser(null, Username.of("alice"));
        when(userRepository.findActiveById(USER_ID)).thenReturn(Optional.of(user));
        when(userRepository.existsActiveByUsername(any())).thenReturn(true);

        var cmd = new PutUserCommand("Alice", "taken_user", null);
        var result = useCase.execute(USER_ID, cmd);

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<?, AuthError>) result).error())
                .isInstanceOf(AuthError.UsernameAlreadyExists.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void execute_sameUsername_isIdempotent() {
        var user = buildUser(null, Username.of("alice_user"));
        when(userRepository.findActiveById(USER_ID)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var cmd = new PutUserCommand("Alice Updated", "alice_user", null);
        var result = useCase.execute(USER_ID, cmd);

        assertThat(result).isInstanceOf(Result.Success.class);
        // existsActiveByUsername should NOT be called (same username skips check)
        verify(userRepository, never()).existsActiveByUsername(any());
    }

    @Test
    void execute_publishesUserUpdatedEvent() {
        var user = buildUser(null, Username.of("alice"));
        when(userRepository.findActiveById(USER_ID)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var cmd = new PutUserCommand("Updated Name", "alice", "https://avatar.com/new.png");
        useCase.execute(USER_ID, cmd);

        var captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(UserUpdatedEvent.class);
        var event = (UserUpdatedEvent) captor.getValue();
        assertThat(event.fullName()).isEqualTo("Updated Name");
        assertThat(event.email()).isEqualTo("alice@example.com");
        assertThat(event.avatarUrl()).isEqualTo("https://avatar.com/new.png");
    }

    @Test
    void execute_newUsername_checksUniqueness() {
        var user = buildUser(null, Username.of("alice"));
        when(userRepository.findActiveById(USER_ID)).thenReturn(Optional.of(user));
        when(userRepository.existsActiveByUsername(Username.of("bob"))).thenReturn(false);
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var cmd = new PutUserCommand("Alice", "bob", null);
        var result = useCase.execute(USER_ID, cmd);

        assertThat(result).isInstanceOf(Result.Success.class);
        verify(userRepository).existsActiveByUsername(Username.of("bob"));
    }
}
