package io.github.smiskinext.meetingmanagement.application.usecase;

import io.github.smiskinext.meetingmanagement.application.response.ValidateInviteTokenResponse;
import io.github.smiskinext.meetingmanagement.application.service.InviteTokenService;
import io.github.smiskinext.meetingmanagement.application.service.InviteTokenService.ValidationResult;
import io.github.smiskinext.meetingmanagement.domain.MeetingError;
import io.github.smiskinext.meetingmanagement.domain.port.InviteTokenRepository;
import io.github.smiskinext.shared.domain.Result;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Validates a raw invite token string, marks it as USED on success, and returns
 * the meeting context needed for the caller to join.
 *
 * <p>This endpoint is public — no authentication is required.
 */
@Service
public class ValidateInviteTokenUseCase {

    private final InviteTokenService inviteTokenService;
    private final InviteTokenRepository inviteTokenRepository;

    public ValidateInviteTokenUseCase(
            InviteTokenService inviteTokenService, InviteTokenRepository inviteTokenRepository) {
        this.inviteTokenService = inviteTokenService;
        this.inviteTokenRepository = inviteTokenRepository;
    }

    @Transactional
    public Result<ValidateInviteTokenResponse, MeetingError> execute(String rawToken) {
        ValidationResult validationResult = inviteTokenService.validateToken(rawToken);

        if (validationResult instanceof ValidationResult.Invalid(String errorCode)) {
            return Result.failure(new MeetingError.InvalidInviteToken(errorCode));
        }

        ValidationResult.Valid valid = (ValidationResult.Valid) validationResult;

        String tokenHash = inviteTokenService.hashToken(rawToken);
        inviteTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
            Result<Void, MeetingError> markResult = token.markUsed();
            if (markResult instanceof Result.Success<?, ?>) {
                inviteTokenRepository.save(token);
            }
        });

        return Result.success(new ValidateInviteTokenResponse(
                valid.meetingId(), valid.shortCode(), !valid.requiresJoinRequest()));
    }
}
