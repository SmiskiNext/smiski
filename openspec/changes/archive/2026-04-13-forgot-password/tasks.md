# Tasks

## 0. API Contract (OpenAPI) - Auto-Generated

> **Note:** `openapi/unified-openapi.yaml` is auto-generated. Backend endpoints
> with springdoc annotations generate the spec. Run build +
> `npm run openapi:join` to regenerate.

- [x] 0.1 (After 5.x) Run
      `./gradlew :services:user-management:generateOpenApiDocs` to generate
      service OpenAPI spec
- [x] 0.2 Run `npm run openapi:join` to merge all service specs into
      unified-openapi.yaml
- [x] 0.3 Regenerate Android API client (./gradlew
      :frontends:android-app:app:openApiGenerate)

## 1. Database Schema & Migration

- [x] 1.1 Create Flyway migration V{N}\_\_add_password_reset_tables.sql with
      password_reset_tokens and password_reset_attempts tables

## 2. Domain Layer (user-management)

- [x] 2.1 Create PasswordResetTokenId value object (UUID wrapper)
- [x] 2.2 Create PasswordResetToken aggregate root with factory methods (issue,
      reconstitute), behaviors (isExpired, isUsed, isValid, markUsed,
      incrementAttempts, isLocked)
- [x] 2.3 Create PasswordResetTokenRepository port interface
- [x] 2.4 Add new AuthError types: OtpExpired, OtpInvalid, OtpAlreadyUsed,
      OtpLocked, RateLimitExceeded, GoogleOnlyAccount
- [x] 2.5 Create PasswordResetRequestedEvent implementing PublishableEvent (with
      topic, eventType, aggregateType, aggregateId methods)
- [x] 2.6 Add updatePassword(HashedPassword newPassword) behavior to User
      aggregate

## 3. Infrastructure Layer (user-management)

- [x] 3.1 Create PasswordResetTokenJpaEntity with JPA mappings
- [x] 3.2 Create PasswordResetTokenJpaRepository (Spring Data)
- [x] 3.3 Create PasswordResetTokenRepositoryAdapter implementing domain port
- [x] 3.4 Create PasswordResetAttemptJpaEntity for rate limiting
- [x] 3.5 Create PasswordResetAttemptJpaRepository with count queries for rate
      limiting (countByEmailAndCreatedAtAfter,
      countByIpAddressAndCreatedAtAfter)
- [x] 3.6 Create OtpGenerator helper (6-digit SecureRandom)
- [x] 3.7 Create OtpHasher helper (SHA-256 hashing)
- [x] 3.8 Create PasswordResetRateLimiter service with isAllowed() and
      recordAttempt()

## 4. Application Layer (user-management)

- [x] 4.1 Create RequestPasswordResetCommand record
- [x] 4.2 Create ResetPasswordCommand record
- [x] 4.3 Create RequestPasswordResetUseCase — inject
      org.springframework.context.ApplicationEventPublisher (Spring's, not
      domain EventPublisher port) for Outbox pattern integration; includes rate
      limiting, OTP generation, event publishing via publishEvent()
- [x] 4.4 Create ResetPasswordUseCase with OTP verification, password update via
      User.updatePassword(), session revocation via
      RefreshTokenRepository.revokeAllByUserId()

## 5. Presentation Layer (user-management)

- [x] 5.1 Create ForgotPasswordRequest DTO with email validation
- [x] 5.2 Create ResetPasswordRequest DTO with email, otp, newPassword
      validation
- [x] 5.3 Add POST /auth/forgot-password endpoint to AuthController
- [x] 5.4 Add POST /auth/reset-password endpoint to AuthController
- [x] 5.5 Add new AuthErrorCode enum values and error mappings in BaseController
      for new AuthError types

## 6. Notification Service

- [x] 6.1 Create PasswordResetRequestedMessage record (Kafka message model
      matching CloudEvent structure)
- [x] 6.2 Create PasswordResetRequestedConsumer (Kafka listener) — reuse
      existing invitationConsumerGroup from NotificationProperties
- [x] 6.3 Create PasswordResetEmailRenderer with HTML template (OTP display,
      15-min expiry notice, warning not to share)
- [x] 6.4 Create SendPasswordResetEmailUseCase

## 7. Android Domain Layer

- [x] 7.1 Create RequestPasswordResetUseCase in domain/usecase/auth/ (thin
      wrapper delegating to AuthRepository)
- [x] 7.2 Create ResetPasswordUseCase in domain/usecase/auth/ (thin wrapper
      delegating to AuthRepository)
- [x] 7.3 Add forgotPassword(email) and resetPassword(email, otp, newPassword)
      methods to AuthRepository interface

## 8. Android Data Layer

- [x] 8.1 Add forgotPassword() implementation to AuthRepositoryImpl using
      generated UserManagementForgotPasswordRequest
- [x] 8.2 Add resetPassword() implementation to AuthRepositoryImpl using
      generated UserManagementResetPasswordRequest
- [x] 8.3 Add error code mappings (OTP_EXPIRED, OTP_INVALID, OTP_LOCKED,
      RATE_LIMIT_EXCEEDED, GOOGLE_ONLY_ACCOUNT) to AndroidErrorTranslator
      CODE_MAP

## 9. Android Presentation Layer

- [x] 9.1 Create ForgotPasswordViewModel with email validation
      (Patterns.EMAIL_ADDRESS), API call, UiState management
- [x] 9.2 Create fragment_forgot_password.xml layout (TextInputLayout for email,
      MaterialButton for send, ProgressBar for loading)
- [x] 9.3 Create ForgotPasswordFragment with view binding, state observers,
      navigation to resetPasswordFragment
- [x] 9.4 Create ResetPasswordViewModel with OTP/password validation, 60s resend
      countdown timer (CountDownTimer), API call
- [x] 9.5 Create fragment_reset_password.xml layout (OTP input, password fields
      with toggle, confirm field, resend button with timer text)
- [x] 9.6 Create ResetPasswordFragment with view binding, observers, countdown
      timer, navigation to loginFragment on success
- [x] 9.7 Add string resources for forgot password flow (English) in
      values/strings.xml
- [x] 9.8 Add string resources for forgot password flow (Vietnamese) in
      values-vi/strings.xml
- [x] 9.9 Update nav_graph_auth.xml: add forgotPasswordFragment and
      resetPasswordFragment destinations with <argument> for email (string) on
      resetPasswordFragment, add navigation actions
- [x] 9.10 Update LoginFragment to navigate to forgotPasswordFragment on "Forgot
      password?" click
- [x] 9.11 Enable tvForgotPassword (remove setEnabled(false) in onViewCreated)

## 10. Testing

- [x] 10.1 Unit tests for PasswordResetToken domain model (isExpired, isUsed,
      isLocked, incrementAttempts)
- [x] 10.2 Unit tests for RequestPasswordResetUseCase (happy path, rate limit
      exceeded, Google-only account, email not found)
- [x] 10.3 Unit tests for ResetPasswordUseCase (happy path, invalid OTP, expired
      OTP, locked after 5 attempts)
- [x] 10.4 Integration tests for forgot-password and reset-password endpoints
      (AuthController)
- [x] 10.5 Unit tests for notification service SendPasswordResetEmailUseCase

## 11. Verification

- [x] 11.1 Run ./gradlew :services:user-management:check to verify backend
      changes (password reset tests pass; pre-existing failures in unrelated
      tests)
- [x] 11.2 Run ./gradlew :services:notification:check to verify notification
      changes
- [ ] 11.3 Run ./gradlew :frontends:android-app:app:assembleDebug to verify
      Android build
- [ ] 11.4 Manual E2E test: full forgot password flow on emulator
