## Why

The local stack under `services/docker/` runs twelve containers and exposes no
way to observe any of them. Reading logs means `docker compose logs -f <name>`
one service at a time, with no cross-service correlation even though
`HttpLoggingFilter` already stamps every request with an `X-Correlation-ID`.
Runtime health — heap pressure, connection pool saturation, request latency,
Kafka consumer progress — is not observable at all.

`spring-boot-starter-actuator` is already on the classpath of all three Java
services (`build-logic/.../service.base.gradle.kts`), but no `management`
configuration exists and no metrics registry is present, so the investment is
inert.

`services/k8s/plg/` holds a Kubernetes PLG stack, but it does not apply here: it
is Helm-only, ships no Prometheus despite `deploy.en.md` advertising one, and is
built on Promtail, which reached end of life on 2 March 2026.

## What Changes

### Observability stack in the local compose topology

- Add Loki, Grafana Alloy, Prometheus, and Grafana to
  `services/docker/compose.yaml`, plus three `postgres-exporter` instances and
  one `redis_exporter` for Valkey.
- Every added container declares `profiles: [observability]`, so
  `docker compose up -d` starts the exact twelve containers it starts today.
  Observability is opted into with
  `docker compose --profile observability up -d`.
- Alloy discovers containers over the Docker socket (mounted read-only) and
  ships their stdout to Loki. It also runs its built-in cAdvisor exporter, so
  per-container CPU and memory need no separate container.
- Grafana is provisioned with Loki and Prometheus datasources at startup.
- Add `services/docker/observability/` holding `loki.yaml`, `prometheus.yml`,
  `config.alloy`, and the Grafana datasource provisioning file.

### Prometheus scrape endpoint on the Java services

- Add `micrometer-registry-prometheus` to `gradle/libs.versions.toml` and to the
  shared `service.base` convention plugin, so all three services gain it
  together. The version is managed by `micrometer-bom` through the Spring Boot
  BOM, matching how every other entry in that file omits a version.
- Add a `management` block to each service's `application.yaml` exposing only
  `health` and `prometheus`.
- **The image must be rebuilt for this to take effect.** Compose runs the
  services with `-Dspring.aot.enabled=true`, and AOT evaluates
  `@ConditionalOnAvailableEndpoint` at build time. Inspecting
  `services/tenant/build/generated/aotSources/` confirms the current build emits
  bean definitions for the health endpoint and none for a Prometheus endpoint.
  No runtime environment variable can add it.

### Structured logs from the Java services

- Add a `docker` profile branch to `services/shared/.../log4j2-spring.xml` using
  Spring Boot's own `StructuredLogLayout` with `format="ecs"`, and set
  `SPRING_PROFILES_ACTIVE=docker` for the three services in compose.
- `StructuredLogLayout` ships inside `spring-boot-4.1.0.jar` and is registered
  in its `Log4j2Plugins.dat`. Unlike the `JsonTemplateLayout` used by the
  existing `k8s` branch, it resolves `service.name` from
  `spring.application.name` and honours the `logging.structured.*` properties.
- The `local | default | dev` and `k8s` branches are untouched.

### Documentation

- Extend `services/docker/AGENTS.md` with the observability port map, the opt-in
  command, and offline validation commands for the three new config formats,
  alongside the existing compose and Envoy validation commands.
- Extend `services/docker/.env.example` with the new ports and the Grafana admin
  password.

### Explicitly excluded

- **Kafka metrics.** `apache/kafka:4.1.0` bundles no JMX exporter; adding one
  means a sidecar plus `KAFKA_OPTS` wiring, which outweighs its value locally.
- **`gateway` (Go) metrics.** The service contains no instrumentation today;
  adding it is a code change to a different language and belongs in its own
  change.
- **`services/k8s/plg/`.** Left as-is. Its Promtail dependency is EOL and its
  README queries a deleted `user-management` service — recorded below as debt.
- **Tracing.** No Tempo, no OpenTelemetry collector.
- **Alerting rules and custom dashboards.** Datasources only.

## Capabilities

### New Capabilities

None. This change extends the existing local stack rather than introducing a new
capability.

### Modified Capabilities

- `dev-infras`: The spec describes a stack with no observability. It gains
  requirements for an opt-in observability profile that leaves default startup
  unchanged, a metrics endpoint contract on the Java services, a structured log
  format for container log collection, log collection over the Docker socket,
  and offline validation of the new configuration formats.

## Impact

**New files**

- `services/docker/observability/loki.yaml`
- `services/docker/observability/prometheus.yml`
- `services/docker/observability/config.alloy`
- `services/docker/observability/grafana/datasources.yaml`

**Modified**

- `services/docker/compose.yaml` — observability services, volumes,
  `SPRING_PROFILES_ACTIVE` on the three Java services
- `services/docker/.env.example` — observability ports, Grafana password
- `services/docker/AGENTS.md` — port map, opt-in command, validation commands
- `gradle/libs.versions.toml` — `micrometer-registry-prometheus`
- `build-logic/src/main/kotlin/io.github.smiskinext.plugin.service.base.gradle.kts`
  — the new dependency
- `services/shared/src/main/resources/log4j2-spring.xml` — `docker` profile
  branch
- `services/{tenant,meet,notification}/src/main/resources/application.yaml` —
  `management` block

**Unchanged**: all Java and Go source, `services/docker/envoy/`, the Forge app
under `app/`, `services/k8s/`, and the `local | default | dev` and `k8s` logging
branches.

**Operator impact**: the three Java images must be rebuilt with `bootBuildImage`
before metrics or JSON logs appear. An operator running the old images against
the new compose file gets today's behaviour, not an error.

**Component versions**, each verified to resolve and run before being written
here:

| Component         | Version          |
| ----------------- | ---------------- |
| Loki              | `3.7.4`          |
| Grafana Alloy     | `v1.18.0`        |
| Prometheus        | `v3.13.2`        |
| Grafana           | `12.4.6`         |
| postgres-exporter | `v0.20.1`        |
| redis_exporter    | `v1.88.0-alpine` |

**Risks**

- _Docker socket exposure._ Alloy needs it to discover containers. Mounted
  read-only, and the stack is already documented as local-only.
- _AOT and the new Spring profile._ AOT freezes `@Conditional` evaluation at
  build time. The `docker` profile changes no bean: the repository contains no
  `@Profile` annotation, and the `@ConditionalOnProperty` usages (`CacheConfig`,
  `RedisConfig`, `OutboxAutoConfiguration`) read properties fed by environment
  variables that are identical across profiles. Spring documents profiles that
  only change properties as supported without limitation under AOT. If a context
  failure appears regardless, the documented remedy is to pass
  `--spring.profiles.active=docker` to the Gradle `ProcessAot` task.
- _Silent log loss._ `log4j2-spring.xml` routes appenders by profile with no
  fallback, so a `docker` profile without a matching branch would produce no log
  output at all. The branch and the profile must land together.
- _Resource cost._ Roughly 800 MB additional RAM when the profile is enabled,
  which is why it is off by default.

**Accepted technical debt**

`services/k8s/plg/` still deploys Promtail (EOL 2 March 2026) and its README
queries `{app="user-management"}`, a service that exists only in the gitignored
`zms/` snapshot. Out of scope here; migrating it to Alloy is follow-up work.
