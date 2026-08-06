## MODIFIED Requirements

### Requirement: Problem Details error responses

All error responses SHALL follow the RFC 9457 Problem Details body shape and
SHALL be served with `Content-Type: application/json`. The body SHALL include
the standard `type`, `title`, `status`, and `detail` members plus the extension
members `code` (a stable, machine-readable identifier) and `traceId` (the
current request trace identifier). The `title` and `detail` SHALL be localized
using the request `Accept-Language`. The `type` member SHALL be a URI derived
from the error code under a configured base URI, or `about:blank` when no base
URI is configured. The HTTP status SHALL be derived from the error's category.

The media type is `application/json` rather than `application/problem+json`
because the Forge Remote invocation contract rejects any non-2xx response whose
content type is not `application/json`, discarding the body and replacing it
with a generic platform error. Serving the same body under `application/json`
keeps `code` and `traceId` available to clients.

Every error response SHALL carry a body. A non-2xx response with an empty body
cannot be parsed by the Forge Remote contract and is reported to the caller as a
transport failure.

#### Scenario: Domain error mapped to Problem Details

- **WHEN** an application operation fails with a domain error whose category is
  `NOT_FOUND`
- **THEN** the response has status `404`, `Content-Type: application/json`, and
  a body containing `type`, `title`, `status`, `detail`, a machine-readable
  `code`, and a `traceId`

#### Scenario: Localized message via Accept-Language

- **WHEN** a client sends an error-producing request with `Accept-Language: vi`
- **THEN** the `title` and `detail` members are resolved to the Vietnamese
  message bundle while `code` remains locale-independent

#### Scenario: Type URI derivation

- **WHEN** a Problem Details response is produced and a type base URI is
  configured
- **THEN** the `type` member is that base URI followed by the kebab-cased error
  code; otherwise the `type` member is `about:blank`

#### Scenario: Authorization failure carries the Problem Details body

- **WHEN** a request is rejected because the caller lacks the required project
  permission
- **THEN** the response has status `403`, `Content-Type: application/json`, and
  a body carrying the machine-readable `code` for the authorization failure

#### Scenario: Error response is never empty

- **WHEN** any error response is produced, including those written outside the
  controller layer
- **THEN** the response carries a Problem Details body rather than an empty
  payload

#### Scenario: Success responses unaffected

- **WHEN** an operation succeeds with a representation
- **THEN** the response media type follows the success contract and is not
  altered by the error-response media type

#### Scenario: Documented error responses use the same media type

- **WHEN** the generated OpenAPI specification documents an error status
- **THEN** the documented content type for that response is `application/json`,
  matching what the service emits at runtime

### Requirement: Validation-failure semantics

When a request fails input validation, the response SHALL be a Problem Details
body served with `Content-Type: application/json`, carrying the
`VALIDATION_ERROR` code and HTTP status `400`, and SHALL include an `errors`
extension member listing each field-level failure. Each entry SHALL carry a
stable `field` name and a machine-readable `code` (from a fixed set: `REQUIRED`,
`INVALID_FORMAT`, `TOO_SHORT`, `TOO_LONG`, `INVALID_VALUE`), with an optional
server-localized `message`.

#### Scenario: Bean Validation constraint failures aggregated

- **WHEN** a request body fails multiple Bean Validation constraints
- **THEN** the response is `400` Problem Details with `code` `VALIDATION_ERROR`
  and an `errors` array containing one entry per failed field, each with a
  `field` and a machine-readable `code`

#### Scenario: Missing required field maps to REQUIRED

- **WHEN** a field annotated `@NotBlank`, `@NotNull`, or `@NotEmpty` is absent
  or blank
- **THEN** the corresponding `errors` entry has `code` `REQUIRED`

#### Scenario: Type mismatch maps to INVALID_FORMAT

- **WHEN** a path variable or request parameter cannot be converted to its
  declared type
- **THEN** the response is `VALIDATION_ERROR` and the offending field's entry
  has `code` `INVALID_FORMAT`

#### Scenario: Malformed body maps to MALFORMED_REQUEST

- **WHEN** the request body cannot be parsed (e.g. malformed JSON)
- **THEN** the response is `400` Problem Details with `code` `MALFORMED_REQUEST`

#### Scenario: Validation failure readable by a Forge Remote client

- **WHEN** a validation failure is returned to a client calling through Forge
  Remote
- **THEN** the client can read the `errors` array and the `VALIDATION_ERROR`
  code, because the response media type is `application/json`
