## ADDED Requirements

### Requirement: CAPTCHA verification after failed attempts

The system SHALL require CAPTCHA verification after 2 or more failed password
reset requests from the same email address or IP address within a 1-hour window.

#### Scenario: First password reset request

- **WHEN** user requests password reset for the first time in 1 hour
- **THEN** system processes request without requiring CAPTCHA
- **AND** system increments attempt counter for email and IP

#### Scenario: Second password reset request

- **WHEN** user requests password reset for the second time within 1 hour
- **THEN** system processes request without requiring CAPTCHA
- **AND** system increments attempt counter for email and IP

#### Scenario: Third password reset request without CAPTCHA

- **WHEN** user requests password reset for the third time within 1 hour
- **AND** request does not include CAPTCHA token
- **THEN** system returns HTTP 400 Bad Request
- **AND** system returns error code `CAPTCHA_REQUIRED`
- **AND** system returns error message "CAPTCHA verification required. Please
  complete the CAPTCHA and try again."

#### Scenario: Third password reset request with valid CAPTCHA

- **WHEN** user requests password reset for the third time within 1 hour
- **AND** request includes valid CAPTCHA token with score ≥ 0.5
- **THEN** system processes request successfully
- **AND** system sends OTP email

#### Scenario: Third password reset request with invalid CAPTCHA

- **WHEN** user requests password reset for the third time within 1 hour
- **AND** request includes CAPTCHA token with score < 0.5
- **THEN** system returns HTTP 400 Bad Request
- **AND** system returns error code `CAPTCHA_INVALID`
- **AND** system returns error message "CAPTCHA verification failed. Please try
  again."

#### Scenario: CAPTCHA attempt counter resets after 1 hour

- **WHEN** user made 2 password reset requests 61 minutes ago
- **AND** user requests password reset now
- **THEN** system resets attempt counter to 1
- **AND** system processes request without requiring CAPTCHA

### Requirement: CAPTCHA verification using Google reCAPTCHA v3

The system SHALL use Google reCAPTCHA v3 for CAPTCHA verification with a minimum
score threshold of 0.5.

#### Scenario: CAPTCHA token verification with Google API

- **WHEN** system receives CAPTCHA token from client
- **THEN** system sends token to Google reCAPTCHA v3 API for verification
- **AND** system checks response score is ≥ 0.5
- **AND** system checks response action matches expected action
  ("password_reset")

#### Scenario: CAPTCHA verification timeout

- **WHEN** Google reCAPTCHA API does not respond within 5 seconds
- **THEN** system logs timeout error
- **AND** system allows request to proceed (fail-open to prevent DoS)
- **AND** system alerts monitoring system of CAPTCHA service degradation

#### Scenario: CAPTCHA verification network error

- **WHEN** Google reCAPTCHA API returns network error
- **THEN** system logs error
- **AND** system allows request to proceed (fail-open)
- **AND** system alerts monitoring system of CAPTCHA service failure

### Requirement: CAPTCHA attempt tracking per email and IP

The system SHALL track CAPTCHA attempt counters separately for email addresses
and IP addresses, requiring CAPTCHA if either counter reaches threshold.

#### Scenario: Email reaches threshold, IP does not

- **WHEN** email address has 2 failed attempts in past hour
- **AND** IP address has 0 failed attempts in past hour
- **THEN** system requires CAPTCHA for next request from that email

#### Scenario: IP reaches threshold, email does not

- **WHEN** IP address has 2 failed attempts in past hour
- **AND** email address has 0 failed attempts in past hour
- **THEN** system requires CAPTCHA for next request from that IP

#### Scenario: Both email and IP reach threshold

- **WHEN** email address has 2 failed attempts in past hour
- **AND** IP address has 2 failed attempts in past hour
- **THEN** system requires CAPTCHA for next request
- **AND** system validates CAPTCHA only once (not twice)
