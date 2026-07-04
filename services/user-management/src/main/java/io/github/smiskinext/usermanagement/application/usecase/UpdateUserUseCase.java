package io.github.smiskinext.usermanagement.application.usecase;

import io.github.phunguy65.zms.shared.domain.Result;
import io.github.phunguy65.zms.shared.domain.valueobject.UserId;
import io.github.smiskinext.usermanagement.application.command.PutUserCommand;
import io.github.smiskinext.usermanagement.application.helper.UserPreferencesParser;
import io.github.smiskinext.usermanagement.application.response.UserResponse;
import io.github.smiskinext.usermanagement.domain.AuthError;
import io.github.smiskinext.usermanagement.domain.PublishableEvent;
import io.github.smiskinext.usermanagement.domain.model.AvatarUpdate;
import io.github.smiskinext.usermanagement.domain.model.User;
import io.github.smiskinext.usermanagement.domain.model.valueobject.FullName;
import io.github.smiskinext.usermanagement.domain.model.valueobject.Username;
import io.github.smiskinext.usermanagement.domain.port.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use case for replacing a user's profile via PUT.
 *
 * <p>This use case expects all profile fields to be provided. A {@code null} value for
 * {@code avatarUrl} clears the avatar.
 */
@Service
public class UpdateUserUseCase {

    private final UserRepository userRepository;
    private final UserPreferencesParser preferencesParser;
    private final ApplicationEventPublisher eventPublisher;

    public UpdateUserUseCase(
            UserRepository userRepository,
            UserPreferencesParser preferencesParser,
            ApplicationEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.preferencesParser = preferencesParser;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Result<UserResponse, AuthError> execute(UserId userId, PutUserCommand command) {
        var userOpt = userRepository.findActiveById(userId);
        if (userOpt.isEmpty()) {
            return Result.failure(new AuthError.UserNotFound());
        }
        var user = userOpt.get();

        Username newUsername = Username.of(command.username());
        boolean isSameAsCurrent = user.getUsername()
                .map(u -> u.value().equals(newUsername.value()))
                .orElse(false);
        if (!isSameAsCurrent && userRepository.existsActiveByUsername(newUsername)) {
            return Result.failure(new AuthError.UsernameAlreadyExists());
        }

        AvatarUpdate avatarUpdate = command.avatarUrl() != null
                ? new AvatarUpdate.Set(command.avatarUrl())
                : new AvatarUpdate.Clear();

        user.replaceProfile(FullName.of(command.fullName()), avatarUpdate, newUsername);

        var saved = userRepository.save(user);

        saved.getDomainEvents().stream()
                .filter(e -> e instanceof PublishableEvent)
                .map(e -> (PublishableEvent) e)
                .forEach(eventPublisher::publishEvent);
        saved.clearDomainEvents();

        return Result.success(toResponse(saved));
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId().value(),
                user.getEmail().value(),
                user.getFullName().value(),
                user.getUsername().map(Username::value).orElse(null),
                user.getAvatarUrl().orElse(null),
                user.getAuthProvider(),
                preferencesParser.parseAsResponse(user.getPreferences()),
                user.getCreatedAt(),
                user.getUpdatedAt());
    }
}
