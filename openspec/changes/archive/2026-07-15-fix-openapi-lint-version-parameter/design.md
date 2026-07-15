## Context

Each service emits its own OpenAPI document from a `@SpringBootTest` that calls
springdoc's `/v3/api-docs.yaml` endpoint (`OpenApiGenerationSupport` in
`services/shared` testFixtures). `pnpm run openapi` regenerates and lints these
documents with redocly (`redocly.yaml` extends `recommended`).

Versioned URLs (`/api/{version}/...`) are produced by
`ApiPathPrefixAutoConfiguration` (`services/shared`), which calls
`PathMatchConfigurer#addPathPrefix("/api/{version}", ...)` for every controller
under `io.github.smiskinext`. This pairs with Spring Framework 7 native API
versioning configured as `spring.mvc.apiversion.use.path-segment: 1`: the
version integer sits at path-segment index 1, and controllers declare only the
resource path (e.g. `@PostMapping("/tenants")`) with no `@PathVariable` for
version.

The `redocly recommended` ruleset runs `path-parameters-defined` at **error**
severity. springdoc renders `{version}` into the path template but emits no
matching parameter object (there is no `@PathVariable version`), so redocly
reports three blocking errors. Separately, `TenantResponse` /
`UninstallTenantResponse` fields annotated with jspecify `@Nullable` render as
non-nullable `type: string`; springdoc does not translate jspecify nullability,
so the hand-written `null` examples violate the schema
(`no-invalid-media-type-examples`, warning severity).

Per `services/AGENTS.md`, the documented URL contract is
`/api/{version}/path/to/resource` with `{version}` an integer at path-segment
index 1. The `openapi` capability spec already governs what the emitted document
must contain (ProblemDetail schema, common responses, self-describing models).

## Goals / Non-Goals

**Goals:**

- `pnpm run openapi:lint` exits 0 (zero errors) after regeneration.
- The `{version}` path parameter is defined for every versioned operation across
  all services, without per-controller boilerplate.
- Tenant response `null` examples validate against their schema.
- The fix lives in one shared place and covers current and future services.

**Non-Goals:**

- No change to the versioning strategy, URL shape, or API contract.
- No change to `ApiPathPrefixAutoConfiguration` or controller mappings.
- Not resolving the record `ProblemDetail` unused-component warning (warnings do
  not fail the lint gate; deferred).
- No system-wide jspecify → nullable mapping (scoped to tenant DTOs only).

## Decisions

### Decision 1: Inject the `version` path parameter via a shared `GlobalOpenApiCustomizer`

Add `ApiVersionParameterOpenApiCustomizer implements GlobalOpenApiCustomizer` in
`services/shared/.../infrastructure/web`. It scans `openApi.getPaths()`, and for
every path key containing the `{version}` template variable, adds a path-level
parameter (`in: path`, `name: version`, `required: true`,
`schema: { type: integer }`, with an example) when the operations do not already
declare it. Registered through a new `OpenApiApiVersionAutoConfiguration` bean,
mirroring `OpenApiProblemDetailAutoConfiguration`, and added to the shared
`AutoConfiguration.imports`.

**Why over alternatives:**

- **Alternative A — switch to header/query versioning** (URL becomes
  `/api/tenants` + `X-API-Version`): eliminates `{version}` entirely, but
  changes the public URL contract and violates the `services/AGENTS.md`
  path-segment convention. Rejected: architectural change, not a lint fix.
- **Alternative B — declare `{version}` + `@PathVariable` in every controller**:
  springdoc would emit the parameter natively, but every method must repeat the
  `{version}` template and accept an unused `@PathVariable`, scattering the
  version concern across all endpoints and contradicting the current
  "controllers never repeat the `/api/{version}` prefix" philosophy. Rejected on
  maintainability: bumping/adding versions later touches every handler.
- **Alternative C (chosen) — shared customizer**: version stays a single,
  centralized concern (config + one customizer). Adding a new version is a
  config change plus per-mapping `version` attributes where behavior differs;
  new endpoints need zero version boilerplate. Matches the existing
  `ProblemDetailOpenApiCustomizer` pattern. springdoc's own path-segment support
  is still immature (issues springdoc/springdoc-openapi#3163, #3258), so
  post-processing the emitted document is the pragmatic, framework-aligned
  choice.

### Decision 2: Parameter type is `integer`

The configured resolver reads an integer version at path-segment index 1
(`spring.mvc.apiversion.use.path-segment: 1`, `default: 1`, `supported: 1`), and
`services/AGENTS.md` states `{version}` is "an integer at path-segment index 1".
The parameter schema is therefore `type: integer`, not string.

### Decision 3: Mark tenant nullable fields with `@Schema(nullable = true)`

Add `nullable = true` to the `@Schema` of each jspecify `@Nullable` field in
`TenantResponse` and `UninstallTenantResponse` (`appVersion`, `environmentId`,
`siteUrl`, `installerAccountId`, `uninstalledAt`, `purgeAfter`). This is a
scoped, explicit, per-field change rather than a repo-wide `ModelConverter`
mapping jspecify to nullable — narrow blast radius, no impact on other DTOs.

## Risks / Trade-offs

- [Customizer runs before/after other `GlobalOpenApiCustomizer`s in unknown
  order] → Behavior is idempotent: it only adds the parameter when absent, keyed
  by `in: path` + `name: version`. Order-independent.
- [Paths without `{version}` (actuator, springdoc) accidentally get a parameter]
  → Guard strictly on the literal `{version}` substring in the path key; only
  application paths carry it because the prefix is scoped to the base package.
- [Future services rely on this behavior implicitly] → It is auto-configured in
  `shared` exactly like the ProblemDetail customizer, so every service inherits
  it automatically; documented in the `openapi` capability spec.
- [springdoc later emits the parameter natively and duplicates it] → The
  presence guard prevents duplication; if springdoc adds it, the customizer
  becomes a no-op and can be removed.

## Migration Plan

1. Add the customizer + auto-configuration + imports entry in `shared`.
2. Add `nullable = true` to the tenant response DTOs.
3. Regenerate `openapi.yaml` for tenant/meet/record.
4. Run `pnpm run openapi:lint`; confirm zero errors.
5. Rollback is trivial: revert the shared files and DTO annotations, regenerate.
