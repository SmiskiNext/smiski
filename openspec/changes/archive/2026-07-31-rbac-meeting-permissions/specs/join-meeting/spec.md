## MODIFIED Requirements

### Requirement: Join a meeting endpoint

The meet service SHALL expose `POST /meetings/{id}:join` allowing an
authenticated account to join a meeting. The endpoint SHALL require the
`view-meeting` project permission — if the caller's permission context does not
contain `view-meeting`, the endpoint SHALL reject the request with
`403 application/problem+json` and code `NOT_AUTHORIZED` before executing the
use case. The request body SHALL carry a non-blank `displayName` (max 100
characters) and a non-blank `deviceId`, and MAY carry an optional `avatarUrl`.
The account identifier SHALL be taken from the request account context and the
tenant from the tenant context; neither SHALL be accepted in the body. A
successful response SHALL be `200 OK` with a body carrying a `status` field that
distinguishes the outcome. Failures SHALL be returned as RFC 9457
`application/problem+json`.

#### Scenario: Missing required field is a validation error

- **WHEN** a client calls `POST /meetings/{id}:join` with a blank `displayName`
  or blank `deviceId`
- **THEN** the response is `400` `application/problem+json` with code
  `VALIDATION_ERROR` and a `REQUIRED` entry for the offending field

#### Scenario: Missing view-meeting permission is rejected

- **WHEN** a caller without `view-meeting` sends `POST /meetings/{id}:join`
- **THEN** the response is `403 application/problem+json` with code
  `NOT_AUTHORIZED` and no join operation is executed

#### Scenario: Unknown meeting

- **WHEN** a client joins a meeting id that does not exist for the current
  tenant
- **THEN** the response is `404` `application/problem+json` with a
  machine-readable meeting-not-found code
