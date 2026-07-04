package io.github.smiskinext.usermanagement.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import io.github.smiskinext.usermanagement.config.TestcontainersConfiguration;
import io.github.smiskinext.usermanagement.domain.model.PasswordResetToken;
import io.github.smiskinext.usermanagement.domain.port.OtpGenerator;
import io.github.smiskinext.usermanagement.domain.port.OtpHasher;
import io.github.smiskinext.usermanagement.domain.port.PasswordResetTokenRepository;
import io.github.smiskinext.usermanagement.domain.port.UserRepository;
import io.github.smiskinext.usermanagement.infrastructure.security.FirebaseTokenVerifier;
import io.github.smiskinext.usermanagement.presentation.request.ForgotPasswordRequest;
import io.github.smiskinext.usermanagement.presentation.request.LoginRequest;
import io.github.smiskinext.usermanagement.presentation.request.LogoutRequest;
import io.github.smiskinext.usermanagement.presentation.request.RefreshTokenRequest;
import io.github.smiskinext.usermanagement.presentation.request.RegisterRequest;
import io.github.smiskinext.usermanagement.presentation.request.ResetPasswordRequest;
import java.time.Instant;

import io.github.smiskinext.usermanagement.domain.model.valueobject.PasswordResetTokenId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AuthControllerIntegrationTest {

    @Autowired
    WebApplicationContext wac;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordResetTokenRepository tokenRepository;

    @Autowired
    OtpGenerator otpGenerator;

    @Autowired
    OtpHasher otpHasher;

    /** Mock Firebase so the context starts without real credentials. */
    @MockitoBean
    FirebaseTokenVerifier firebaseTokenVerifier;

    ObjectMapper objectMapper = new ObjectMapper();

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(wac)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    void fullRegisterLoginRefreshLogoutFlow() throws Exception {
        // 1. Register
        var registerRequest = new RegisterRequest(
                "integration@example.com", "password123", "Integration User", "integration_user");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.email").value("integration@example.com"));

        // 2. Login
        var loginRequest = new LoginRequest("integration@example.com", "password123");
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andReturn();

        var loginBody = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String accessToken = loginBody.at("/data/accessToken").asText();
        String refreshToken = loginBody.at("/data/refreshToken").asText();

        // 3. Refresh
        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RefreshTokenRequest(refreshToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andReturn();

        var refreshBody = objectMapper.readTree(refreshResult.getResponse().getContentAsString());
        String newRefreshToken = refreshBody.at("/data/refreshToken").asText();
        String newAccessToken = refreshBody.at("/data/accessToken").asText();

        // 4. Logout (requires valid access token)
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + newAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LogoutRequest(newRefreshToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));

        // 5. Reuse detection: old refresh token should be rejected
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RefreshTokenRequest(refreshToken))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void duplicateRegistrationReturns409() throws Exception {
        var request = new RegisterRequest("dup@example.com", "password123", "Dup User", "dup_user");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("fail"))
                .andExpect(jsonPath("$.data.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void invalidLoginReturns401() throws Exception {
        var request = new LoginRequest("nobody@example.com", "wrongpass");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.data.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void registerWithBlankFieldsReturns400WithViolations() throws Exception {
        // All fields blank — triggers @NotBlank on email, password, fullName
        var body = "{\"email\":\"\",\"password\":\"\",\"fullName\":\"\"}";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("fail"))
                .andExpect(jsonPath("$.data.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data.errors", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.data.errors[0].field").isNotEmpty())
                .andExpect(jsonPath("$.data.errors[0].message").isNotEmpty())
                .andExpect(jsonPath("$.data.errors[0].code").isNotEmpty());
    }

    @Test
    void registerWithInvalidEmailReturns400WithEmailViolation() throws Exception {
        var body =
                "{\"email\":\"not-an-email\",\"password\":\"password123\",\"fullName\":\"Test User\",\"username\":\"testuser\"}";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("fail"))
                .andExpect(jsonPath("$.data.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data.errors", hasSize(1)))
                .andExpect(jsonPath("$.data.errors[0].field").value("email"))
                .andExpect(jsonPath("$.data.errors[0].code").value("INVALID_FORMAT"));
    }

    @Test
    void registerWithShortPasswordReturns400WithTooShortViolation() throws Exception {
        var body =
                "{\"email\":\"valid@example.com\",\"password\":\"short\",\"fullName\":\"Test User\",\"username\":\"testuser\"}";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("fail"))
                .andExpect(jsonPath("$.data.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data.errors", hasSize(1)))
                .andExpect(jsonPath("$.data.errors[0].field").value("password"))
                .andExpect(jsonPath("$.data.errors[0].code").value("TOO_SHORT"));
    }

    @Test
    void loginWithBlankFieldsReturns400WithViolations() throws Exception {
        var body = "{\"email\":\"\",\"password\":\"\"}";
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("fail"))
                .andExpect(jsonPath("$.data.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data.errors", hasSize(greaterThan(0))));
    }

    @Test
    void deleteAccount_validJwt_returns204() throws Exception {
        // Register + login to get a JWT
        var email = "delete-me@example.com";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(
                                email, "password123", "Delete Me", "delete_me_user"))))
                .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest(email, "password123"))))
                .andExpect(status().isOk())
                .andReturn();

        String accessToken = objectMapper
                .readTree(loginResult.getResponse().getContentAsString())
                .at("/data/accessToken")
                .asText();

        // Delete account
        mockMvc.perform(delete("/api/v1/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    @Test
    void deleteAccount_deletedUserJwt_returns401() throws Exception {
        // Register + login + delete + try to use old JWT
        var email = "deleted-jwt@example.com";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(
                                email, "password123", "Deleted JWT", "deleted_jwt_user"))))
                .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest(email, "password123"))))
                .andExpect(status().isOk())
                .andReturn();

        String accessToken = objectMapper
                .readTree(loginResult.getResponse().getContentAsString())
                .at("/data/accessToken")
                .asString();

        // Delete account
        mockMvc.perform(delete("/api/v1/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        // Subsequent request with same JWT should be rejected (filter checks deletedAt)
        mockMvc.perform(delete("/api/v1/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginWithDeletedUser_returns401UserDeleted() throws Exception {
        var email = "deleted-login@example.com";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(
                                email, "password123", "Deleted Login", "deleted_login_user"))))
                .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest(email, "password123"))))
                .andExpect(status().isOk())
                .andReturn();

        String accessToken = objectMapper
                .readTree(loginResult.getResponse().getContentAsString())
                .at("/data/accessToken")
                .asString();

        mockMvc.perform(delete("/api/v1/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        // Login attempt after deletion should return 401 USER_DELETED
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest(email, "password123"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.data.code").value("USER_DELETED"));
    }

    @Test
    void registerWithPreviouslyDeletedEmail_succeeds() throws Exception {
        var email = "reuse-email@example.com";

        // Register
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(
                                email, "password123", "Original", "original_user"))))
                .andExpect(status().isCreated());

        // Login + delete
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest(email, "password123"))))
                .andExpect(status().isOk())
                .andReturn();

        String accessToken = objectMapper
                .readTree(loginResult.getResponse().getContentAsString())
                .at("/data/accessToken")
                .asString();

        mockMvc.perform(delete("/api/v1/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        // Re-register with same email should succeed (201)
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(
                                email, "newpassword123", "New User", "new_user_reuse"))))
                .andExpect(status().isCreated());
    }

    @Test
    void registerWithMissingUsernameReturns400() throws Exception {
        var body =
                "{\"email\":\"missing-username@example.com\",\"password\":\"password123\",\"fullName\":\"Test User\"}";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.code").value("VALIDATION_ERROR"));
    }

    @Test
    void registerWithDuplicateUsernameReturns409() throws Exception {
        String username = "dupuser_" + System.nanoTime() % 100000;
        // First registration
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(
                                "first-" + System.nanoTime() + "@example.com",
                                "password123",
                                "First",
                                username))))
                .andExpect(status().isCreated());

        // Second registration with same username but different email
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(
                                "second-" + System.nanoTime() + "@example.com",
                                "password123",
                                "Second",
                                username))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.data.code").value("USERNAME_ALREADY_EXISTS"));
    }

    @Test
    void registerSuccess_responseIncludesUsername() throws Exception {
        String username = "testuser_" + System.nanoTime() % 100000;
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(
                                "withusername-" + System.nanoTime() + "@example.com",
                                "password123",
                                "Test User",
                                username))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.username").value(username));
    }

    // ==================== Password Reset Tests ====================

    @Nested
    class ForgotPasswordEndpoint {

        @Test
        void validEmailReturns200WithSuccess() throws Exception {
            String email = "forgot-" + System.nanoTime() + "@example.com";
            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new RegisterRequest(
                                    email,
                                    "password123",
                                    "Test User",
                                    "forgot_user_" + System.nanoTime() % 100000))))
                    .andExpect(status().isCreated());

            mockMvc.perform(post("/api/v1/auth/forgot-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new ForgotPasswordRequest(email, null))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("success"));
        }

        @Test
        void nonExistentEmailReturns200ToPreventEnumeration() throws Exception {
            mockMvc.perform(post("/api/v1/auth/forgot-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new ForgotPasswordRequest(
                                    "nobody-" + System.nanoTime() + "@example.com", null))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("success"));
        }

        @Test
        void invalidEmailFormatReturns400() throws Exception {
            mockMvc.perform(post("/api/v1/auth/forgot-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"not-an-email\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value("fail"))
                    .andExpect(jsonPath("$.data.code").value("VALIDATION_ERROR"));
        }

        @Test
        void blankEmailReturns400() throws Exception {
            mockMvc.perform(post("/api/v1/auth/forgot-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.data.code").value("VALIDATION_ERROR"));
        }
    }

    @Nested
    class ResetPasswordEndpoint {

        @Test
        void validOtpAndPasswordReturns200() throws Exception {
            // Register user
            String email = "reset-" + System.nanoTime() + "@example.com";
            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new RegisterRequest(
                                    email,
                                    "oldpassword123",
                                    "Test User",
                                    "reset_user_" + System.nanoTime() % 100000))))
                    .andExpect(status().isCreated());

            // Create a valid token directly in DB for testing
            var user = userRepository
                    .findActiveByEmail(
                            io.github.phunguy65.zms.shared.domain.valueobject.Email.of(email))
                    .orElseThrow();
            String otp = "123456";
            String otpHash = otpHasher.hash(otp);
            PasswordResetToken token = PasswordResetToken.issue(
                    user.getId(), otpHash, Instant.now().plusSeconds(900));
            tokenRepository.save(token);

            // Reset password
            mockMvc.perform(post("/api/v1/auth/reset-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new ResetPasswordRequest(email, otp, null, "newpassword123"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("success"));

            // Verify new password works
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new LoginRequest(email, "newpassword123"))))
                    .andExpect(status().isOk());
        }

        @Test
        void invalidOtpReturns400() throws Exception {
            // Register user
            String email = "invalid-otp-" + System.nanoTime() + "@example.com";
            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new RegisterRequest(
                                    email,
                                    "password123",
                                    "Test User",
                                    "inv_otp_user_" + System.nanoTime() % 100000))))
                    .andExpect(status().isCreated());

            // Create a valid token with known OTP
            var user = userRepository
                    .findActiveByEmail(
                            io.github.phunguy65.zms.shared.domain.valueobject.Email.of(email))
                    .orElseThrow();
            String otpHash = otpHasher.hash("123456");
            PasswordResetToken token = PasswordResetToken.issue(
                    user.getId(), otpHash, Instant.now().plusSeconds(900));
            tokenRepository.save(token);

            // Try with wrong OTP
            mockMvc.perform(post("/api/v1/auth/reset-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new ResetPasswordRequest(
                                    email, "999999", null, "newpassword123"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.data.code").value("OTP_INVALID"));
        }

        @Test
        void expiredOtpReturns400() throws Exception {
            // Register user
            String email = "expired-otp-" + System.nanoTime() + "@example.com";
            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new RegisterRequest(
                                    email,
                                    "password123",
                                    "Test User",
                                    "exp_otp_user_" + System.nanoTime() % 100000))))
                    .andExpect(status().isCreated());

            // Create an expired token
            var user = userRepository
                    .findActiveByEmail(
                            io.github.phunguy65.zms.shared.domain.valueobject.Email.of(email))
                    .orElseThrow();
            String otp = "123456";
            String otpHash = otpHasher.hash(otp);
            PasswordResetToken token = PasswordResetToken.issue(
                    user.getId(), otpHash, Instant.now().minusSeconds(60)); // Already expired
            tokenRepository.save(token);

            // Try reset with expired OTP
            mockMvc.perform(post("/api/v1/auth/reset-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new ResetPasswordRequest(email, otp, null, "newpassword123"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.data.code").value("OTP_EXPIRED"));
        }

        @Test
        void shortPasswordReturns400ValidationError() throws Exception {
            mockMvc.perform(
                            post("/api/v1/auth/reset-password")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            "{\"email\":\"test@example.com\",\"otp\":\"123456\",\"newPassword\":\"short\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.data.code").value("VALIDATION_ERROR"));
        }

        @Test
        void invalidOtpFormatReturns400() throws Exception {
            mockMvc.perform(
                            post("/api/v1/auth/reset-password")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            "{\"email\":\"test@example.com\",\"otp\":\"12345\",\"newPassword\":\"password123\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.data.code").value("VALIDATION_ERROR"));
        }

        @Test
        void passwordResetRevokesAllRefreshTokens() throws Exception {
            // Register and login to get a refresh token
            String email = "revoke-" + System.nanoTime() + "@example.com";
            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new RegisterRequest(
                                    email,
                                    "oldpassword123",
                                    "Test User",
                                    "revoke_user_" + System.nanoTime() % 100000))))
                    .andExpect(status().isCreated());

            MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new LoginRequest(email, "oldpassword123"))))
                    .andExpect(status().isOk())
                    .andReturn();

            String refreshToken = objectMapper
                    .readTree(loginResult.getResponse().getContentAsString())
                    .at("/data/refreshToken")
                    .asText();

            // Create a valid password reset token
            var user = userRepository
                    .findActiveByEmail(
                            io.github.phunguy65.zms.shared.domain.valueobject.Email.of(email))
                    .orElseThrow();
            String otp = "123456";
            PasswordResetToken token = PasswordResetToken.issue(
                    user.getId(), otpHasher.hash(otp), Instant.now().plusSeconds(900));
            tokenRepository.save(token);

            // Reset password
            mockMvc.perform(post("/api/v1/auth/reset-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new ResetPasswordRequest(email, otp, null, "newpassword123"))))
                    .andExpect(status().isOk());

            // Old refresh token should now be invalid
            mockMvc.perform(post("/api/v1/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new RefreshTokenRequest(refreshToken))))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class VerifyOtpEndpoint {

        @Test
        void returnsTemporaryTokenWithValidOtp() throws Exception {
            String email = "verifyotp@example.com";
            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new RegisterRequest(
                                    email, "password123", "Verify OTP User", "verifyotp_user"))))
                    .andExpect(status().isCreated());

            var user = userRepository
                    .findActiveByEmail(
                            io.github.phunguy65.zms.shared.domain.valueobject.Email.of(email))
                    .orElseThrow();
            String otp = "123456";
            PasswordResetToken token = PasswordResetToken.issue(
                    user.getId(), otpHasher.hash(otp), Instant.now().plusSeconds(1200));
            tokenRepository.save(token);

            mockMvc.perform(post("/api/v1/auth/verify-otp")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"" + email + "\",\"otp\":\"" + otp + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("success"))
                    .andExpect(jsonPath("$.data.temporaryToken").isNotEmpty());
        }

        @Test
        void returns400WithInvalidOtp() throws Exception {
            String email = "invalidotp@example.com";
            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new RegisterRequest(
                                    email, "password123", "Invalid OTP User", "invalidotp_user"))))
                    .andExpect(status().isCreated());

            var user = userRepository
                    .findActiveByEmail(
                            io.github.phunguy65.zms.shared.domain.valueobject.Email.of(email))
                    .orElseThrow();
            String correctOtp = "123456";
            PasswordResetToken token = PasswordResetToken.issue(
                    user.getId(), otpHasher.hash(correctOtp), Instant.now().plusSeconds(1200));
            tokenRepository.save(token);

            mockMvc.perform(post("/api/v1/auth/verify-otp")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"" + email + "\",\"otp\":\"999999\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value("fail"))
                    .andExpect(jsonPath("$.data.code").value("OTP_INVALID"));
        }

        @Test
        void returns400WithExpiredOtp() throws Exception {
            String email = "expiredotp@example.com";
            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new RegisterRequest(
                                    email, "password123", "Expired OTP User", "expiredotp_user"))))
                    .andExpect(status().isCreated());

            var user = userRepository
                    .findActiveByEmail(
                            io.github.phunguy65.zms.shared.domain.valueobject.Email.of(email))
                    .orElseThrow();
            String otp = "123456";
            PasswordResetToken token = PasswordResetToken.issue(
                    user.getId(), otpHasher.hash(otp), Instant.now().minusSeconds(60));
            tokenRepository.save(token);

            mockMvc.perform(post("/api/v1/auth/verify-otp")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"" + email + "\",\"otp\":\"" + otp + "\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value("fail"))
                    .andExpect(jsonPath("$.data.code").value("OTP_EXPIRED"));
        }

        @Test
        void returns429WithRetryAfterHeaderWhenProgressiveDelayActive() throws Exception {
            String email = "delayotp@example.com";
            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new RegisterRequest(
                                    email, "password123", "Delay OTP User", "delayotp_user"))))
                    .andExpect(status().isCreated());

            var user = userRepository
                    .findActiveByEmail(
                            io.github.phunguy65.zms.shared.domain.valueobject.Email.of(email))
                    .orElseThrow();
            String otp = "123456";
            PasswordResetToken token = PasswordResetToken.reconstitute(
                    PasswordResetTokenId.of(java.util.UUID.randomUUID()),
                    user.getId(),
                    otpHasher.hash(otp),
                    Instant.now().plusSeconds(1200),
                    null,
                    2,
                    Instant.now().minusMillis(500),
                    Instant.now().minusSeconds(60));
            tokenRepository.save(token);

            mockMvc.perform(post("/api/v1/auth/verify-otp")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"" + email + "\",\"otp\":\"" + otp + "\"}"))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.status").value("fail"))
                    .andExpect(jsonPath("$.data.code").value("RESEND_TOO_SOON"))
                    .andExpect(header().exists("Retry-After"));
        }
    }

    @Nested
    class ResetPasswordWithTemporaryToken {

        @Test
        void resetsPasswordWithValidTemporaryToken() throws Exception {
            String email = "temptokenreset@example.com";
            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new RegisterRequest(
                                    email,
                                    "password123",
                                    "Temp Token Reset User",
                                    "temptokenreset_user"))))
                    .andExpect(status().isCreated());

            var user = userRepository
                    .findActiveByEmail(
                            io.github.phunguy65.zms.shared.domain.valueobject.Email.of(email))
                    .orElseThrow();
            String otp = "123456";
            PasswordResetToken token = PasswordResetToken.issue(
                    user.getId(), otpHasher.hash(otp), Instant.now().plusSeconds(1200));
            tokenRepository.save(token);

            MvcResult verifyResult = mockMvc.perform(post("/api/v1/auth/verify-otp")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"" + email + "\",\"otp\":\"" + otp + "\"}"))
                    .andExpect(status().isOk())
                    .andReturn();

            var verifyBody = objectMapper.readTree(verifyResult.getResponse().getContentAsString());
            String temporaryToken = verifyBody.at("/data/temporaryToken").asText();

            mockMvc.perform(post("/api/v1/auth/reset-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"" + email
                                    + "\",\"temporaryToken\":\"" + temporaryToken
                                    + "\",\"newPassword\":\"newpassword123\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("success"));

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new LoginRequest(email, "newpassword123"))))
                    .andExpect(status().isOk());
        }

        @Test
        void returns400WithExpiredTemporaryToken() throws Exception {
            String email = "expiredtoken@example.com";
            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new RegisterRequest(
                                    email,
                                    "password123",
                                    "Expired Token User",
                                    "expiredtoken_user"))))
                    .andExpect(status().isCreated());

            String expiredToken =
                    "eyJhbGciOiJIUzUxMiIsImtpZCI6Inptcy10ZW1wLXRva2VuIn0.eyJzdWIiOiIwMTkzZTNhYy1hMzQ1LTcwNzAtYjU4Zi1hYzI3YzI5YzI3YzIiLCJwdXJwb3NlIjoiUEFTU1dPUkRfUkVTRVQiLCJpYXQiOjE3MzAwMDAwMDAsImV4cCI6MTczMDAwMDAwMH0.invalid";

            mockMvc.perform(post("/api/v1/auth/reset-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"" + email
                                    + "\",\"temporaryToken\":\"" + expiredToken
                                    + "\",\"newPassword\":\"newpassword123\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value("fail"))
                    .andExpect(jsonPath("$.data.code").value("TOKEN_INVALID"));
        }

        @Test
        void returns400WithInvalidTemporaryToken() throws Exception {
            String email = "invalidtoken@example.com";
            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new RegisterRequest(
                                    email,
                                    "password123",
                                    "Invalid Token User",
                                    "invalidtoken_user"))))
                    .andExpect(status().isCreated());

            mockMvc.perform(
                            post("/api/v1/auth/reset-password")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            "{\"email\":\"" + email
                                                    + "\",\"temporaryToken\":\"invalid-token\",\"newPassword\":\"newpassword123\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value("fail"))
                    .andExpect(jsonPath("$.data.code").value("TOKEN_INVALID"));
        }
    }

    @Nested
    class FullTwoStepPasswordResetFlow {

        @Test
        void completesFullFlowFromForgotPasswordToResetWithTemporaryToken() throws Exception {
            String email = "fullflow@example.com";

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new RegisterRequest(
                                    email, "password123", "Full Flow User", "fullflow_user"))))
                    .andExpect(status().isCreated());

            mockMvc.perform(post("/api/v1/auth/forgot-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new ForgotPasswordRequest(email, null))))
                    .andExpect(status().isOk());

            var user = userRepository
                    .findActiveByEmail(
                            io.github.phunguy65.zms.shared.domain.valueobject.Email.of(email))
                    .orElseThrow();
            var tokenOpt = tokenRepository.findValidByUserId(user.getId());
            assertThat(tokenOpt).isPresent();

            String otp = otpGenerator.generate();
            PasswordResetToken token = tokenOpt.get();
            token = PasswordResetToken.reconstitute(
                    token.getId(),
                    token.getUserId(),
                    otpHasher.hash(otp),
                    token.getExpiresAt(),
                    token.getUsedAt().orElse(null),
                    token.getAttempts(),
                    token.getLastAttemptTimestamp().orElse(null),
                    token.getCreatedAt());
            tokenRepository.save(token);

            MvcResult verifyResult = mockMvc.perform(post("/api/v1/auth/verify-otp")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"" + email + "\",\"otp\":\"" + otp + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.temporaryToken").isNotEmpty())
                    .andReturn();

            var verifyBody = objectMapper.readTree(verifyResult.getResponse().getContentAsString());
            String temporaryToken = verifyBody.at("/data/temporaryToken").asText();

            mockMvc.perform(post("/api/v1/auth/reset-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"" + email
                                    + "\",\"temporaryToken\":\"" + temporaryToken
                                    + "\",\"newPassword\":\"newpassword456\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("success"));

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new LoginRequest(email, "newpassword456"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
        }
    }
}
