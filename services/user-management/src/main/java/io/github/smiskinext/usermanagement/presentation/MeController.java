package io.github.smiskinext.usermanagement.presentation;

import io.github.phunguy65.zms.shared.domain.Result;
import io.github.phunguy65.zms.shared.domain.valueobject.UserId;
import io.github.phunguy65.zms.shared.infrastructure.web.JsendResponse;
import io.github.smiskinext.usermanagement.application.command.PutPreferencesCommand;
import io.github.smiskinext.usermanagement.application.response.DeleteAccountResponse;
import io.github.smiskinext.usermanagement.application.response.UserPreferencesResponse;
import io.github.smiskinext.usermanagement.application.response.UserResponse;
import io.github.smiskinext.usermanagement.application.usecase.DeleteAccountUseCase;
import io.github.smiskinext.usermanagement.application.usecase.GetUserPreferencesUseCase;
import io.github.smiskinext.usermanagement.application.usecase.GetUserUseCase;
import io.github.smiskinext.usermanagement.application.usecase.PutUpdatePreferencesUseCase;
import io.github.smiskinext.usermanagement.application.usecase.UpdateUserUseCase;
import io.github.smiskinext.usermanagement.domain.AuthError;
import io.github.smiskinext.usermanagement.presentation.request.PutUserRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "Me", description = "Current user profile and preferences")
public class MeController extends BaseController {

    private final GetUserUseCase getUserUseCase;
    private final UpdateUserUseCase updateUserUseCase;
    private final GetUserPreferencesUseCase getPreferencesUseCase;
    private final PutUpdatePreferencesUseCase putUpdatePreferencesUseCase;
    private final DeleteAccountUseCase deleteAccountUseCase;

    public MeController(
            GetUserUseCase getUserUseCase,
            UpdateUserUseCase updateUserUseCase,
            GetUserPreferencesUseCase getPreferencesUseCase,
            PutUpdatePreferencesUseCase putUpdatePreferencesUseCase,
            DeleteAccountUseCase deleteAccountUseCase) {
        this.getUserUseCase = getUserUseCase;
        this.updateUserUseCase = updateUserUseCase;
        this.getPreferencesUseCase = getPreferencesUseCase;
        this.putUpdatePreferencesUseCase = putUpdatePreferencesUseCase;
        this.deleteAccountUseCase = deleteAccountUseCase;
    }

    @Operation(summary = "Get current user profile")
    @GetMapping(value = "/{version}/me", version = "1.0")
    public ResponseEntity<JsendResponse<UserResponse>> getMe(Authentication auth) {
        UserId userId = extractUserId(auth);
        if (userId == null) {
            return errorResponse(new AuthError.InvalidCredentials());
        }
        return switch (getUserUseCase.execute(userId)) {
            case Result.Success<UserResponse, AuthError> s ->
                ResponseEntity.ok(JsendResponse.success(s.value()));
            case Result.Failure<UserResponse, AuthError> f -> errorResponse(f.error());
        };
    }

    @Operation(summary = "Replace current user profile")
    @PutMapping(value = "/{version}/me", version = "1.0")
    public ResponseEntity<JsendResponse<UserResponse>> putMe(
            @Valid @RequestBody PutUserRequest dto, Authentication auth) {
        UserId userId = extractUserId(auth);
        if (userId == null) {
            return errorResponse(new AuthError.InvalidCredentials());
        }
        return switch (updateUserUseCase.execute(userId, dto.toCommand())) {
            case Result.Success<UserResponse, AuthError> s ->
                ResponseEntity.ok(JsendResponse.success(s.value()));
            case Result.Failure<UserResponse, AuthError> f -> errorResponse(f.error());
        };
    }

    @Operation(summary = "Get current user preferences")
    @GetMapping(value = "/{version}/me/preferences", version = "1.0")
    public ResponseEntity<JsendResponse<UserPreferencesResponse>> getPreferences(
            Authentication auth) {
        UserId userId = extractUserId(auth);
        if (userId == null) {
            return errorResponse(new AuthError.InvalidCredentials());
        }
        return switch (getPreferencesUseCase.execute(userId)) {
            case Result.Success<UserPreferencesResponse, AuthError> s ->
                ResponseEntity.ok(JsendResponse.success(s.value()));
            case Result.Failure<UserPreferencesResponse, AuthError> f -> errorResponse(f.error());
        };
    }

    @Operation(summary = "Replace current user preferences")
    @PutMapping(value = "/{version}/me/preferences", version = "1.0")
    public ResponseEntity<JsendResponse<UserPreferencesResponse>> putPreferences(
            @RequestBody(required = false) Map<String, Object> settings, Authentication auth) {
        UserId userId = extractUserId(auth);
        if (userId == null) {
            return errorResponse(new AuthError.InvalidCredentials());
        }
        if (settings == null) {
            return validationErrorResponse("body", "Request body must not be null");
        }
        return switch (putUpdatePreferencesUseCase.execute(
                userId, new PutPreferencesCommand(settings))) {
            case Result.Success<UserPreferencesResponse, AuthError> s ->
                ResponseEntity.ok(JsendResponse.success(s.value()));
            case Result.Failure<UserPreferencesResponse, AuthError> f -> errorResponse(f.error());
        };
    }

    @Operation(summary = "Delete current user account")
    @DeleteMapping(value = "/{version}/me", version = "1.0")
    public ResponseEntity<JsendResponse<DeleteAccountResponse>> deleteMe(Authentication auth) {
        UserId userId = extractUserId(auth);
        if (userId == null) {
            return errorResponse(new AuthError.InvalidCredentials());
        }
        return switch (deleteAccountUseCase.execute(userId)) {
            case Result.Success<DeleteAccountResponse, AuthError> s ->
                ResponseEntity.ok(JsendResponse.success(s.value()));
            case Result.Failure<DeleteAccountResponse, AuthError> f -> errorResponse(f.error());
        };
    }

    private UserId extractUserId(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof String principalId)) {
            return null;
        }
        try {
            return UserId.of(UUID.fromString(principalId));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
