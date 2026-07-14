## ADDED Requirements

### Requirement: Reusable Problem Details schema in the emitted spec

Each service's emitted OpenAPI document SHALL define a reusable `ProblemDetail`
schema component describing the RFC 9457 `application/problem+json` body used
for all error responses. The schema SHALL include the standard members `type`,
`title`, `status`, and `detail`, plus the extension members `code` (string),
`traceId` (string), and `errors` (array of field-level violation objects, each
with `field`, `code`, and optional `message`). Error responses SHALL reference
this schema via `$ref` rather than redefining the body inline.

#### Scenario: Problem Details schema present and referenced

- **WHEN** a service OpenAPI document is generated
- **THEN** it contains a `ProblemDetail` schema component exposing `type`,
  `title`, `status`, `detail`, `code`, `traceId`, and `errors`, and every error
  response in the document references that schema via `$ref`

#### Scenario: Violation entry shape documented

- **WHEN** the `ProblemDetail` schema documents the `errors` array
- **THEN** each entry declares a `field` (string), a `code` constrained to the
  fixed set `REQUIRED`, `INVALID_FORMAT`, `TOO_SHORT`, `TOO_LONG`,
  `INVALID_VALUE`, and an optional `message` (string)

### Requirement: Common error responses applied to every operation

The emitted OpenAPI document SHALL attach the shared error responses produced by
the shared framework exception handling — `405 Method Not Allowed`,
`415 Unsupported Media Type`, and `500 Internal Server Error` — to every
operation, each with `content` type `application/problem+json` referencing the
`ProblemDetail` schema. This attachment SHALL be applied uniformly across
services without per-controller declaration.

#### Scenario: Common responses present on an operation

- **WHEN** any operation is generated into a service OpenAPI document
- **THEN** the operation declares `405`, `415`, and `500` responses whose body
  is `application/problem+json` referencing the `ProblemDetail` schema

#### Scenario: No per-controller duplication

- **WHEN** a new controller operation is added to a service
- **THEN** the `405`, `415`, and `500` responses appear in the emitted spec
  without the controller declaring them explicitly

### Requirement: Endpoint-specific responses documented with examples

Every controller operation SHALL document its endpoint-specific responses with
accurate media types, response schemas, and at least one example per documented
status. Success responses SHALL reference the concrete representation schema;
the `400 Bad Request` response SHALL reference the `ProblemDetail` schema and
provide examples for each distinct error the endpoint can return.

#### Scenario: Tenant registration success responses documented

- **WHEN** the `POST /api/{version}/tenants` operation is generated
- **THEN** it documents `201 Created` (first install) and `200 OK` (reinstall),
  each with an `application/json` body referencing the tenant representation
  schema and at least one example

#### Scenario: Tenant registration error responses documented

- **WHEN** the `POST /api/{version}/tenants` operation is generated
- **THEN** it documents a `400 Bad Request` response referencing the
  `ProblemDetail` schema with examples covering `VALIDATION_ERROR` (including a
  populated `errors` array), `MALFORMED_REQUEST`, and `MISSING_TENANT_CONTEXT`

#### Scenario: Request and response models are self-describing

- **WHEN** the request and response models for an operation are generated
- **THEN** their schema properties carry human-readable descriptions and the
  models expose at least one example value

### Requirement: Invalid enum request field rejected at the boundary

A request field constrained to an enumeration SHALL reject an unrecognized value
at the request boundary as a validation failure yielding `400 VALIDATION_ERROR`
with a corresponding `errors` entry, rather than propagating an unhandled
exception that produces `500`. An absent or blank value for an optional enum
field SHALL retain its documented default.

#### Scenario: Unrecognized environment type yields 400

- **WHEN** a client submits a tenant registration with an `environmentType`
  value that is not one of the supported values
- **THEN** the response is `400` `application/problem+json` with `code`
  `VALIDATION_ERROR` and an `errors` entry for the `environmentType` field

#### Scenario: Absent environment type keeps default

- **WHEN** a client omits `environmentType` or sends it blank
- **THEN** the request is accepted and the environment type defaults to
  `PRODUCTION`
