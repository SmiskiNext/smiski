## MODIFIED Requirements

### Requirement: Rate limit for password reset requests per email

The system SHALL limit password reset requests to 3 per hour per email address.

#### Scenario: First three requests within one hour

- **WHEN** user requests password reset for email address A at time T
- **AND** user requests password reset for email address A at time T + 10
  minutes
- **AND** user requests password reset for email address A at time T + 20
  minutes
- **THEN** system processes all three requests successfully

#### Scenario: Fourth request within one hour

- **WHEN** user has made 3 password reset requests for email address A in past
  hour
- **AND** user requests password reset for email address A again
- **THEN** system returns HTTP 429 Too Many Requests
- **AND** system returns error code `RATE_LIMIT_EXCEEDED`
- **AND** system returns error message "Too many password reset requests. Please
  try again later."
- **AND** system includes `Retry-After` header with seconds until rate limit
  resets

#### Scenario: Rate limit resets after one hour

- **WHEN** user made 3 password reset requests at time T
- **AND** user requests password reset at time T + 61 minutes
- **THEN** system resets rate limit counter
- **AND** system processes request successfully

### Requirement: Rate limit for password reset requests per IP address

The system SHALL limit password reset requests to 10 per hour per IP address.

#### Scenario: First ten requests from same IP within one hour

- **WHEN** IP address X makes 10 password reset requests for different emails
  within 1 hour
- **THEN** system processes all ten requests successfully

#### Scenario: Eleventh request from same IP within one hour

- **WHEN** IP address X has made 10 password reset requests in past hour
- **AND** IP address X makes another password reset request
- **THEN** system returns HTTP 429 Too Many Requests
- **AND** system returns error code `RATE_LIMIT_EXCEEDED`
- **AND** system returns error message "Too many password reset requests from
  your network. Please try again later."
- **AND** system includes `Retry-After` header with seconds until rate limit
  resets

#### Scenario: Rate limit applies to both email and IP

- **WHEN** email address A has made 3 requests in past hour (email limit
  reached)
- **AND** IP address X has made 5 requests in past hour (IP limit not reached)
- **AND** user requests password reset for email A from IP X
- **THEN** system returns HTTP 429 with email rate limit error

### Requirement: Server-side resend cooldown enforcement

The system SHALL enforce 120-second cooldown between password reset requests for
the same email address.

#### Scenario: Resend request within 120 seconds

- **WHEN** user requests password reset for email address A at time T
- **AND** user requests password reset for email address A at time T + 60
  seconds
- **THEN** system returns HTTP 429 Too Many Requests
- **AND** system returns error code `RESEND_TOO_SOON`
- **AND** system returns error message "Please wait before requesting another
  code."
- **AND** system includes `retryAfter` field with remaining seconds (60)

#### Scenario: Resend request after 120 seconds

- **WHEN** user requests password reset for email address A at time T
- **AND** user requests password reset for email address A at time T + 121
  seconds
- **THEN** system processes request successfully
- **AND** system sends new OTP email

#### Scenario: Resend cooldown boundary

- **WHEN** user requests password reset for email address A at time T
- **AND** user requests password reset for email address A at exactly time T +
  120 seconds
- **THEN** system processes request successfully

#### Scenario: Resend cooldown independent of rate limit

- **WHEN** user has made 1 password reset request (under rate limit)
- **AND** user attempts resend within 120 seconds
- **THEN** system enforces resend cooldown
- **AND** system does NOT increment rate limit counter
