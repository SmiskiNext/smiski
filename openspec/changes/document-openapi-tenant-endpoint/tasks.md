## 1. Shared Problem Details documentation

- [x] 1.1 Add an annotated `ProblemDetailSchema` type in
      `services/shared/.../infrastructure/web` declaring `type`, `title`,
      `status`, `detail`, `code`, `traceId`, and `errors` (array of violation
      entries with `field`, `code` fixed enum
      `REQUIRED`/`INVALID_FORMAT`/`TOO_SHORT`/`TOO_LONG`/ `INVALID_VALUE`,
      optional `message`) via `@Schema`
- [x] 1.2 Add a `GlobalOpenApiCustomizer` bean that registers the
      `ProblemDetail` schema under `components.schemas` and appends
      `405`/`415`/`500` responses (`application/problem+json`, `$ref` to
      `ProblemDetail`) to every operation that does not already declare that
      status
- [x] 1.3 Add `OpenApiProblemDetailAutoConfiguration`
      (`@ConditionalOnClass(OpenAPI.class)` +
      `@ConditionalOnWebApplication(SERVLET)`) exposing the customizer bean, and
      register it in
      `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
      ← (verify: bean auto-detected by springdoc; imports file lists the new
      config)

## 2. Tenant endpoint annotations

- [x] 2.1 Annotate `TenantController.register` with `@Operation` (summary,
      description) and `@ApiResponse` for `201 Created` (with `Location`) and
      `200 OK`, each `application/json` referencing `TenantResponse` with an
      example
- [x] 2.2 Add `@ApiResponse` for `400 Bad Request` referencing the shared
      `ProblemDetail` schema with distinct examples for `VALIDATION_ERROR`
      (populated `errors` array), `MALFORMED_REQUEST`, and
      `MISSING_TENANT_CONTEXT`
- [x] 2.3 Add `@Schema` descriptions and examples to `RegisterTenantRequest`
      (including nested `App`/`Environment`) and `TenantResponse` properties ←
      (verify: request/response models self-describing; `created` stays hidden)

## 3. Environment type boundary fix

- [x] 3.1 Add a Bean Validation constraint on
      `RegisterTenantRequest.environmentType` that accepts null/blank or a
      case-insensitive `EnvironmentType` name, rejecting unknown values so they
      fail as `400 VALIDATION_ERROR` (field `environmentType`) instead of `500`
- [x] 3.2 Verify `resolveEnvironmentType()` still defaults null/blank to
      `PRODUCTION` after the constraint is added ← (verify: invalid value → 400
      VALIDATION_ERROR; null/blank → PRODUCTION)

## 4. Regenerate spec

- [x] 4.1 Run
      `./services/gradlew -p services/ tenant generateOpenApiDocsFromTests` and
      commit the regenerated `services/tenant/openapi.yaml`
- [x] 4.2 Run `./services/gradlew spotlessApply` ← (verify: openapi.yaml shows
      201/200 with TenantResponse, 400 with three examples, and 405/415/500
      referencing ProblemDetail)

## 5. Tests

- [x] 5.1 Shared customizer test: an arbitrary/sample operation in the generated
      document declares `405`, `415`, `500` as `application/problem+json`
      referencing `ProblemDetail` (spec: Common error responses applied to every
      operation)
- [x] 5.2 Generation/spec assertion test: tenant `openapi.yaml` contains the
      `ProblemDetail` schema with
      `type`/`title`/`status`/`detail`/`code`/`traceId`/ `errors`, and every
      error response references it via `$ref` (spec: Reusable Problem Details
      schema)
- [x] 5.3 Generation/spec assertion test: `POST /tenants` operation documents
      `201` and `200` with `TenantResponse` + examples, and `400` referencing
      `ProblemDetail` with `VALIDATION_ERROR`/`MALFORMED_REQUEST`/
      `MISSING_TENANT_CONTEXT` examples (spec: Endpoint-specific responses
      documented)
- [x] 5.4 Controller integration regression test: invalid `environmentType`
      returns `400` `application/problem+json` with `code` `VALIDATION_ERROR`
      and an `errors` entry for `environmentType` (spec: Invalid enum request
      field; bug-fix regression)
- [x] 5.5 Controller integration test: omitted/blank `environmentType` is
      accepted and defaults to `PRODUCTION` (spec: Absent environment type keeps
      default) ← (verify: all openapi scenarios and the enum-boundary scenarios
      pass)
