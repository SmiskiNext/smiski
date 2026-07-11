## Why

The backend services enforce hexagonal + DDD layering with ArchUnit, but the
only tests that exist per service are architecture rules, a context-load smoke
test, and OpenAPI generation. There are no domain unit tests, no use-case tests,
no adapter integration tests, no code-coverage measurement, and no way to detect
weak assertions. Without a shared, layer-aware test convention and objective
quality gates, regressions in business logic ship undetected and test quality is
invisible.

## What Changes

- Define a testing strategy aligned to the hexagonal layers (domain,
  application, infrastructure adapters, presentation) with a clear test pyramid:
  fast pure-domain unit tests, use-case tests with mocked ports,
  Testcontainers-backed adapter integration tests, ArchUnit design tests, and
  full context tests.
- Separate the Gradle `test` source set (fast unit + application tests, no
  containers) from a new `integrationTest` source set (Testcontainers,
  `@SpringBootTest`), so mutation testing and CI can target fast tests
  independently.
- Add a reusable Gradle convention plugin
  `io.github.smiskinext.plugin.test.base` in `build-logic/` that wires the
  source sets, JaCoCo, and PIT once and applies to every service, keeping
  per-service `build.gradle.kts` minimal.
- Add **JaCoCo** (latest, Java 25 compatible) for line/branch coverage reporting
  with an aggregated report and configurable coverage-verification thresholds.
- Add **PIT (pitest)** mutation testing (latest, Java 25 compatible) via
  `info.solidsoft.pitest`, targeting domain + application packages with the
  JUnit 5 plugin, plus a starting mutation-score threshold.
- Pin the new tooling versions (`jacoco`, `pitest`, `pitest-junit5`,
  `pitest-gradle-plugin`) in `gradle/libs.versions.toml`.
- Document the conventions in `services/AGENTS.md` (test layout, source sets,
  coverage/mutation commands and gates).

## Capabilities

### New Capabilities

- `test-architecture`: Layer-aware testing strategy for the hexagonal services —
  test types per layer, source-set separation, naming/placement conventions, and
  coverage + mutation-score quality gates enforced through shared Gradle
  convention plugins.

### Modified Capabilities

<!-- None. api-convention and db-schema requirements are unchanged. -->

## Impact

- **Build**: New convention plugin in `build-logic/`;
  `build-logic/build.gradle.kts` gains the PIT plugin dependency; each service
  `build.gradle.kts` applies the new test plugin.
- **Dependencies**: `gradle/libs.versions.toml` gains JaCoCo tool version, PIT
  core, `pitest-junit5-plugin`, and `info.solidsoft.pitest` plugin entries.
- **Services**: `meet`, `tenant`, `record`, `notification` gain the
  `integrationTest` source set; existing Testcontainers-based tests
  (`*ApplicationTests`, OpenAPI generation) move into it, while ArchUnit and
  pure unit tests stay in `test`.
- **Shared**: `services/shared/src/testFixtures` may gain small test-support
  helpers; existing `CleanArchitectureTest` and container support are unchanged.
- **CI / docs**: `services/AGENTS.md` documents the new test layout and commands
  (`test`, `integrationTest`, `jacocoTestReport`,
  `jacocoTestCoverageVerification`, `pitest`).
- **No production/runtime code changes**: purely test, build, and tooling
  additions.
