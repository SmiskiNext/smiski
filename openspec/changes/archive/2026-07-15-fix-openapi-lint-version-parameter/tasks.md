## 1. Shared version-parameter customizer

- [x] 1.1 Create `ApiVersionParameterOpenApiCustomizer` in
      `services/shared/src/main/java/io/github/smiskinext/shared/infrastructure/web/`
      implementing `GlobalOpenApiCustomizer`, scanning `openApi.getPaths()` and
      adding an `in: path`, `name: version`, `required: true`, integer-typed
      path parameter to every path key containing the `{version}` template
      variable, only when that parameter is not already present
- [x] 1.2 Create `OpenApiApiVersionAutoConfiguration` exposing the customizer as
      a bean, mirroring `OpenApiProblemDetailAutoConfiguration`
      (`@AutoConfiguration`, `@ConditionalOnWebApplication(SERVLET)`,
      `@ConditionalOnClass(OpenAPI.class)`)
- [x] 1.3 Register `OpenApiApiVersionAutoConfiguration` in
      `services/shared/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

## 2. Tenant nullable schema fix

- [x] 2.1 Add `nullable = true` to the `@Schema` of each `@Nullable` field in
      `TenantResponse` (`appVersion`, `environmentId`, `siteUrl`,
      `installerAccountId`, `uninstalledAt`, `purgeAfter`)
- [x] 2.2 Add `nullable = true` to the `@Schema` of each `@Nullable` field in
      `UninstallTenantResponse` (same field set)

## 3. Tests

- [x] 3.1 Add a unit test for `ApiVersionParameterOpenApiCustomizer` (mirroring
      `ProblemDetailOpenApiCustomizerTest`) asserting a `version` path parameter
      is added to a path containing `{version}` — covers scenario "Version
      parameter present on a versioned operation"
- [x] 3.2 Extend the same unit test to assert no `version` parameter is added to
      a path without `{version}`, and that an already-present `version`
      parameter is not duplicated — covers scenarios "Non-versioned paths are
      not given a version parameter" and idempotency
- [x] 3.3 Add regression assertions in `TenantOpenApiContentTest` that the
      emitted spec marks the nullable tenant fields as nullable — covers
      scenario "Nullable tenant field validates its null example" ← (verify:
      assertions read the generated document and match the spec scenarios)

## 4. Regenerate and verify

- [x] 4.1 Regenerate specs: `pnpm run openapi:generate` (or per-service
      `generateOpenApiDocsFromTests` for tenant, meet, record)
- [x] 4.2 Run `pnpm run openapi:lint`; confirm exit 0 with zero errors and zero
      warnings for tenant and meet ← (verify: 3 `path-parameters-defined` errors
      gone, tenant nullable warnings gone; record `ProblemDetail` warning
      acceptable and out of scope)
- [x] 4.3 Run `./services/gradlew -p services/shared build` to confirm shared
      module compiles, tests pass, and Spotless is satisfied ← (verify: build
      green, no formatting violations)
