## Purpose

Defines the cross-service HTTP API contract every backend service must satisfy:
the versioned URL scheme, RESTful resource and action naming, the shape of
successful responses, RFC 9457 (`application/problem+json`) error responses, and
validation-failure semantics. This is the authoritative reference for API design
reviews and for onboarding new services.

## ADDED Requirements

### Requirement: Versioned API URL scheme

Every REST endpoint SHALL be exposed under a versioned prefix of the form
`/api/{version}/path/to/resource`, where `{version}` is an integer occupying
path-segment index 1 (segment 0 is the literal `api`). The prefix SHALL be
applied globally to every application `@RestController` so that controllers
declare only the resource path and never repeat `api` or the version.

#### Scenario: Version resolved from path segment

- **WHEN** a client sends `GET /api/1/meetings/{id}`
- **THEN** the request is routed to the controller handler mapped to
  `/meetings/{id}` and the resolved API version is `1`

#### Scenario: Controller declares only the resource path

- **WHEN** a controller method is annotated `@GetMapping("/meetings/{id}")`
- **THEN** the effective route is `/api/{version}/meetings/{id}` without the
  controller repeating the `/api/{version}` prefix

#### Scenario: Framework endpoints stay unprefixed

- **WHEN** a request targets a non-application endpoint such as Actuator or
  `/v3/api-docs`
- **THEN** the versioned prefix is not applied and the endpoint is served at its
  conventional unprefixed path

### Requirement: RESTful resource and action naming

Endpoints SHALL map standard CRUD operations to collection and resource paths
using the conventional HTTP methods. Operations that fall outside standard REST
SHALL use the `:action` suffix on the target resource and SHALL use the `POST`
method.

#### Scenario: Standard collection and resource operations

- **WHEN** defining CRUD endpoints for a `meetings` resource
- **THEN** `GET /meetings` and `POST /meetings` operate on the collection, and
  `GET /meetings/{id}`, `PUT /meetings/{id}`, and `DELETE /meetings/{id}`
  operate on a single resource

#### Scenario: Nested sub-resource

- **WHEN** exposing participants of a meeting
- **THEN** the endpoints are `GET /meetings/{id}/participants` and
  `POST /meetings/{id}/participants`

#### Scenario: Non-CRUD action uses the action suffix

- **WHEN** an operation is not a standard CRUD verb (e.g. ending a meeting or
  muting a participant)
- **THEN** it is expressed as a `POST` to a `:action`-suffixed path such as
  `POST /meetings/{id}:end` or
  `POST /meetings/{id}/participants/{participantId}:mute`

### Requirement: Successful response body

A successful response SHALL return the resource representation directly as the
response body without any wrapping envelope. The HTTP status SHALL reflect the
operation outcome: `200 OK` for retrievals and updates that return a body,
`201 Created` with a `Location` header for resource creation, and
`204 No Content` for operations that produce no body.

#### Scenario: Retrieval returns the raw representation

- **WHEN** a client successfully retrieves a resource
- **THEN** the response status is `200 OK` and the body is the resource
  representation itself, not wrapped in an envelope

#### Scenario: Creation returns Location header

- **WHEN** a client successfully creates a resource
- **THEN** the response status is `201 Created`, the `Location` header points to
  the newly created resource, and the body is the created representation

#### Scenario: No-body operation returns 204

- **WHEN** a successful operation produces no representation
- **THEN** the response status is `204 No Content` with an empty body

### Requirement: Problem Details error responses

All error responses SHALL follow RFC 9457 (`application/problem+json`). The body
SHALL include the standard `type`, `title`, `status`, and `detail` members plus
the extension members `code` (a stable, machine-readable identifier) and
`traceId` (the current request trace identifier). The `title` and `detail` SHALL
be localized using the request `Accept-Language`. The `type` member SHALL be a
URI derived from the error code under a configured base URI, or `about:blank`
when no base URI is configured. The HTTP status SHALL be derived from the
error's category.

#### Scenario: Domain error mapped to Problem Details

- **WHEN** an application operation fails with a domain error whose category is
  `NOT_FOUND`
- **THEN** the response has status `404`,
  `Content-Type: application/problem+json`, and a body containing `type`,
  `title`, `status`, `detail`, a machine-readable `code`, and a `traceId`

#### Scenario: Localized message via Accept-Language

- **WHEN** a client sends an error-producing request with `Accept-Language: vi`
- **THEN** the `title` and `detail` members are resolved to the Vietnamese
  message bundle while `code` remains locale-independent

#### Scenario: Type URI derivation

- **WHEN** a Problem Details response is produced and a type base URI is
  configured
- **THEN** the `type` member is that base URI followed by the kebab-cased error
  code; otherwise the `type` member is `about:blank`

### Requirement: Validation-failure semantics

When a request fails input validation, the response SHALL be a Problem Details
body with the `VALIDATION_ERROR` code and HTTP status `400`, and SHALL include
an `errors` extension member listing each field-level failure. Each entry SHALL
carry a stable `field` name and a machine-readable `code` (from a fixed set:
`REQUIRED`, `INVALID_FORMAT`, `TOO_SHORT`, `TOO_LONG`, `INVALID_VALUE`), with an
optional server-localized `message`.

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
