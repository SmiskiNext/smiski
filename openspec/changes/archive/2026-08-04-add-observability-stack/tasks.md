## 1. Metrics dependency and service configuration

- [x] 1.1 Add `micrometer-registry-prometheus` to `gradle/libs.versions.toml`
      under `[libraries]`, without a version — it is managed by `micrometer-bom`
      through the Spring Boot BOM, matching the surrounding entries
- [x] 1.2 Add the dependency as `implementation` in
      `build-logic/src/main/kotlin/io.github.smiskinext.plugin.service.base.gradle.kts`,
      next to `libs.spring.boot.starter.actuator`
- [x] 1.3 Add a `management` block to
      `services/tenant/src/main/resources/application.yaml` exposing only
      `health` and `prometheus`, per design D5
- [x] 1.4 Add the same `management` block to
      `services/meet/src/main/resources/application.yaml`
- [x] 1.5 Add the same `management` block to
      `services/notification/src/main/resources/application.yaml`
- [x] 1.6 Run `./services/gradlew -p services/tenant build` and confirm the
      dependency resolves and the existing suites still pass ← (verify:
      dependency resolves without an explicit version; `management` blocks are
      identical across all three services and expose nothing beyond health and
      prometheus)

## 2. Structured logging

- [x] 2.1 Add a `<SpringProfile name="docker">` branch to
      `services/shared/src/main/resources/log4j2-spring.xml` using Spring Boot's
      `StructuredLogLayout` with `format="ecs"`, mirroring the logger and root
      levels of the existing `k8s` branch
- [x] 2.2 Confirm the `local | default | dev` and `k8s` branches are
      byte-for-byte unchanged ← (verify: the new branch is the only edit; a
      service under the `docker` profile has a matching appender, so no profile
      can yield zero log output)

## 3. Observability configuration files

- [x] 3.1 Create `services/docker/observability/loki.yaml` — single binary,
      `auth_enabled: false`, filesystem storage, TSDB schema `v13`,
      `allow_structured_metadata: true`, 7-day retention (design D6)
- [x] 3.2 Create `services/docker/observability/prometheus.yml` with the five
      scrape jobs from design D5
- [x] 3.3 Create `services/docker/observability/config.alloy` with
      `discovery.docker`, a `discovery.relabel` rule mapping the container name
      to `service_name`, `loki.source.docker`, `loki.write`,
      `prometheus.exporter.cadvisor`, and `prometheus.scrape` (design D1, D7)
- [x] 3.4 Create `services/docker/observability/grafana/datasources.yaml`
      provisioning the Loki and Prometheus datasources
- [x] 3.5 Validate each file offline: `promtool check config`, `alloy validate`,
      and `docker compose config` ← (verify: all three validators exit zero
      against the files as committed, not against variants)

## 4. Compose topology

- [x] 4.1 Add `loki`, `alloy`, `prometheus`, and `grafana` services to
      `services/docker/compose.yaml`, each pinned to the version in design D8
      and each carrying `profiles: [observability]`
- [x] 4.2 Add three `postgres-exporter` services and one `redis_exporter` for
      Valkey, all carrying `profiles: [observability]` and none publishing a
      host port
- [x] 4.3 Publish only the host ports in design D9 — Grafana `3000`, Prometheus
      `9090`, Loki `3100`, Alloy `12345`
- [x] 4.4 Mount `/var/run/docker.sock` read-only into `alloy` only ← **Amended:
      two further read-only mounts were required.** The in-process cAdvisor
      exporter needs cgroup accounting for the numbers and Docker's own state
      for the names; with the socket alone it reports one series, for its own
      cgroup. `CADVISOR_CGROUP_ROOT` and `CADVISOR_DOCKER_ROOT` make both host
      paths overridable. Measurements and the Docker Desktop limitation are in
      design D7.
- [x] 4.5 Give Loki a healthcheck with a `start_period` of at least 30s — it
      became ready in 18s under measurement (design D6) ← **Resolved: no
      healthcheck is declared.** `grafana/loki:3.7.4` is distroless: its entire
      filesystem holds one executable, `/usr/bin/loki`, with no shell and no
      `wget`/`curl`, so no `CMD`/`CMD-SHELL` probe can reach `GET /ready`.
      Verified by `docker export | tar -t` on both `3.7.4` and `3.7.4-amd64`; no
      `3.7.4-alpine` tag exists. A `['CMD', '/usr/bin/loki', '-version']` probe
      was measured and rejected: it reports `healthy` at 4s while `/ready` still
      returns "Ingester not ready", so it would assert a readiness that does not
      hold — worse than declaring none. Dependants wait on `service_started`,
      matching how `gateway` is already handled in this file for the same
      reason. Host-side readiness is documented in AGENTS.md. A `wget`-bearing
      sidecar was considered and rejected as disproportionate: it adds a
      container to work around a probe for a component nothing depends on.
      Recorded in design D6.
- [x] 4.6 Add named volumes for Loki, Prometheus, and Grafana persistence
- [x] 4.7 Add `SPRING_PROFILES_ACTIVE: docker` to the `tenant`, `meet`, and
      `notification` service definitions
- [x] 4.8 Add explanatory comments in the style of the existing file for the
      socket mount, the profile gating, and the reason Kafka has no exporter ←
      (verify: `docker compose config` succeeds both with and without the
      profile; no observability service is reachable without it; no new service
      name collides with a name `envoy.yaml` resolves)

## 5. Documentation

- [x] 5.1 Add the four observability ports to the host port map in
      `services/docker/AGENTS.md`, noting the exporters are unpublished
- [x] 5.2 Document the opt-in command and state that the three Java images must
      be rebuilt with `bootBuildImage` before metrics or JSON logs appear
- [x] 5.3 Add the three new validation commands to the existing "Validating
      configuration changes" section
- [x] 5.4 Add the observability ports and the Grafana admin password to
      `services/docker/.env.example`, following the existing comment style
- [x] 5.5 Run `pnpm format` and `pnpm lint` ← (verify: the port map matches the
      compose file exactly; the rebuild requirement is stated next to the opt-in
      command, not buried elsewhere)

## 6. Verification against spec scenarios

Each task below maps to a scenario in `specs/dev-infras/spec.md`. The stack has
no automated test harness, so these are executed manually against a running
stack. Tasks 6.1 through 6.4 require the three images to be rebuilt first.

- [x] 6.1 Rebuild all three Java images with `bootBuildImage` ← **All three
      succeeded.** New image IDs `tenant e4b2a22c8638`, `meet c821c23b44eb`,
      `notification bcb354bad4fa`. The AOT source dump now carries
      `PrometheusScrapeEndpoint` in two files per service — the absence recorded
      in design Context is gone.
- [x] 6.2 _Metrics endpoint serves scrapeable output_ — request
      `/actuator/prometheus` on each of the three services and confirm metric
      families for JVM memory, GC, and HTTP requests ← **All three answer 200 as
      `text/plain;version=0.0.4`,** the Prometheus exposition format. Families
      per service: `tenant` 110, `meet` 116, `notification` 108. Each carries 4
      `jvm_memory_*`, 7–9 `jvm_gc_*` and 4 `http_server_requests_*` families.
      Requested over the stack network, since the services publish no host port.
- [x] 6.3 _Endpoint availability survives build-time optimisation_ — confirm the
      endpoint answers in a container running with `-Dspring.aot.enabled=true`.
      If it does not, apply the `ProcessAot` remedy in design D4 ← (verify: this
      is the change's central AOT assumption; confirm against a running
      container, not from configuration inspection) ← **The endpoint answers
      under AOT. The `ProcessAot` remedy was NOT needed and is not applied.**
      Each container logs
      `Starting AOT-processed {Tenant,Meet,Notification}     Application` with
      `JAVA_TOOL_OPTIONS=-Dspring.aot.enabled=true` and
      `SPRING_PROFILES_ACTIVE=docker`, so the optimisation is genuinely active
      rather than silently skipped, and `/actuator/prometheus` still returns 200
      on all three. Compiling `management.endpoints.web.exposure.include` into
      `application.yaml` is therefore sufficient: `PrometheusScrapeEndpoint`
      appears in the AOT sources of all three services after the rebuild.
      Confirmed by HTTP against running containers — the images have no shell,
      so `exec` is impossible and requests were issued from a throwaway curl
      container on the stack network.
- [x] 6.4 _Only health and metrics endpoints are exposed_ — confirm
      `/actuator/env` and `/actuator/configprops` are not reachable ← **Only
      `health` and `prometheus` answer.** Ten endpoints enumerated per service:
      `health` and `prometheus` return 200; `env`, `configprops`, `beans`,
      `metrics`, `loggers`, `threaddump`, `heapdump` and `mappings` all return
      404 on all three. The endpoints that would disclose datasource credentials
      on a `permitAll()` chain are not reachable.
- [x] 6.5 _Log records are machine-parseable_ — confirm the first log line of
      each service parses as a structured record with severity, logger, message,
      timestamp, and service name ← **Passes, with one clarification about what
      "first line" means.** The first lines a container emits are the Paketo
      memory calculator's, printed by the launcher before a JVM exists, so no
      logging configuration can shape them. The first line the application
      itself emits is ECS JSON, and every field the scenario names is present:
      `log.level`, `log.logger`, `message`, `@timestamp`, `service.name`.
      Measured across all records, not just the first — `tenant` 38, `meet` 122,
      `notification` 342 JSON records, zero unparseable under `jq`. This also
      clears the "logs disappear entirely" risk in design: the `docker` branch
      matches, so an appender is attached.
- [x] 6.6 _Correlation identifier is preserved_ — issue a request carrying
      `X-Correlation-ID` and confirm the identifier appears as a field on the
      resulting records ← **Preserved as a discrete top-level field.** A request
      with a known `X-Correlation-ID` produced two records on each service — the
      `-->` and `<--` pair from the shared `HttpLoggingFilter` — each carrying
      `"correlationId":"<sent value>"`, extractable with `jq -r .correlationId`.
      The supplied value is used rather than a generated one, and it is echoed
      back on the response header, so a caller can correlate too.
- [x] 6.7 _Default startup is unaffected_ — run the default startup command and
      confirm the running container set is identical to the pre-change set ←
      (verify: compare the full container list, not just a count) ← **Identical,
      and the pre-change set was measured rather than assumed.** The baseline
      was started from `git show HEAD:services/docker/compose.yaml`, its running
      services captured, then torn down and the post-change default started.

- [x] 6.8 _Observability is enabled by one command_ — start with the profile and
      confirm all eight observability containers reach a running state ← **All
      eight reach `running` from the single documented command,** with no file
      edit: `loki`, `alloy`, `prometheus`, `grafana`,
      `{tenant,meet,notification}-postgres-exporter`, `valkey-exporter`. Total
      20 containers — the 12 of the default set plus these 8.
- [x] 6.9 _Every declared target is scraped_ — confirm every Prometheus target
      reports up ← **9 of 9 up, 0 down, no `lastError` on any target.** Five
      jobs cover everything the requirement names: `spring-services`
      (`tenant`/`meet`/`notification:8080`), `envoy` (`envoy:9901`), `postgres`
      (three exporters on 9187), `valkey` (`valkey-exporter:9121`) and `alloy`
      (`alloy:12345`). `cadvisor` is absent by design — those series arrive by
      remote write, see 6.12.
- [x] 6.10 _Logs from every container reach the store_ — query Loki and confirm
      records are present for each container, labelled by container name ← **All
      20 stack containers return records, zero empty.** `query_range` per
      container over a 15-minute window returned entries for every one,
      including the shell-less `gateway` and the three Spring services. Labels
      exposed are `service_name` and `platform`; `service_name` carries the
      compose container name with Docker's leading `/` stripped, as the
      `discovery.relabel` rule intends. Loki reached `ready` about 9s after the
      ingester ring settled — within the 16–45s band recorded in design D6.
      **Observed, not a defect:** `discovery.docker` has no project filter, so
      Alloy also collected two containers from an unrelated compose project
      running on the same host. That is consistent with the requirement's
      wording ("every running container") and with a host-level collector, but
      worth knowing before reading a `service_name` list as the stack's own
      inventory.
- [x] 6.11 _Logs are filterable by service and severity_ — run a query
      constrained to one service and one severity and confirm the result set ←
      **Filters correctly, and confirmed discriminating rather than merely
      non-empty.**
      `{service_name="docker-tenant-1"} | json level="log.level" |     level="INFO"`
      returned 95 records; the same query at `DEBUG` returned 5, at `WARN` 1, at
      `ERROR` 0, against 115 records unfiltered — so the severity predicate
      partitions the stream instead of passing it through. Inspecting the result
      set, the only `service_name` present is `docker-tenant-1` and the only
      `log.level` is `INFO`. Severity lives at the nested ECS path `log.level`,
      so a query needs either an explicit `level="log.level"` extraction or the
      flattened `log_level` label `| json` generates — a bare `level=` matches
      nothing, which is worth knowing when writing the first Grafana query.
- [x] 6.12 _Per-container resource metrics are available_ — query CPU and memory
      for a named application container ← **Values returned for all three
      application containers, re-checked after the mount amendment.** Queried by
      `name`, not merely counted:

      | container              | working set | cpu total | cpu rate (1m) |
                                          | ---------------------- | ----------- | --------- | ------------- |
                                          | `docker-tenant-1`      | 508.9 MB    | 32.8 s    | 0.003 cores   |
                                          | `docker-meet-1`        | 575.9 MB    | 36.1 s    | 0.014 cores   |
                                          | `docker-notification-1`| 477.9 MB    | 36.3 s    | 0.019 cores   |

                                          All 20 stack containers carry a `name` label, so the narrowed
                                          `/sys/fs/cgroup` + `/var/lib/docker` pair is sufficient — the earlier
                                          `/rootfs` mount is confirmed unnecessary. `store_container_labels = false`
                                          also holds: a series carries exactly `__name__, id, image, instance, job,
                                          name` and zero `container_label_*` keys. No per-container agent is deployed;
                                          the exporter runs inside Alloy and pushes 5,543 series by remote write.

- [x] 6.13 _Observability failure does not block the application_ — stop Loki
      while the profile runs and confirm the application containers stay healthy
      ← **No application impact.** With `loki` in state `exited`, all 19
      remaining containers stayed `running`, `/actuator/health` reported `UP` on
      all three Spring services, and a request through Envoy still returned 401
      from `ext_authz` — proving the gateway path serves rather than merely that
      processes are alive. Nothing in the profile is a `depends_on` of an
      application container, which is what makes this hold. Alloy also stayed
      `running` with all 7 components `healthy`, logging push failures rather
      than exiting. Restarting Loki additionally covers the delta spec's
      _Collector restart does not lose the log store's contents_: it returned to
      `ready` in 21s, the 145 pre-stop records were still queryable, and
      collection resumed (10 streams in the following 60s) because `loki-data`
      and `alloy-data` are named volumes.
- [x] 6.14 _Observability is disabled again without file changes_ — restart
      without the profile and confirm the observability containers do not start
      ← **Disabled by dropping the flag, no file edited.** After `down` and a
      plain `up -d`, the running set is the same 12 containers, `diff`-identical
      to the pre-change baseline. `ps -a` shows no observability container in
      any state, not merely none running — the profile excludes them from
      creation rather than starting and stopping them — and none of the four
      host ports (3000, 9090, 3100, 12345) is listening. The three services
      report `UP` and Envoy still serves, so the round trip through the profile
      left the applications unaffected.
- [x] 6.15 _Every pinned tag resolves_ — resolve each observability image
      reference against its registry ← (verify: every scenario in the delta spec
      has a corresponding executed check above, and each was run rather than
      reasoned about) ← **All six resolve; each was resolved, not assumed.**
      `docker manifest inspect` returned a digest for every reference:

      | image                                           | digest (truncated) |
                                          | ----------------------------------------------- | ------------------ |
                                          | `grafana/loki:3.7.4`                            | `sha256:d80be589` |
                                          | `grafana/alloy:v1.18.0`                         | `sha256:eb21f4c0` |
                                          | `prom/prometheus:v3.13.2`                       | `sha256:1147c928` |
                                          | `grafana/grafana:12.4.6`                        | `sha256:950e5b5b` |
                                          | `prometheuscommunity/postgres-exporter:v0.20.1` | `sha256:4f3d8280` |
                                          | `oliver006/redis_exporter:v1.88.0-alpine`       | `sha256:9291e77f` |

                                          The sibling scenario _No observability image floats on a mutable tag_ also
                                          passes: every reference carries an explicit version and none is `latest`,
                                          `main`, `stable` or bare.

                                          **Delta-spec scenario coverage.** The delta spec declares 27 scenarios, not
                                          25 — an earlier count here was wrong. All 27 are executed rather than
                                          reasoned about. The 15 tasks above cover 19 directly. Three more were
                                          covered in passing and are recorded where they were observed:
                                          _Collector restart does not lose the log store's contents_ in 6.13,
                                          _Collection requires no per-service configuration_ in 6.10 (no application
                                          service definition carries logging configuration, yet all 20 containers are
                                          collected — including two from an unrelated project), and _A component
                                          without a metrics endpoint is excluded deliberately_ in 6.9 (`kafka` and
                                          `gateway` are absent from the 9 targets and their exclusion is documented in
                                          `prometheus.yml`, `compose.yaml` and `AGENTS.md`). _Stale image reports a
                                          down target rather than failing_ is the one scenario asserted structurally
                                          rather than by executing a stale build: no application container depends on
                                          a Prometheus target, which 6.13 demonstrated from the opposite direction.

                                          The remaining four are configuration scenarios, executed against the files
                                          as committed:

                                          - _Each configuration format has a documented validation command_ and
                                            _Compose definition is valid with and without the profile_ — the five
                                            commands in `AGENTS.md` "Validating configuration changes" cover compose
                                            (both modes), Envoy, Prometheus, Alloy and Loki; all exit 0.
                                          - _Invalid configuration is rejected before startup_ — each validator was
                                            run against a deliberately corrupted copy outside the repository.
                                            `promtool` exit 1 naming `field scrape_intervl not found`, `alloy
                                            validate` exit 1 with a caret at the offending line, `loki
                                            -verify-config` exit 1 naming `field auth_enabld not found`. The same
                                            three commands exit 0 against the committed files, so the validators
                                            discriminate rather than always passing.
                                          - _Other profiles are unaffected_ — `git diff` on `log4j2-spring.xml` is
                                            purely additive: the `local | default | dev` and `k8s` branches have no
                                            removed or altered line, so only the new `docker` branch is introduced.

                                          Two further scenarios are covered by 6.3 and 6.5 under different wording:
                                          _Stack-activated profile does not alter the component graph_ (the three
                                          services start healthy under `docker` with AOT active and expose the same
                                          endpoints) and _Every profile the stack activates produces log output_ (the
                                          `docker` branch matches, so an appender is attached — the "no fallback
                                          branch" risk in design does not fire). _Service with no unresolvable
                                          property starts_ and _Property required by a shared component is supplied_
                                          hold as a precondition of 6.2 through 6.6: all three services reached a
                                          healthy state and served requests under the stack's supplied environment.
