package io.github.smiskinext.usermanagement.application.usecase;

import io.github.phunguy65.zms.shared.domain.Result;
import io.github.phunguy65.zms.shared.domain.valueobject.UserId;
import io.github.smiskinext.usermanagement.application.response.DeleteAccountResponse;
import io.github.smiskinext.usermanagement.domain.AuthError;
import io.github.smiskinext.usermanagement.domain.PublishableEvent;
import io.github.smiskinext.usermanagement.domain.port.RefreshTokenRepository;
import io.github.smiskinext.usermanagement.domain.port.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeleteAccountUseCase {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final ApplicationEventPublisher eventPublisher;

    public DeleteAccountUseCase(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            ApplicationEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Result<DeleteAccountResponse, AuthError> execute(UserId userId) {
        var userOpt = userRepository.findById(userId);

        if (userOpt.isEmpty()) {
            return Result.failure(new AuthError.UserNotFound());
        }

        var user = userOpt.get();
        if (user.isDeleted()) {
            return Result.success(new DeleteAccountResponse(
                    user.getId().value(),
                    user.getEmail().value(),
                    user.getFullName().value(),
                    user.getDeletedAt().orElseThrow()));
        }

        user.delete();
        var saved = userRepository.save(user);
        refreshTokenRepository.revokeAllByUserId(userId);

        saved.getDomainEvents().stream()
                .filter(e -> e instanceof PublishableEvent)
                .map(e -> (PublishableEvent) e)
                .forEach(eventPublisher::publishEvent);
        saved.clearDomainEvents();

        return Result.success(new DeleteAccountResponse(
                saved.getId().value(),
                saved.getEmail().value(),
                saved.getFullName().value(),
                saved.getDeletedAt().orElseThrow()));
    }
}
