# ADDED Requirements

## Requirement: AuthInterceptor for Bearer Token

The system SHALL provide an OkHttp interceptor that injects Bearer token into
authenticated API requests.

### Scenario: Add Authorization header

- **WHEN** an API request is made and TokenManager has an access token
- **THEN** AuthInterceptor SHALL add header
  `Authorization: Bearer <accessToken>` to the request

### Scenario: No token available

- **WHEN** an API request is made and TokenManager.getAccessToken() returns null
- **THEN** AuthInterceptor SHALL proceed with the request without adding
  Authorization header

### Scenario: Interceptor order

- **WHEN** OkHttpClient is built in NetworkModule
- **THEN** AuthInterceptor SHALL be added BEFORE JsendUnwrapInterceptor to
  ensure token is added before response processing

## Requirement: AuthInterceptor Integration

The AuthInterceptor SHALL be integrated into the existing NetworkModule.

### Scenario: Hilt injection

- **WHEN** AuthInterceptor is created
- **THEN** it SHALL receive TokenManager via constructor injection

### Scenario: NetworkModule provides interceptor

- **WHEN** NetworkModule.provideOkHttpClient() is called
- **THEN** it SHALL include AuthInterceptor in the interceptor chain

## Requirement: GetMeUseCase

The system SHALL provide a use case for fetching the current user's profile.

### Scenario: Execute get me

- **WHEN** `GetMeUseCase.execute()` is called
- **THEN** it SHALL call `GET /api/v1/me` via the generated MeApi

### Scenario: Return user profile

- **WHEN** /api/v1/me succeeds
- **THEN** `GetMeUseCase` SHALL return a `CompletableFuture<User>` with userId,
  email, fullName, username, avatarUrl

### Scenario: Authenticated request

- **WHEN** GetMeUseCase makes the API call
- **THEN** the request SHALL include Authorization header (via AuthInterceptor)
  with the current access token
