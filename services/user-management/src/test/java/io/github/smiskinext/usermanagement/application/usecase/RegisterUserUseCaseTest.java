package io.github.smiskinext.usermanagement.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.github.phunguy65.zms.shared.domain.Result;
import io.github.phunguy65.zms.shared.domain.valueobject.Email;
import io.github.smiskinext.usermanagement.application.command.RegisterCommand;
import io.github.smiskinext.usermanagement.domain.AuthError;
import io.github.smiskinext.usermanagement.domain.model.User;
import io.github.smiskinext.usermanagement.domain.model.valueobject.HashedPassword;
import io.github.smiskinext.usermanagement.domain.port.PasswordHasher;
import io.github.smiskinext.usermanagement.domain.port.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class RegisterUserUseCaseTest {

    @Mock
    UserRepository userRepository;

    @Mock
    PasswordHasher passwordHasher;

    @Mock
    ApplicationEventPublisher eventPublisher;

    @InjectMocks
    RegisterUserUseCase useCase;

    @Test
    void successfulRegistration() {
        var cmd = new RegisterCommand(
                "alice@example.com", "password123", "Alice Smith", "alice_smith");
        when(userRepository.existsActiveByEmail(any())).thenReturn(false);
        when(userRepository.existsActiveByUsername(any())).thenReturn(false);
        when(passwordHasher.hash("password123")).thenReturn(HashedPassword.of("$argon2id$hash"));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = useCase.execute(cmd);

        assertThat(result).isInstanceOf(Result.Success.class);
        var success = (Result.Success<?, ?>) result;
        assertThat(success.value()).isNotNull();
        verify(passwordHasher).hash("password123");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void duplicateEmailReturnsFailure() {
        var cmd = new RegisterCommand(
                "alice@example.com", "password123", "Alice Smith", "alice_smith");
        when(userRepository.existsActiveByEmail(Email.of("alice@example.com"))).thenReturn(true);

        var result = useCase.execute(cmd);

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<?, AuthError>) result).error())
                .isInstanceOf(AuthError.EmailAlreadyExists.class);
        verify(passwordHasher, never()).hash(any());
    }

    @Test
    void duplicateUsernameReturnsFailure() {
        var cmd = new RegisterCommand(
                "alice@example.com", "password123", "Alice Smith", "taken_user");
        when(userRepository.existsActiveByEmail(any())).thenReturn(false);
        when(userRepository.existsActiveByUsername(any())).thenReturn(true);

        var result = useCase.execute(cmd);

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<?, AuthError>) result).error())
                .isInstanceOf(AuthError.UsernameAlreadyExists.class);
        verify(passwordHasher, never()).hash(any());
    }

    @Test
    void passwordHashingIsCalled() {
        var cmd = new RegisterCommand("bob@example.com", "securepass", "Bob", "bob_user");
        when(userRepository.existsActiveByEmail(any())).thenReturn(false);
        when(userRepository.existsActiveByUsername(any())).thenReturn(false);
        when(passwordHasher.hash("securepass")).thenReturn(HashedPassword.of("$argon2id$xyz"));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(cmd);

        verify(passwordHasher, times(1)).hash("securepass");
    }
}
