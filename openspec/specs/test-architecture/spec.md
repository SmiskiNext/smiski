## Purpose

Defines the layer-aware testing strategy every backend service must satisfy for
the hexagonal + DDD architecture: which test type belongs to each layer, the
separation of the fast `test` source set from the container-backed
`integrationTest` source set, the shared Gradle convention plugin that wires
them, and the JaCoCo coverage and PIT mutation-testing quality gates. This is
the authoritative reference for test structure, coverage measurement, and
mutation-score thresholds across services.

## Requirements

### Requirement: Layer-Aligned Test Types

Each service SHALL provide tests aligned to the hexagonal layers, with each
layer tested at the appropriate granularity. Domain logic SHALL be tested with
pure unit tests that do not load a Spring context or start containers.
Application use cases SHALL be tested against mocked outbound ports.
Infrastructure adapters SHALL be tested against real dependencies using
Testcontainers. Presentation controllers SHALL be tested for HTTP behavior
including RFC 9457 `application/problem+json` error mapping. Architectural
constraints SHALL continue to be enforced by ArchUnit.

#### Scenario: Domain unit test runs without Spring or containers

- **WHEN** a domain aggregate or value object test executes in the `test` source
  set
- **THEN** it constructs domain objects directly and asserts business rules
  without a Spring `ApplicationContext` and without starting any Testcontainer

#### Scenario: Application use-case test mocks outbound ports

- **WHEN** an `*ApplicationService` test executes
- **THEN** its domain repository ports and event publisher are provided as
  Mockito mocks and the test asserts the returned `Result<T, {Feature}Error>`
  and port interactions without a database

#### Scenario: Adapter integration test uses Testcontainers

- **WHEN** a `*RepositoryAdapter` or messaging adapter test executes in the
  `integrationTest` source set
- **THEN** it runs against a real backing service provided by the shared
  Testcontainers support (Postgres, Kafka, Valkey, or Minio) rather than mocks

#### Scenario: Architecture rules remain enforced

- **WHEN** the `test` task runs
- **THEN** the service's `ArchitectureTest` extending `CleanArchitectureTest`
  executes and fails the build on any hexagonal layering or naming violation

### Requirement: Separate Fast and Integration Source Sets

Each service SHALL separate fast tests from container-backed tests using two
Gradle source sets. The `test` source set SHALL contain only fast tests (domain
unit tests, application use-case tests, and ArchUnit tests) and SHALL NOT
require Testcontainers. A dedicated `integrationTest` source set SHALL contain
`@SpringBootTest`, Testcontainers-backed, and OpenAPI-generation tests. The
`integrationTest` source set SHALL extend the test compile and runtime
classpaths and SHALL run after `test`.

#### Scenario: Fast test task excludes containers

- **WHEN** `test` is executed for a service
- **THEN** only classes under the `test` source set run and no Testcontainer is
  started

#### Scenario: Integration task runs container-backed tests

- **WHEN** `integrationTest` is executed for a service
- **THEN** classes under the `integrationTest` source set run with access to
  main, `test`, and `integrationTest` outputs, and it is ordered to run after
  `test`

#### Scenario: Existing context and OpenAPI tests live in integrationTest

- **WHEN** the source sets are established
- **THEN** `{Service}ApplicationTests`, `{Service}OpenApiGenerationTest`, and
  `config/TestcontainersConfiguration` reside in `src/integrationTest`, while
  `architecture/ArchitectureTest` remains in `src/test`

#### Scenario: OpenAPI generation still works after migration

- **WHEN** `generateOpenApiDocsFromTests` runs after the migration
- **THEN** it uses the `integrationTest` classes and classpath and regenerates
  the service's `openapi.yaml` successfully

### Requirement: Shared Test Convention Plugin

A single Gradle convention plugin SHALL provide the `integrationTest` source
set, JaCoCo configuration, and PIT configuration; this plugin is
`io.github.smiskinext.plugin.test.base` in `build-logic/`. Every backend service
SHALL apply this plugin, and per-service `build.gradle.kts` SHALL NOT duplicate
source-set, coverage, or mutation configuration.

#### Scenario: Service applies the convention plugin

- **WHEN** a service `build.gradle.kts` applies
  `io.github.smiskinext.plugin.test.base`
- **THEN** the `integrationTest` source set and task, JaCoCo tasks, and the
  `pitest` task are available without further per-service configuration

#### Scenario: Tool versions come from the version catalog

- **WHEN** the convention plugin configures JaCoCo and PIT
- **THEN** the JaCoCo tool version, PIT core version, `pitest-junit5-plugin`
  version, and `info.solidsoft.pitest` plugin version are resolved from
  `gradle/libs.versions.toml`, not hardcoded in the plugin or services

### Requirement: Code Coverage with JaCoCo

The build SHALL measure code coverage using JaCoCo at a version that supports
Java 25 bytecode. A coverage report SHALL be produced from the fast `test` task
in both HTML and XML formats. A coverage-verification task SHALL enforce
configurable minimum line and branch thresholds, excluding bootstrap,
configuration, JPA entity, and generated classes.

#### Scenario: Coverage report generated on Java 25

- **WHEN** `jacocoTestReport` runs against Java 25 bytecode
- **THEN** JaCoCo produces HTML and XML reports without bytecode-version errors

#### Scenario: Coverage below threshold fails verification

- **WHEN** `jacocoTestCoverageVerification` runs and measured line or branch
  coverage is below the configured minimum
- **THEN** the task fails and reports which rule was violated

#### Scenario: Non-testable classes excluded from coverage

- **WHEN** coverage is computed
- **THEN** `*Application` bootstrap classes, `*Config`/`*Configuration`,
  `*JpaEntity`, and generated classes are excluded from the coverage denominator

### Requirement: Mutation Testing with PIT

The build SHALL support mutation testing using PIT (pitest) at a version that
supports Java 25, integrated with JUnit 5 via `pitest-junit5-plugin`. Mutation
testing SHALL target the `domain` and `application` packages, SHALL run against
the fast `test` task, and SHALL enforce a configurable minimum mutation-score
threshold.

#### Scenario: Mutation run targets domain and application

- **WHEN** the `pitest` task runs for a service
- **THEN** it mutates classes under `..domain..` and `..application..` and
  excludes configuration, JPA entity, application-bootstrap, and generated
  classes

#### Scenario: JUnit 5 tests drive mutation analysis

- **WHEN** PIT executes
- **THEN** the `pitest-junit5-plugin` is on the `pitest` configuration so
  Jupiter tests are discovered and executed as the mutation test suite

#### Scenario: Mutation score below threshold fails

- **WHEN** the measured mutation score is below the configured minimum threshold
- **THEN** the `pitest` task fails and reports the surviving mutations

### Requirement: Quality Gates and Documentation

The testing conventions, source-set layout, and quality-gate commands SHALL be
documented in `services/AGENTS.md`. Coverage and mutation thresholds SHALL start
at realistic baseline values that can be ratcheted upward and SHALL be
configurable in one place.

#### Scenario: Test layout documented

- **WHEN** a contributor reads `services/AGENTS.md`
- **THEN** it describes the `test` vs `integrationTest` layout, per-layer test
  types, and the commands for coverage (`jacocoTestReport`,
  `jacocoTestCoverageVerification`) and mutation testing (`pitest`)

#### Scenario: Baseline thresholds are configurable

- **WHEN** a maintainer needs to raise a coverage or mutation threshold
- **THEN** the value is changed in the convention plugin (or catalog) once and
  applies to all services
