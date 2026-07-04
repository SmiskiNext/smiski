## ADDED Requirements

### Requirement: Progressive delay on OTP verification failures

The system SHALL implement exponential backoff delays for failed OTP
verification attempts instead of hard account locks. The delay SHALL be
calculated as `min(2^(attempts-1), 16)` seconds, where attempts is the number of
consecutive failed verifications for the current token.

#### Scenario: First failed OTP attempt

- **WHEN** user submits incorrect OTP for the first time
- **THEN** system increments attempt counter to 1
- **AND** system allows immediate retry (0 second delay)
- **AND** system returns error "Invalid OTP code"

#### Scenario: Second failed OTP attempt

- **WHEN** user submits incorrect OTP for the second time
- **THEN** system increments attempt counter to 2
- **AND** system enforces 1 second delay before next attempt
- **AND** system returns error "Invalid OTP code. Please wait 1 second before
  retrying."

#### Scenario: Third failed OTP attempt

- **WHEN** user submits incorrect OTP for the third time
- **THEN** system increments attempt counter to 3
- **AND** system enforces 2 second delay before next attempt
- **AND** system returns error "Invalid OTP code. Please wait 2 seconds before
  retrying."

#### Scenario: Fifth failed OTP attempt

- **WHEN** user submits incorrect OTP for the fifth time
- **THEN** system increments attempt counter to 5
- **AND** system enforces 8 second delay before next attempt
- **AND** system returns error "Invalid OTP code. Please wait 8 seconds before
  retrying."

#### Scenario: Sixth or more failed OTP attempts

- **WHEN** user submits incorrect OTP for the sixth or subsequent time
- **THEN** system caps delay at 16 seconds (maximum)
- **AND** system returns error "Invalid OTP code. Please wait 16 seconds before
  retrying."

#### Scenario: Retry before delay elapsed

- **WHEN** user attempts OTP verification before the required delay has elapsed
- **THEN** system returns HTTP 429 Too Many Requests
- **AND** system includes `Retry-After` header with remaining seconds
- **AND** system returns error "Too many attempts. Please wait X seconds before
  retrying."

#### Scenario: Successful OTP verification resets delay

- **WHEN** user submits correct OTP after previous failed attempts
- **THEN** system resets attempt counter to 0
- **AND** system clears any pending delays
- **AND** system proceeds with password reset flow

#### Scenario: Token expiry resets delay

- **WHEN** OTP token expires (20 minutes after issuance)
- **THEN** system invalidates the token
- **AND** system resets attempt counter to 0
- **AND** user must request new OTP to retry

### Requirement: Delay calculation persistence

The system SHALL persist attempt count and last attempt timestamp in the
`PasswordResetToken` entity to enforce delays across multiple requests.

#### Scenario: Delay persists across requests

- **WHEN** user makes failed OTP attempt and closes browser/app
- **AND** user returns and attempts OTP verification again
- **THEN** system enforces delay based on previous attempt count
- **AND** system does not allow delay bypass by restarting client

#### Scenario: Delay tied to token, not user

- **WHEN** user has active token with failed attempts and pending delay
- **AND** user requests new OTP (new token issued)
- **THEN** system resets attempt counter for new token
- **AND** user can verify new OTP immediately without delay
