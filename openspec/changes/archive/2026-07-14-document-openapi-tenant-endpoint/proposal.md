## Why

The generated `services/tenant/openapi.yaml` is nearly empty: the only
documented response is a bare `200` with an untyped `object` body, because
`TenantController` returns `ResponseEntity<Object>` and carries no OpenAPI
annotations. Consumers of the tenant API (the Forge app and the API gateway)
have no machine-readable contract for the real success shapes (`201`/`200` with
`TenantResponse`) nor for any error shape. Since tenant is the first service in
the monorepo to expose a controller, this is also the moment to establish a
reusable OpenAPI documentation pattern for the shared `application/problem+json`
error responses so every future controller inherits it without boilerplate.

## What Changes

- Introduce a reusable `ProblemDetail` OpenAPI schema in `services/shared` that
  documents the RFC 9457 body plus the extension members (`code`, `traceId`,
  `errors[]`) produced by the shared `GlobalExceptionHandler` and
  `ProblemDetailMapper`.
- Introduce a shared springdoc `GlobalOpenApiCustomizer` in `services/shared`
  that automatically attaches the common error responses (`405`, `415`, `500`) —
  all emitted by the shared `GlobalExceptionHandler` — to every operation of
  every service, referencing the shared `ProblemDetail` schema.
- Annotate `TenantController.register` with springdoc
  `@Operation`/`@ApiResponse` metadata and examples for its endpoint-specific
  responses: `201 Created` (first install, with `Location`), `200 OK`
  (reinstall/redelivery), and `400` covering `VALIDATION_ERROR` (with
  `errors[]`), `MALFORMED_REQUEST`, and `MISSING_TENANT_CONTEXT`.
- Annotate `RegisterTenantRequest` and `TenantResponse` with `@Schema`
  descriptions and examples so request/response models are self-describing.
- Fix the latent defect where an unrecognized `environmentType` value causes an
  uncaught `IllegalArgumentException` and a `500` response; validate it at the
  request boundary so an invalid value yields `400 VALIDATION_ERROR` while
  `null`/blank still defaults to `PRODUCTION`.
- Regenerate `services/tenant/openapi.yaml` from the `@SpringBootTest`
  generation test (spec is generated, never hand-edited).

## Capabilities

### New Capabilities

- `openapi`: Cross-service OpenAPI documentation contract — how controllers,
  request/response models, and the shared error responses are annotated so each
  service emits a complete, accurate `openapi.yaml`, including the reusable
  `application/problem+json` error schema and the common `405`/`415`/`500`
  responses applied to every operation.

### Modified Capabilities

<!-- No spec-level requirement changes to existing capabilities. The api-convention
     spec already defines the HTTP/error contract; this change documents that
     contract in OpenAPI without altering it. -->

## Impact

- `services/shared/src/main/java/io/github/smiskinext/shared/infrastructure/web/`
  — new `ProblemDetail` schema type + `GlobalOpenApiCustomizer`; register in
  `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
  if a new auto-configuration is added.
- `services/tenant/.../presentation/TenantController.java` — OpenAPI annotations
  and examples.
- `services/tenant/.../presentation/request/RegisterTenantRequest.java` — schema
  annotations + `environmentType` boundary validation (behavior fix: `500` →
  `400`).
- `services/tenant/.../application/response/TenantResponse.java` — schema
  annotations.
- `services/tenant/openapi.yaml` — regenerated artifact.
- Tests: tenant controller/generation integration tests; shared customizer test.
- Dependencies: none added (springdoc + swagger-annotations already on the
  classpath via the `service.base` convention plugin).
