## Why

The backend is API-first (each service emits its own OpenAPI spec), yet the
cross-service HTTP contract — URL versioning, resource/action naming, the JSend
response envelope, and error semantics — lives only as prose in
`services/AGENTS.md`. `openspec/specs/api-convention/spec.md` is empty, so
design reviews and new services have no authoritative, testable behavioral
contract to reference. Config already instructs authors to read this spec before
making API decisions; it needs real content.

## What Changes

- Establish the `api-convention` capability spec as the authoritative behavioral
  contract for all HTTP APIs across services.
- Define versioned URL scheme requirements: `/api/{version}/path/to/resource`,
  version as an integer at path-segment index 1, prefix applied globally so
  controllers never repeat `api` or the version.
- Define RESTful resource-path mapping for standard methods and the `:action`
  suffix convention (POST) for non-CRUD operations.
- Define the JSend response envelope as the required shape for every response
  (success, fail, error) and how clients unwrap it.
- Define machine-readable error codes and validation-failure (`fail`) semantics.
- Define exclusions: Actuator and other non-`@RestController` endpoints stay
  unprefixed.

## Capabilities

### New Capabilities

- `api-convention`: The cross-service HTTP API contract — URL versioning scheme,
  resource and action naming, the JSend response envelope, error codes, and
  validation-failure semantics that every service's REST API must satisfy.

### Modified Capabilities

<!-- None: no existing spec's requirements change. -->

## Impact

- Spec: populates `openspec/specs/api-convention/spec.md` (currently empty).
- Referenced by: `openspec/config.yaml` design rule ("Read
  openspec/specs/api-convention/spec.md before making API decisions").
- Documents behavior already implemented by `services/shared`
  (`ApiPathPrefixAutoConfiguration`, `JsendResponse`, `GlobalExceptionHandler`,
  `ErrorCode`) and every service's `presentation/` controllers. No code changes;
  this is a documentation/contract artifact.
