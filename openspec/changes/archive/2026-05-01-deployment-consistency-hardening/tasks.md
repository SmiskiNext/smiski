# Tasks

## 1. Phase 1 — Standardize Backend Environment Variable Patterns

- [x] 1.1 In `services/chat-management/src/main/resources/application.yaml`,
      replace the hardcoded MongoDB URI with
      `${SPRING_DATA_MONGODB_URI:mongodb://localhost:27017/chat_management}`
- [x] 1.2 In `services/chat-management/src/main/resources/application.yaml`,
      replace the hardcoded Kafka bootstrap servers value with
      `${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}`
- [x] 1.3 In `services/chat-management/src/main/resources/application.yaml`,
      replace the hardcoded `DEBUG` root log level with
      `${SPRING_LOG_LEVEL_ROOT:INFO}` ← (verify: confirm the yaml key path for
      log level matches Spring Boot's externalization convention and test that
      `SPRING_LOG_LEVEL_ROOT=DEBUG` overrides the level correctly)
- [x] 1.4 In
      `services/meeting-management/src/main/resources/application.properties`,
      change the `spring.grpc.client.channels.user-management.negotiation-type`
      value to `${GRPC_NEGOTIATION_TYPE:plaintext}`
- [x] 1.5 Run `./services/gradlew build` and `./services/gradlew test` to
      confirm no regressions from config changes ← (verify: build and test
      suites pass green; confirm chat-management integration tests connect using
      the default placeholder URI via Testcontainers and are not broken by the
      env-var switch)

## 2. Phase 2 — Separate Secrets from K8s Base Manifests

- [x] 2.1 Remove the `Secret` object from
      `services/k8s/base/services/user-management.yaml` (and any co-located
      Secret YAML for this service)
- [x] 2.2 Remove the `Secret` object from
      `services/k8s/base/services/meeting-management.yaml`
- [x] 2.3 Remove the `Secret` object from
      `services/k8s/base/services/chat-management.yaml`
- [x] 2.4 Remove the `Secret` object from
      `services/k8s/base/services/notification.yaml`
- [x] 2.5 Relocate secret content from
      `services/k8s/base/livekit/02-secrets.yaml` — remove Secret documents from
      the base file
- [x] 2.6 Relocate secret content from
      `services/k8s/base/rustfs/01-secrets.yaml` — remove Secret documents from
      the base file
- [x] 2.7 Update `services/k8s/base/kustomization.yaml` to remove any references
      to the now-empty or removed secret files
- [x] 2.8 Create `services/k8s/overlays/dev/secrets/` directory and write one
      Secret YAML file per service (user-management, meeting-management,
      chat-management, notification) with the same dev-appropriate values
      previously in base
- [x] 2.9 Create dev overlay secret files for LiveKit
      (`services/k8s/overlays/dev/secrets/livekit-secrets.yaml`) and RustFS
      (`services/k8s/overlays/dev/secrets/rustfs-secrets.yaml`) with the same
      values previously in base
- [x] 2.10 Update `services/k8s/overlays/dev/kustomization.yaml` to include all
      files under `secrets/` in its resources list
- [x] 2.11 Create `services/k8s/overlays/prod/secrets/` directory and write one
      Secret YAML file per service (user-management, meeting-management,
      chat-management, notification) where every sensitive field value is
      `MUST_CHANGE_IN_PRODUCTION`
- [x] 2.12 Create prod overlay secret files for LiveKit and RustFS under
      `services/k8s/overlays/prod/secrets/` with `MUST_CHANGE_IN_PRODUCTION`
      placeholder values
- [x] 2.13 Update `services/k8s/overlays/prod/kustomization.yaml` to include all
      files under `secrets/` in its resources list
- [x] 2.14 Run `kustomize build services/k8s/overlays/dev` and confirm the
      output contains Secret objects and zero errors ← (verify: rendered output
      includes Secrets for all four services plus LiveKit and RustFS; no
      plaintext credentials remain in any base manifest;
      `kustomize build services/k8s/base` renders zero Secret objects)

## 3. Phase 3 — Build Out Prod K8s Overlay

- [x] 3.1 Create `services/k8s/overlays/prod/patches/prod-resources.yaml` as a
      strategic-merge patch setting CPU and memory requests and limits for all
      four service Deployments (e.g., requests: `500m`/`512Mi`, limits:
      `2000m`/`2Gi`) and database StatefulSets if present in the overlay
- [x] 3.2 Create `services/k8s/overlays/prod/patches/prod-replicas.yaml` as a
      strategic-merge patch setting `spec.replicas: 2` for all four stateless
      service Deployments (user-management, meeting-management, chat-management,
      notification)
- [x] 3.3 Create `services/k8s/overlays/prod/patches/prod-images.yaml` as a
      strategic-merge patch setting `imagePullPolicy: Always` on all containers
      in all four service Deployments
- [x] 3.4 Add a prominent comment to
      `services/k8s/overlays/prod/metallb-pool.yaml` stating that the IP address
      range is a local subnet placeholder that MUST be replaced with the actual
      production network range before applying
- [x] 3.5 Update `services/k8s/overlays/prod/kustomization.yaml` to include
      `patches:` entries for `prod-resources.yaml`, `prod-replicas.yaml`, and
      `prod-images.yaml`
- [x] 3.6 Run `kustomize build services/k8s/overlays/prod` and confirm zero
      errors ← (verify: rendered Deployments for all four services have
      `replicas: 2`, non-empty resource requests and limits, and
      `imagePullPolicy: Always`; metallb-pool carries the override comment; no
      Secret object contains a non-placeholder value)

## 4. Phase 4 — Frontend Environment Safety

- [x] 4.1 Create `frontends/web/.env.local.example` with documented entries for
      `NEXT_PUBLIC_API_BASE_URL`, `NEXT_PUBLIC_LIVEKIT_URL`, and all other
      `NEXT_PUBLIC_*` variables required by the web app; include inline comments
      describing purpose and expected format for each variable
- [x] 4.2 In `frontends/web/src/components/api-client-provider.tsx`, add a guard
      at module initialization that throws a descriptive error when
      `process.env.NEXT_PUBLIC_API_BASE_URL` is absent or resolves to an empty
      string
- [x] 4.3 In `frontends/web/src/components/meeting/index.tsx`, add a guard at
      initialization that throws a descriptive error when
      `process.env.NEXT_PUBLIC_LIVEKIT_URL` is absent, removing the silent
      localhost fallback
- [x] 4.4 In `frontends/android-app/app/build.gradle.kts`, add a TODO comment
      adjacent to each release `buildConfigField` or `resValue` that uses a
      placeholder value (e.g., `example.com`), stating what the real value must
      be ← (verify: web build `pnpm --dir frontends/web build` passes when valid
      env vars are present; both guards throw on missing vars;
      .env.local.example covers all NEXT*PUBLIC*\* vars referenced in the
      codebase)

## 5. Phase 5 — CI/CD Container Image Build Automation

- [x] 5.1 Create `.github/workflows/build-images.yml` with a workflow trigger on
      `push` to `main` using `paths` filters; define four jobs (one per
      deployable service: user-management, meeting-management, chat-management,
      notification) each filtered to `services/<service-name>/**` and
      `build-logic/**`
- [x] 5.2 In each job, add steps to: check out the repository, set up Java
      (matching the project's Java 25 toolchain), log in to `ghcr.io` using a
      repository secret, run
      `./services/gradlew -p services/ <service-name> bootBuildImage`, and push
      the built image tagged with the git SHA to
      `ghcr.io/phunguy65/zms/<service-name>`
- [x] 5.3 Add a workflow-level check that fails with a descriptive message if
      the required GitHub secret (e.g., `GHCR_TOKEN`) is not set, rather than
      producing an obscure authentication error
- [x] 5.4 Confirm that `proto` and `shared` are not referenced as build targets
      in any job in the workflow ← (verify: workflow YAML is valid; path filters
      are correct for each service; image naming matches
      `ghcr.io/phunguy65/zms/<service-name>`; no job targets proto or shared; a
      dry-run lint of the YAML with `actionlint` or equivalent passes if
      available)
