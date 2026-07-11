# Implementation Tasks

## 1. Tooling versions

- [x] 1.1 Add versions to `gradle/libs.versions.toml`: `jacoco = "0.8.15"`,
      `pitest = "1.25.7"`, `pitestJunit5 = "1.2.3"`, and plugin
      `pitestGradle = "1.19.0"`
- [x] 1.2 Add `[libraries]` entries:
      `pitest-junit5 = { module = "org.pitest:pitest-junit5-plugin", version.ref = "pitestJunit5" }`
      (and pitest core if referenced by the plugin config) — NOTE: versions
      consumed directly via `pitestVersion`/`junit5PluginVersion` catalog refs
      in the plugin extension; no separate `[libraries]` entry needed
- [x] 1.3 Add `[plugins]` entry:
      `pitest = { id = "info.solidsoft.pitest", version.ref = "pitestGradle" }`
- [x] 1.4 Add the PIT Gradle plugin dependency to `build-logic/build.gradle.kts`
      `dependencies` via the `plugin(...)` helper so the precompiled script
      plugin can apply it ← (verified: `./services/gradlew -p build-logic build`
      succeeds under JDK 21)

## 2. Test convention plugin

- [x] 2.1 Create
      `build-logic/src/main/kotlin/io.github.smiskinext.plugin.test.base.gradle.kts`
      applying `jacoco` and `info.solidsoft.pitest`, reading `LibrariesForLibs`
- [x] 2.2 Register the `integrationTest` source set with compile/runtime
      classpaths extending `main` + `test` outputs, and matching
      `integrationTestImplementation`/`integrationTestRuntimeOnly`
      configurations extending the `test` counterparts
- [x] 2.3 Register the `integrationTest` `Test` task (`useJUnitPlatform()`,
      `shouldRunAfter("test")`) and wire
      `tasks.check { dependsOn("integrationTest") }`
- [x] 2.4 Configure `jacoco { toolVersion = libs.versions.jacoco.get() }`; make
      `jacocoTestReport` produce HTML+XML from the `test` task and run after
      `test`
- [x] 2.5 Configure `jacocoTestCoverageVerification` with line ≥ 0.70 / branch ≥
      0.60 rules, excluding `**/*Application*`, `**/*Config*`,
      `**/*Configuration*`, `**/infrastructure/persistence/**JpaEntity*`, and
      generated classes
- [x] 2.6 Configure the `pitest` extension: `pitestVersion` = catalog,
      `junit5PluginVersion` = catalog,
      `targetClasses = ["io.github.smiskinext.*.domain.*", "io.github.smiskinext.*.application.*"]`,
      exclude config/entity/application/generated, `mutationThreshold = 60`,
      bind to the `test` task ← (verified: `pitest` ran on meet, mutating
      domain/application, test strength 100% on covered mutants)

## 3. Apply to services

- [x] 3.1 Apply `io.github.smiskinext.plugin.test.base` in
      `services/meet/build.gradle.kts`
- [x] 3.2 Apply the plugin in `services/tenant`, `services/record`, and
      `services/notification` build files
- [x] 3.3 Move `{Service}ApplicationTests`, `{Service}OpenApiGenerationTest`,
      and `config/TestcontainersConfiguration` from `src/test` to
      `src/integrationTest` for all four services; keep
      `architecture/ArchitectureTest` in `src/test` (via `git mv`)
- [x] 3.4 Move `src/test/resources/application-test.yaml` (and any container
      resources) to `src/integrationTest/resources` where required by the moved
      tests
- [x] 3.5 Repoint the `generateOpenApiDocsFromTests` task in each service to the
      `integrationTest` source set output and classpath ← (verified:
      `generateOpenApiDocsFromTests` regenerates meet `openapi.yaml`, no diff)

## 4. Reference tests (prove the harness)

- [x] 4.1 Add a pure domain unit test in
      `services/meet/src/test/java/.../meet/domain/` (aggregate or value object,
      no Spring/containers) — added `MeetingTimeRangeTest` + `MeetingTitleTest`
- [~] 4.2 Add an application use-case test for an `*ApplicationService` with
  Mockito-mocked ports ← DEVIATION: the `application` layer is empty across ALL
  services (only `.gitkeep`), so no `*ApplicationService` exists to test yet.
  Domain coverage maximized instead; the mocked-port use-case pattern is
  documented in `design.md`/`AGENTS.md` for when services land. `test` runs with
  no container started; `pitest` reports a mutation score for meet's domain.

## 5. Verify and document

- [x] 5.1 Run per service: `test`, `integrationTest`, `jacocoTestReport`,
      `pitest` — `build` (test + integrationTest) green on all four services;
      `jacocoTestReport` + `pitest` green on meet. Coverage/mutation _gates_
      fail by design (no test backfill yet) and are opt-in, not wired into
      `check`.
- [x] 5.2 Run `./services/gradlew spotlessApply` and confirm formatting passes
      on new/moved files
- [x] 5.3 Update `services/AGENTS.md`: document `test` vs `integrationTest`
      layout, per-layer test types, and the coverage/mutation commands and
      baseline gates
