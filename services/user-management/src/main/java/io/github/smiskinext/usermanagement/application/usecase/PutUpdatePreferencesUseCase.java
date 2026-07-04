package io.github.smiskinext.usermanagement.application.usecase;

import io.github.phunguy65.zms.shared.domain.Result;
import io.github.phunguy65.zms.shared.domain.valueobject.UserId;
import io.github.smiskinext.usermanagement.application.command.PutPreferencesCommand;
import io.github.smiskinext.usermanagement.application.helper.UserPreferencesSerializer;
import io.github.smiskinext.usermanagement.application.response.UserPreferencesResponse;
import io.github.smiskinext.usermanagement.domain.AuthError;
import io.github.smiskinext.usermanagement.domain.port.UserRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PutUpdatePreferencesUseCase {

    private static final Logger log = LoggerFactory.getLogger(PutUpdatePreferencesUseCase.class);

    private final UserRepository userRepository;
    private final UserPreferencesSerializer preferencesSerializer;

    public PutUpdatePreferencesUseCase(
            UserRepository userRepository, UserPreferencesSerializer preferencesSerializer) {
        this.userRepository = userRepository;
        this.preferencesSerializer = preferencesSerializer;
    }

    @Transactional
    public Result<UserPreferencesResponse, AuthError> execute(
            UserId userId, PutPreferencesCommand command) {
        var userOpt = userRepository.findActiveById(userId);
        if (userOpt.isEmpty()) {
            return Result.failure(new AuthError.UserNotFound());
        }

        Map<String, Object> replacement = new LinkedHashMap<>(command.settings());

        try {
            String json = preferencesSerializer.serialize(replacement);
            var user = userOpt.get();
            user.updatePreferences(json);
            userRepository.save(user);
            return Result.success(new UserPreferencesResponse(replacement));
        } catch (Exception e) {
            log.error("Failed to serialise preferences for user {}", userId, e);
            return Result.failure(new AuthError.PreferencesSerializationError());
        }
    }
}
