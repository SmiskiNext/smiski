## ADDED Requirements

### Requirement: Two-step password reset API flow

The system SHALL split password reset into two separate API endpoints:
`POST /v1/auth/verify-otp` for OTP verification and
`POST /v1/auth/reset-password` for password change.

#### Scenario: Verify OTP endpoint success

- **WHEN** user submits valid OTP to `POST /v1/auth/verify-otp`
- **THEN** system validates OTP against stored token
- **AND** system generates temporary JWT token with 5-minute expiry
- **AND** system includes claims: `userId`, `purpose: "password-reset"`, `exp`,
  `iat`
- **AND** system returns HTTP 200 with temporary token in response body
- **AND** system does NOT invalidate the OTP token yet

#### Scenario: Verify OTP endpoint with invalid OTP

- **WHEN** user submits invalid OTP to `POST /v1/auth/verify-otp`
- **THEN** system increments attempt counter
- **AND** system applies progressive delay if applicable
- **AND** system returns HTTP 400 with error code `OTP_INVALID`
- **AND** system returns remaining attempts in response

#### Scenario: Verify OTP endpoint with expired OTP

- **WHEN** user submits OTP that expired (>20 minutes old)
- **THEN** system returns HTTP 400 with error code `OTP_EXPIRED`
- **AND** system returns error message "OTP has expired. Please request a new
  one."

#### Scenario: Reset password endpoint with valid temporary token

- **WHEN** user submits new password and valid temporary token to
  `POST /v1/auth/reset-password`
- **THEN** system validates temporary token signature and expiry
- **AND** system validates token purpose is "password-reset"
- **AND** system extracts userId from token claims
- **AND** system hashes new password with Argon2id
- **AND** system updates user password in database
- **AND** system invalidates all password reset tokens for that user
- **AND** system returns HTTP 200 with success message

#### Scenario: Reset password endpoint with expired temporary token

- **WHEN** user submits new password with temporary token expired (>5 minutes
  old)
- **THEN** system returns HTTP 401 with error code `TOKEN_EXPIRED`
- **AND** system returns error message "Verification expired. Please verify OTP
  again."

#### Scenario: Reset password endpoint with invalid token signature

- **WHEN** user submits new password with tampered temporary token
- **THEN** system returns HTTP 401 with error code `TOKEN_INVALID`
- **AND** system returns error message "Invalid verification token."

#### Scenario: Reset password endpoint with wrong token purpose

- **WHEN** user submits access token or refresh token instead of temporary token
- **THEN** system validates token purpose claim
- **AND** system returns HTTP 403 with error code `TOKEN_INVALID_PURPOSE`
- **AND** system returns error message "Invalid token type."

### Requirement: Temporary token security properties

The system SHALL generate temporary tokens with the following security
properties: 5-minute expiry, single-purpose scope, and signed with same secret
as access tokens.

#### Scenario: Temporary token expiry enforcement

- **WHEN** temporary token is generated at time T
- **THEN** token `exp` claim SHALL be set to T + 5 minutes
- **AND** system SHALL reject token after expiry time

#### Scenario: Temporary token purpose scope

- **WHEN** temporary token is generated
- **THEN** token SHALL include `purpose: "password-reset"` claim
- **AND** token SHALL NOT be usable for general authentication
- **AND** token SHALL NOT be usable for API access

#### Scenario: Temporary token signature

- **WHEN** temporary token is generated
- **THEN** token SHALL be signed with HS256 algorithm
- **AND** token SHALL use same JWT secret as access tokens
- **AND** token signature SHALL be validated on every use

### Requirement: Backward compatibility with legacy OTP parameter

The system SHALL accept both OTP (legacy) and temporary token (new) in
`POST /v1/auth/reset-password` for a 30-day grace period.

#### Scenario: Reset password with legacy OTP parameter

- **WHEN** user submits new password with OTP parameter (no temporary token)
- **AND** current date is within 30 days of deployment
- **THEN** system validates OTP directly
- **AND** system proceeds with password reset
- **AND** system logs deprecation warning

#### Scenario: Reset password with both OTP and temporary token

- **WHEN** user submits new password with both OTP and temporary token
- **THEN** system prioritizes temporary token
- **AND** system ignores OTP parameter

#### Scenario: Reset password with legacy OTP after grace period

- **WHEN** user submits new password with OTP parameter (no temporary token)
- **AND** current date is after 30-day grace period
- **THEN** system returns HTTP 400 with error code `OTP_PARAMETER_DEPRECATED`
- **AND** system returns error message "OTP parameter is no longer supported.
  Please use the two-step verification flow."
