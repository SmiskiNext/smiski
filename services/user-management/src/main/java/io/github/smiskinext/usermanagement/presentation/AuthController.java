package io.github.smiskinext.usermanagement.presentation;

import io.github.phunguy65.zms.shared.domain.Result;
import io.github.phunguy65.zms.shared.infrastructure.web.JsendResponse;
import io.github.smiskinext.usermanagement.application.response.LoginResponse;
import io.github.smiskinext.usermanagement.application.response.RegisterResponse;
import io.github.smiskinext.usermanagement.application.response.VerifyOtpResponse;
import io.github.smiskinext.usermanagement.application.usecase.LoginUserUseCase;
import io.github.smiskinext.usermanagement.application.usecase.LoginWithGoogleUseCase;
import io.github.smiskinext.usermanagement.application.usecase.LogoutUserUseCase;
import io.github.smiskinext.usermanagement.application.usecase.RefreshTokenUseCase;
import io.github.smiskinext.usermanagement.application.usecase.RegisterUserUseCase;
import io.github.smiskinext.usermanagement.application.usecase.RequestPasswordResetUseCase;
import io.github.smiskinext.usermanagement.application.usecase.ResetPasswordUseCase;
import io.github.smiskinext.usermanagement.domain.AuthError;
import io.github.smiskinext.usermanagement.application.usecase.VerifyOtpUseCase;
import io.github.smiskinext.usermanagement.presentation.request.ForgotPasswordRequest;
import io.github.smiskinext.usermanagement.presentation.request.GoogleLoginRequest;
import io.github.smiskinext.usermanagement.presentation.request.LoginRequest;
import io.github.smiskinext.usermanagement.presentation.request.LogoutRequest;
import io.github.smiskinext.usermanagement.presentation.request.RefreshTokenRequest;
import io.github.smiskinext.usermanagement.presentation.request.RegisterRequest;
import io.github.smiskinext.usermanagement.presentation.request.ResetPasswordRequest;
import io.github.smiskinext.usermanagement.presentation.request.VerifyOtpRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "Auth", description = "Authentication and registration")
public class AuthController extends BaseController {

    private final RegisterUserUseCase registerUserUseCase;
    private final LoginUserUseCase loginUserUseCase;
    private final RefreshTokenUseCase refreshTokenUseCase;
    private final LogoutUserUseCase logoutUserUseCase;
    private final LoginWithGoogleUseCase loginWithGoogleUseCase;
    private final RequestPasswordResetUseCase requestPasswordResetUseCase;
    private final ResetPasswordUseCase resetPasswordUseCase;
    private final VerifyOtpUseCase
            verifyOtpUseCase;

    public AuthController(
            RegisterUserUseCase registerUserUseCase,
            LoginUserUseCase loginUserUseCase,
            RefreshTokenUseCase refreshTokenUseCase,
            LogoutUserUseCase logoutUserUseCase,
            LoginWithGoogleUseCase loginWithGoogleUseCase,
            RequestPasswordResetUseCase requestPasswordResetUseCase,
            ResetPasswordUseCase resetPasswordUseCase,
            VerifyOtpUseCase
                    verifyOtpUseCase) {
        this.registerUserUseCase = registerUserUseCase;
        this.loginUserUseCase = loginUserUseCase;
        this.refreshTokenUseCase = refreshTokenUseCase;
        this.logoutUserUseCase = logoutUserUseCase;
        this.loginWithGoogleUseCase = loginWithGoogleUseCase;
        this.requestPasswordResetUseCase = requestPasswordResetUseCase;
        this.resetPasswordUseCase = resetPasswordUseCase;
        this.verifyOtpUseCase = verifyOtpUseCase;
    }

    @Operation(summary = "Register a new user account")
    @PostMapping(value = "/{version}/auth/register", version = "1.0")
    public ResponseEntity<JsendResponse<RegisterResponse>> register(
            @Valid @RequestBody RegisterRequest request) {
        return switch (registerUserUseCase.execute(request.toCommand())) {
            case Result.Success<RegisterResponse, AuthError> s ->
                ResponseEntity.status(HttpStatus.CREATED).body(JsendResponse.success(s.value()));
            case Result.Failure<RegisterResponse, AuthError> f -> errorResponse(f.error());
        };
    }

    @Operation(summary = "Login with email and password")
    @PostMapping(value = "/{version}/auth/login", version = "1.0")
    public ResponseEntity<JsendResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        return switch (loginUserUseCase.execute(request.toCommand())) {
            case Result.Success<LoginResponse, AuthError> s ->
                ResponseEntity.ok(JsendResponse.success(s.value()));
            case Result.Failure<LoginResponse, AuthError> f -> errorResponse(f.error());
        };
    }

    @Operation(summary = "Refresh access token")
    @PostMapping(value = "/{version}/auth/refresh", version = "1.0")
    public ResponseEntity<JsendResponse<LoginResponse>> refresh(
            @Valid @RequestBody RefreshTokenRequest request) {
        return switch (refreshTokenUseCase.execute(request.toCommand())) {
            case Result.Success<LoginResponse, AuthError> s ->
                ResponseEntity.ok(JsendResponse.success(s.value()));
            case Result.Failure<LoginResponse, AuthError> f -> errorResponse(f.error());
        };
    }

    @Operation(summary = "Logout and revoke refresh token")
    @PostMapping(value = "/{version}/auth/logout", version = "1.0")
    public ResponseEntity<JsendResponse<Void>> logout(@Valid @RequestBody LogoutRequest request) {
        return switch (logoutUserUseCase.execute(request.toCommand())) {
            case Result.Success<Void, AuthError> _ -> ResponseEntity.ok(JsendResponse.success());
            case Result.Failure<Void, AuthError> f -> errorResponse(f.error());
        };
    }

    @Operation(summary = "Login or register with Google")
    @PostMapping(value = "/{version}/auth/google-login", version = "1.0")
    public ResponseEntity<JsendResponse<LoginResponse>> googleLogin(
            @Valid @RequestBody GoogleLoginRequest request) {
        return switch (loginWithGoogleUseCase.execute(request.toCommand())) {
            case Result.Success<LoginResponse, AuthError> s ->
                ResponseEntity.ok(JsendResponse.success(s.value()));
            case Result.Failure<LoginResponse, AuthError> f -> errorResponse(f.error());
        };
    }

    @Operation(summary = "Request password reset OTP")
    @PostMapping(value = "/{version}/auth/forgot-password", version = "1.0")
    public ResponseEntity<JsendResponse<Void>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request, HttpServletRequest httpRequest) {
        String ipAddress = getClientIpAddress(httpRequest);
        return switch (requestPasswordResetUseCase.execute(request.toCommand(ipAddress))) {
            case Result.Success<Void, AuthError> _ -> ResponseEntity.ok(JsendResponse.success());
            case Result.Failure<Void, AuthError> f -> errorResponse(f.error());
        };
    }

    @Operation(summary = "Reset password using OTP")
    @PostMapping(value = "/{version}/auth/reset-password", version = "1.0")
    public ResponseEntity<JsendResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        return switch (resetPasswordUseCase.execute(request.toCommand())) {
            case Result.Success<Void, AuthError> _ -> ResponseEntity.ok(JsendResponse.success());
            case Result.Failure<Void, AuthError> f -> errorResponse(f.error());
        };
    }

    @Operation(summary = "Verify OTP and get temporary token")
    @PostMapping(value = "/{version}/auth/verify-otp", version = "1.0")
    public ResponseEntity<JsendResponse<VerifyOtpResponse>> verifyOtp(
            @Valid @RequestBody VerifyOtpRequest request) {
        return switch (verifyOtpUseCase.execute(request.toCommand())) {
            case Result.Success<VerifyOtpResponse, AuthError> s ->
                ResponseEntity.ok(JsendResponse.success(s.value()));
            case Result.Failure<VerifyOtpResponse, AuthError> f -> errorResponse(f.error());
        };
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
