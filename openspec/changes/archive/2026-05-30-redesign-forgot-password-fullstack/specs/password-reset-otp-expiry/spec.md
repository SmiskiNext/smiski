## MODIFIED Requirements

### Requirement: OTP expiry time

The system SHALL set OTP expiry to 20 minutes from issuance time.

#### Scenario: OTP valid within 20 minutes

- **WHEN** user receives OTP at time T
- **AND** user submits OTP at time T + 19 minutes
- **THEN** system accepts OTP as valid

#### Scenario: OTP expired after 20 minutes

- **WHEN** user receives OTP at time T
- **AND** user submits OTP at time T + 21 minutes
- **THEN** system rejects OTP with error code `OTP_EXPIRED`
- **AND** system returns error message "OTP has expired. Please request a new
  one."

#### Scenario: OTP expiry boundary

- **WHEN** user receives OTP at time T
- **AND** user submits OTP at exactly time T + 20 minutes
- **THEN** system rejects OTP as expired
