## Context

The `services/` monorepo hosts Spring Boot 4 / Java 25 microservices (`tenant`,
`meet`, `record`, `notification`) built as separate Gradle `includeBuild`s and
sharing convention plugins in `build-logic/`. Hexagonal + DDD layering
(`domain → application → infrastructure → presentation`) is enforced by ArchUnit
through the shared `CleanArchitectureTest`.

Current test reality per service is thin: an `architecture/ArchitectureTest`, a
`{Service}ApplicationTests` context-load, a `{Service}OpenApiGenerationTest`,
and a `config/TestcontainersConfiguration`. All live in the single `test` source
set, which mixes fast pure-JVM tests with slow `@SpringBootTest` +
Testcontainers tests. There is no coverage measurement and no mutation testing,
so business-logic regressions and weak assertions go undetected.

Constraints:

- Java 25 toolchain (`libs.versions.toml` `java = "25"`); tooling must support
  Java 25 bytecode.
- Build convention lives in `build-logic/` Kotlin-DSL precompiled script
  plugins; `build-logic` itself compiles with a JDK 21 toolchain, so any plugin
  dependency must be resolvable there.
- Domain must stay framework-agnostic (ArchUnit-enforced) — domain unit tests
  must not pull Spring onto the domain classpath.
- Spotless formats `**/src/**/*.java`; new test source sets are auto-covered by
  the existing `src/**` target.

## Goals / Non-Goals

**Goals:**

- A single, layer-aware testing strategy documented and enforced across all
  services.
- Separate fast `test` (unit + application, no containers) from
  `integrationTest` (Testcontainers, `@SpringBootTest`).
- One reusable `test.base` convention plugin wiring source sets + JaCoCo + PIT,
  so services stay boilerplate-free.
- JaCoCo coverage reporting with a verification gate; PIT mutation testing on
  `domain` + `application` with a mutation-score gate.
- Latest Java-25-compatible tool versions pinned centrally in the version
  catalog.

**Non-Goals:**

- Writing exhaustive test suites for existing business logic (this change
  delivers the harness + conventions + a reference example, not full coverage
  backfill).
- Changing any production/runtime code or the ArchUnit rules themselves.
- CI pipeline wiring (GitHub Actions/Jenkins); this change defines Gradle
  tasks/gates that CI can later call.
- Contract testing (Pact), performance/load testing, or E2E across services.

## Decisions

### Decision 1: Two test source sets — `test` (fast) + `integrationTest` (slow)

`test` holds pure-domain unit tests, application use-case tests (ports mocked
with Mockito), and ArchUnit design tests — no containers, no Spring context
(except ArchUnit's static analysis). `integrationTest` holds `@SpringBootTest`,
adapter tests against real dependencies via Testcontainers, and the OpenAPI
generation test.

Rationale: PIT mutation testing and the fast CI feedback loop must run without
spinning Testcontainers (minutes per module). Separating source sets is the
standard Gradle approach and lets `pitest` bind to the fast `test` task only.

Alternatives considered:

- **JUnit tags + single source set** (`@Tag("integration")` with
  `test { excludeTags }`): less build config, but keeps slow deps on the unit
  classpath, muddies coverage attribution, and PIT still sees integration
  classes. Rejected for weaker isolation.
- **Separate Gradle subprojects per test type**: maximal isolation but heavy for
  a per-service `includeBuild`. Rejected as overkill.

### Decision 2: A new `io.github.smiskinext.plugin.test.base` convention plugin

The `integrationTest` source set, its `Test` task, JaCoCo config, and PIT config
are declared once in a precompiled script plugin and applied by every service.
Service `build.gradle.kts` only adds the plugin id.

Rationale: mirrors the existing `service.base` / `jvm.base` pattern; centralizes
tool versions and gates; avoids drift across four services. ArchUnit's
`CleanArchitectureTest` and `@SpringBootTest` reference services already prove
the shared-fixture pattern works.

Alternatives: copy config into each `build.gradle.kts` (rejected — duplication,
drift).

### Decision 3: JaCoCo latest (0.8.15) via `toolVersion` override

Apply the built-in Gradle `jacoco` plugin and set
`jacoco { toolVersion = <catalog> }`. JaCoCo 0.8.14 added Java 25 support;
0.8.15 supports Java 26 bytecode. Coverage is measured from the fast `test`
task; `jacocoTestReport` produces HTML+XML, and `jacocoTestCoverageVerification`
enforces thresholds.

Rationale: the bundled Gradle JaCoCo default lags behind and would fail on Java
25 bytecode. Overriding `toolVersion` is the supported mechanism.

### Decision 4: PIT via `info.solidsoft.pitest` 1.19.0, core 1.25.7, JUnit5 plugin

Apply `info.solidsoft.pitest` in the convention plugin; override `pitestVersion`
to the latest core (Java-25-capable) and add `pitest-junit5-plugin` on the
`pitest` configuration (the project is JUnit 5 / Jupiter). Scope `targetClasses`
to `..domain.*` and `..application.*`; exclude `*Config`, `*Configuration`,
`*JpaEntity`, `*Application`, generated classes. Bind PIT to the fast `test`
task.

Rationale: mutation testing yields the most signal on domain rules and use-case
orchestration, and running it against integration tests would be prohibitively
slow. Plugin defaults to an older core, so the version is pinned explicitly.

Risk: `build-logic` compiles under JDK 21; the plugin dependency must resolve
there (it targets JDK 17+, so it does). PIT execution uses the project's Java 25
toolchain at runtime.

### Decision 5: Quality gates — realistic starting thresholds

- JaCoCo: 70% line, 60% branch coverage verification (bundle-level to start),
  excluding config/entity/application-bootstrap and generated classes.
- PIT: 60% mutation-score threshold on `domain` + `application` to start, to be
  raised as suites mature.

Gates are configurable in the convention plugin and start non-blocking-friendly
(documented as ratcheting up). Rationale: unrealistic gates (100%) get disabled;
a modest baseline that ratchets upward is sustainable.

### Decision 6: Migrate existing Testcontainers tests into `integrationTest`

`{Service}ApplicationTests`, `{Service}OpenApiGenerationTest`, and
`config/TestcontainersConfiguration` move to `src/integrationTest/java`.
`architecture/ArchitectureTest` stays in `src/test/java`. The
`generateOpenApiDocsFromTests` task is repointed to the `integrationTest`
classpath.

Rationale: these are container-backed / full-context tests by nature; keeping
them in `test` would defeat the fast-suite separation and slow PIT.

### Test layout (per service)

```text
src/test/java/.../<name>/                # FAST — no containers, PIT + JaCoCo target
  domain/            # pure unit tests: aggregates, value objects, domain services
  application/       # use-case tests: *ApplicationService with mocked ports (Mockito)
  architecture/      # ArchitectureTest extends CleanArchitectureTest
src/integrationTest/java/.../<name>/     # SLOW — Testcontainers + @SpringBootTest
  config/            # TestcontainersConfiguration
  infrastructure/    # adapter tests: RepositoryAdapter, KafkaEventPublisher (real deps)
  presentation/      # @WebMvcTest / @SpringBootTest controller + problem+json tests
  {Service}ApplicationTests.java
  {Service}OpenApiGenerationTest.java
src/integrationTest/resources/application-test.yaml
```

### Gradle wiring (convention plugin)

```mermaid
sequenceDiagram
    participant Svc as service build.gradle.kts
    participant TB as plugin.test.base
    participant JC as jacoco plugin
    participant PIT as info.solidsoft.pitest
    Svc->>TB: apply id
    TB->>TB: register integrationTest sourceSet + Test task
    TB->>JC: apply + set toolVersion, configure report + verification
    TB->>PIT: apply + set pitestVersion, targetClasses, threshold, bind to test
    Note over TB: check dependsOn integrationTest; jacocoTestReport after test
```

## Risks / Trade-offs

- **PIT on Java 25 is early in real-world use** → Pin the newest core version;
  keep the gate modest; PIT runs on fast tests only so failures are quick to
  diagnose. If PIT proves unstable on Java 25, the `pitest` task can be excluded
  from `check` while `jacoco` still gates.
- **`build-logic` JDK 21 vs runtime Java 25** → The PIT Gradle plugin only needs
  to compile/resolve under JDK 21 (it supports 17+); mutation runs use the
  project toolchain (25). Verified against plugin requirements.
- **Moving tests to `integrationTest` breaks the OpenAPI generation task** →
  Repoint `generateOpenApiDocsFromTests` to the `integrationTest` source set
  output/classpath in the same change and verify `openapi.yaml` still
  regenerates.
- **Coverage gate blocking builds before suites exist** → Start with modest,
  documented thresholds and exclude bootstrap/config/entity classes so the
  baseline is achievable; ratchet up over time.
- **JaCoCo + PIT bytecode interaction** → PIT runs as its own task with its own
  JVM; JaCoCo instrumentation is scoped to the `test` task's report, avoiding
  simultaneous transformation conflicts.

## Migration Plan

1. Add versions to `libs.versions.toml` (jacoco tool, pitest core,
   pitest-junit5, `info.solidsoft.pitest` plugin) and the plugin dependency to
   `build-logic/build.gradle.kts`.
2. Create `plugin.test.base` convention plugin; wire source sets, JaCoCo, PIT,
   and `check` dependencies.
3. Apply the plugin to `tenant`, `meet`, `record`, `notification`; move
   container/context tests into `integrationTest`; repoint OpenAPI task.
4. Add a reference unit test (domain) and a reference use-case test (application
   with mocked ports) in one service (`meet`) to prove the fast suite + PIT
   path.
5. Run `test`, `integrationTest`, `jacocoTestReport`,
   `jacocoTestCoverageVerification`, `pitest`, and
   `generateOpenApiDocsFromTests` per service; run Spotless.
6. Update `services/AGENTS.md` with the layout, commands, and gates.

Rollback: the change is additive (new plugin, new source set, tooling).
Reverting the plugin application and moving tests back to `test` restores prior
behavior; no runtime code is touched.

## Open Questions

- Should coverage/mutation gates be enforced in `check` immediately or run in a
  separate `qualityGate` task until suites are backfilled? (Default: wire into
  `check` but with modest thresholds; revisit if it blocks unrelated work.)
- Aggregated cross-service coverage report — needed now, or per-service reports
  sufficient? (Default: per-service now; aggregation deferred, since services
  are independent `includeBuild`s.)
