package io.github.smiskinext.usermanagement.application.usecase;

import io.github.phunguy65.zms.shared.domain.Result;
import io.github.phunguy65.zms.shared.domain.valueobject.UserId;
import io.github.smiskinext.usermanagement.application.helper.UserPreferencesParser;
import io.github.smiskinext.usermanagement.application.response.UserPreferencesResponse;
import io.github.smiskinext.usermanagement.domain.AuthError;
import io.github.smiskinext.usermanagement.domain.port.UserRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetUserPreferencesUseCase {

    private final UserRepository userRepository;
    private final UserPreferencesParser preferencesParser;

    public GetUserPreferencesUseCase(
            UserRepository userRepository, UserPreferencesParser preferencesParser) {
        this.userRepository = userRepository;
        this.preferencesParser = preferencesParser;
    }

    @Transactional(readOnly = true)
    public Result<UserPreferencesResponse, AuthError> execute(UserId userId) {
        var summaryOpt = userRepository.findSummaryActiveById(userId);
        if (summaryOpt.isEmpty()) {
            return Result.failure(new AuthError.UserNotFound());
        }
        return Result.success(preferencesParser.parseAsResponse(
                Optional.ofNullable(summaryOpt.get().preferences())));
    }
}
