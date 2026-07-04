package io.github.smiskinext.usermanagement.application.usecase;

import io.github.phunguy65.zms.shared.domain.Result;
import io.github.phunguy65.zms.shared.domain.valueobject.Email;
import io.github.smiskinext.usermanagement.application.command.RegisterCommand;
import io.github.smiskinext.usermanagement.application.response.RegisterResponse;
import io.github.smiskinext.usermanagement.domain.AuthError;
import io.github.smiskinext.usermanagement.domain.PublishableEvent;
import io.github.smiskinext.usermanagement.domain.model.User;
import io.github.smiskinext.usermanagement.domain.model.valueobject.FullName;
import io.github.smiskinext.usermanagement.domain.model.valueobject.Username;
import io.github.smiskinext.usermanagement.domain.port.PasswordHasher;
import io.github.smiskinext.usermanagement.domain.port.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegisterUserUseCase {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final ApplicationEventPublisher eventPublisher;

    public RegisterUserUseCase(
            UserRepository userRepository,
            PasswordHasher passwordHasher,
            ApplicationEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Result<RegisterResponse, AuthError> execute(RegisterCommand command) {
        Email email = Email.of(command.email());

        if (userRepository.existsActiveByEmail(email)) {
            return Result.failure(new AuthError.EmailAlreadyExists());
        }

        Username username = Username.of(command.username());

        if (userRepository.existsActiveByUsername(username)) {
            return Result.failure(new AuthError.UsernameAlreadyExists());
        }

        var hashedPassword = passwordHasher.hash(command.password());
        var fullName = FullName.of(command.fullName());
        var user = User.register(email, hashedPassword, fullName, username);
        var saved = userRepository.save(user);

        saved.getDomainEvents().stream()
                .filter(e -> e instanceof PublishableEvent)
                .map(e -> (PublishableEvent) e)
                .forEach(eventPublisher::publishEvent);
        saved.clearDomainEvents();

        return Result.success(new RegisterResponse(
                saved.getId().value(),
                saved.getEmail().value(),
                saved.getFullName().value(),
                saved.getUsername().map(Username::value).orElse(null)));
    }
}
