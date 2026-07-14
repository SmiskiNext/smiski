## Context

`services/tenant/openapi.yaml` is generated at build time by
`TenantOpenApiGenerationTest` (extends the shared `OpenApiGenerationSupport`),
which boots the service via `@SpringBootTest(RANDOM_PORT)`, fetches springdoc's
`/v3/api-docs.yaml`, and writes it to the project root. The spec is therefore a
build artifact — it is never edited by hand; hand edits would be overwritten by
the `generateOpenApiDocsFromTests` Gradle task.

Today the emitted spec is nearly empty because `TenantController.register`
returns `ResponseEntity<Object>` and carries no OpenAPI annotations, so
springdoc infers only a bare `200` with an untyped body. Meanwhile the real HTTP
contract is richer and fully implemented:

- Success: `201 Created` (first install, with `Location`) and `200 OK`
  (reinstall/redelivery), both bodied with `TenantResponse`.
- Failure: `400` for `VALIDATION_ERROR` (with `errors[]`), `MALFORMED_REQUEST`,
  and the domain `MISSING_TENANT_CONTEXT`; plus `405`/`415`/`500` produced
  uniformly by the shared `GlobalExceptionHandler`.

All errors are RFC 9457 `application/problem+json` bodies built by
`ProblemDetailMapper`, carrying extension members `code`, `traceId`, and (for
validation) `errors[]`. This contract is already normative in
`openspec/specs/api-convention/spec.md`; this change documents it in OpenAPI
without altering runtime behavior — except for one boundary-validation fix (see
Decisions).

tenant is the first service in the monorepo to expose a controller (meet/record
have none yet), so the shared error-response documentation established here
becomes the template every future controller inherits.

Constraints:

- springdoc-openapi (`3.0.3`) + `swagger-annotations` are already on the
  classpath via the `service.base` convention plugin — no new dependencies.
- Hexagonal/ArchUnit rules: `@RestController` only in `presentation`; domain
  stays framework-agnostic; the shared schema/customizer live in
  `shared.infrastructure.web` alongside `ProblemDetailMapper`.
- The shared error responses are emitted by shared infrastructure, so their
  documentation must also be shared to stay DRY and consistent.

## Goals / Non-Goals

**Goals:**

- Emit a complete, accurate `services/tenant/openapi.yaml`: success `201`/`200`
  with `TenantResponse`, `400` with the three endpoint error variants, and the
  common `405`/`415`/`500` — all with examples for documented statuses.
- Define the `application/problem+json` body once as a reusable `ProblemDetail`
  schema in `services/shared`, referenced by `$ref`.
- Attach the common `405`/`415`/`500` responses to every operation of every
  service automatically, without per-controller boilerplate.
- Fix the `environmentType` boundary defect: invalid value → `400`
  `VALIDATION_ERROR` instead of `500`.

**Non-Goals:**

- No change to the versioned URL scheme, no new endpoints, no `X-Tenant-ID`
  header documentation in the spec.
- No changes to the `api-convention` spec requirements (behavior is unchanged;
  only its OpenAPI documentation is added).
- No changes to meet/record services.
- No enabling of swagger-ui (stays disabled per `application.yaml`).

## Decisions

### D1: Document the shared error contract in `services/shared`, not per controller

The `405`/`415`/`500` responses (and the malformed/validation `400`s) are all
produced by the shared `GlobalExceptionHandler`; their body is the shared
`ProblemDetail`. Documenting them once in `shared` keeps the OpenAPI contract in
lockstep with the code that produces it and prevents divergence across future
controllers.

- **Mechanism**: a springdoc `GlobalOpenApiCustomizer` bean (interface
  `org.springdoc.core.customizers.GlobalOpenApiCustomizer`, auto-detected by
  springdoc). Its `customise(OpenAPI)` registers the `ProblemDetail` schema
  under `components.schemas` and appends `405`/`415`/`500` responses
  (referencing `#/components/schemas/ProblemDetail`, media type
  `application/problem+json`) to every operation that does not already declare
  them.
- **Wiring**: expose the bean via a new `@AutoConfiguration`
  (`OpenApiProblemDetailAutoConfiguration`) guarded by
  `@ConditionalOnClass(OpenAPI.class)` and
  `@ConditionalOnWebApplication(SERVLET)`, mirroring
  `OpenApiServerAutoConfiguration`; register it in
  `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
- **Alternatives considered**:
    - Per-controller `@ApiResponses` for the common errors → rejected:
      duplicated on every future controller, drifts from the shared handler.
    - A springdoc `OperationCustomizer` → viable, but `GlobalOpenApiCustomizer`
      lets us register the schema and iterate operations in one place with
      access to the whole document.

### D2: Represent `ProblemDetail` as an annotated schema type, not raw YAML

Introduce a small annotated class (e.g. `ProblemDetailSchema`) in
`shared.infrastructure.web` used only to drive schema generation, so the
`type`/`title`/`status`/`detail`/`code`/`traceId`/`errors` members and the
`Violation` entry shape (with the fixed `code` enum) are declared once with
`@Schema` and resolved by springdoc's `ModelConverters`. The customizer
registers the resolved schema into `components.schemas`.

- **Rationale**: keeps the documented shape colocated with the shared web layer,
  reuses springdoc's converter, and avoids hand-authoring OpenAPI YAML (which
  the generator would ignore/overwrite anyway).
- **Alternative**: build `io.swagger.v3.oas.models.media.Schema` objects
  programmatically in the customizer → more verbose and error-prone; used only
  if the annotated-type approach fights the converter.

### D3: Endpoint-specific docs live on `TenantController` + models

`TenantController.register` gets `@Operation` plus `@ApiResponse` entries for
`201`, `200`, and `400` (with `examples` for `VALIDATION_ERROR`,
`MALFORMED_REQUEST`, `MISSING_TENANT_CONTEXT`, the `400` body referencing the
shared `ProblemDetail`). `RegisterTenantRequest` and `TenantResponse` get
`@Schema` descriptions/examples. `TenantResponse.created` is already
`@JsonIgnore` so it stays out of the schema.

### D4: Fix `environmentType` at the request boundary via Bean Validation

`RegisterTenantRequest.resolveEnvironmentType()` calls
`EnvironmentType.valueOf(environmentType.toUpperCase())`, which throws
`IllegalArgumentException` for unknown values → currently surfaces as `500`. Add
a Bean Validation constraint on the `environmentType` field so an invalid value
fails as `400 VALIDATION_ERROR` (field `environmentType`) via the existing
`handleMethodArgumentNotValid` path, while `null`/blank still defaults to
`PRODUCTION`.

- **Approach**: a field-level constraint that accepts null/blank or a
  case-insensitive match to an `EnvironmentType` name. Preferred: a small custom
  constraint (e.g. `@ValidEnum(EnvironmentType.class)`) kept in the presentation
  layer for reuse and clarity; the validator returns `true` for null/blank so
  the optional-default semantics are preserved. `@Pattern` is rejected because
  it would duplicate enum names as a brittle regex and not match
  case-insensitively cleanly.
- **Violation code**: the constraint maps through
  `GlobalExceptionHandler.resolveViolationCode`; its default is `INVALID_VALUE`,
  which is the correct machine-readable code for an out-of-set enum value.

## Risks / Trade-offs

- [Global customizer touches every operation] → Only append responses when the
  operation does not already declare that status, so endpoint-specific overrides
  win; cover with a shared customizer unit/slice test.
- [springdoc converter may emit `ProblemDetail` differently than expected] →
  Assert the generated tenant `openapi.yaml` contains the schema and the
  `405/415/500` + `400` references in the generation/controller integration
  test.
- [Non-deterministic spec output] → Server list is already pinned by
  `OpenApiServerAutoConfiguration`; keep examples static so regeneration is
  stable and diff-friendly.
- [Behavior change for `environmentType`] → Narrow and strictly an improvement
  (`500` → `400`); guarded by explicit tests for invalid value and
  blank/default.
- [ArchUnit naming/placement] → New shared types stay in
  `shared.infrastructure.web`; the custom constraint stays in tenant
  `presentation`; run tenant + shared test suites.

## Migration Plan

1. Add the shared `ProblemDetail` schema type + `GlobalOpenApiCustomizer` +
   auto-configuration; register in the imports file.
2. Add the `environmentType` boundary constraint in tenant presentation.
3. Annotate `TenantController`, `RegisterTenantRequest`, `TenantResponse`.
4. Run tenant `integrationTest` (controller + generation) and shared `test`.
5. Run `generateOpenApiDocsFromTests` to regenerate
   `services/tenant/openapi.yaml`; commit the regenerated artifact.
6. `spotlessApply`.

Rollback: revert the shared and tenant source changes and re-run the generation
task to restore the previous `openapi.yaml`. No data or schema migration is
involved.

## Open Questions

- None. The customizer interface (`GlobalOpenApiCustomizer.customise(OpenAPI)`),
  the springdoc/swagger-annotations availability, and the error-source mapping
  have all been verified against the codebase and dependency cache.
