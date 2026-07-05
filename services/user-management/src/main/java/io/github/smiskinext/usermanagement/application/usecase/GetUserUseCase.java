package io.github.smiskinext.usermanagement.application.usecase;

import io.github.smiskinext.shared.domain.Result;
import io.github.smiskinext.shared.domain.valueobject.UserId;
import io.github.smiskinext.usermanagement.application.helper.UserPreferencesParser;
import io.github.smiskinext.usermanagement.application.response.UserResponse;
import io.github.smiskinext.usermanagement.domain.AuthError;
import io.github.smiskinext.usermanagement.domain.port.UserRepository;
import io.github.smiskinext.usermanagement.domain.projection.UserSummary;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetUserUseCase {

    private final UserRepository userRepository;
    private final UserPreferencesParser preferencesParser;

    public GetUserUseCase(UserRepository userRepository, UserPreferencesParser preferencesParser) {
        this.userRepository = userRepository;
        this.preferencesParser = preferencesParser;
    }

    @Transactional(readOnly = true)
    public Result<UserResponse, AuthError> execute(UserId userId) {
        return userRepository
                .findSummaryActiveById(userId)
                .map(summary -> Result.<UserResponse, AuthError>success(toResponse(summary)))
                .orElseGet(() -> Result.failure(new AuthError.UserNotFound()));
    }

    private UserResponse toResponse(UserSummary summary) {
        return new UserResponse(
                summary.id(),
                summary.email(),
                summary.fullName(),
                summary.username(),
                summary.avatarUrl(),
                summary.authProvider(),
                preferencesParser.parseAsResponse(Optional.ofNullable(summary.preferences())),
                summary.createdAt(),
                summary.updatedAt());
    }
}
