# Context

The monorepo runs four deployable Spring Boot services (user-management,
meeting-management, chat-management, notification) plus an Android app and a
Next.js web frontend, all backed by a Kustomize-based Kubernetes delivery model.
Analysis surfaced five consistency gaps that individually are manageable but
collectively make a safe production rollout impossible:

1. `chat-management` hardcodes connection strings and a DEBUG log level directly
   in `application.yaml`, diverging from the `${ENV_VAR:default}` convention
   followed by the other three services.
2. All four service base manifests embed Kubernetes `Secret` objects with
   `change-me-in-production` placeholders; Kustomize inheritance means those
   plaintext values flow into the prod overlay unchanged.
3. The prod overlay names only a MetalLB IP pool. There are no resource
   requests/limits, no replica patches, and no image pull policy overrides —
   production would start identical to dev.
4. The web frontend silently swallows missing environment variables (empty
   string fallback for API base URL, localhost fallback for LiveKit URL),
   masking misconfiguration at startup.
5. No CI pipeline builds or pushes container images; K8s manifests reference
   static `0.0.1-SNAPSHOT` tags and image lifecycle is manual.

## Goals / Non-Goals

**Goals:**

- Bring `chat-management` env-var handling to parity with the other three
  services using the same `${VAR:default}` pattern already in use.
- Make gRPC negotiation type in `meeting-management` env-overridable for
  TLS-capable prod deployments.
- Eliminate plaintext credentials from Kustomize base manifests; scope secrets
  to overlay directories.
- Give the prod overlay meaningful resource, replica, and image-pull-policy
  patches for all four services.
- Make both web app startup paths fail fast and loudly on missing required env
  vars.
- Automate container image builds and pushes from CI on merge to main.

**Non-Goals:**

- Introducing a secrets management system (Vault, External Secrets Operator) —
  overlay-scoped static secrets is the right incremental step.
- Changing any service's runtime behavior, API contracts, or data models.
- Adding new Kubernetes workloads or changing the overall cluster topology.
- Modifying Android app network or auth logic beyond adding documentation
  comments.
- Setting up image signing or SBOM generation in CI.

## Decisions

### D1: Retain `${ENV_VAR:default}` inline placeholders for backend config rather than centralizing to a ConfigMap

Spring Boot's `${VAR:default}` syntax is already the established pattern in
three of four services. Adopting ConfigMaps for all config would require
refactoring all services and does not add value at this stage. The pattern is
kept consistent by patching `chat-management` to match.

**Alternatives considered:** Spring Cloud Config Server — rejected, adds
operational complexity and a new service dependency without benefit for a
four-service system.

### D2: Overlay-scoped secret files rather than Kustomize `secretGenerator`

Kustomize's `secretGenerator` hashes secret names, requiring all consumer
Deployments to reference the generated name (or use
`disableNameSuffixHash: true`). Given the existing manifests reference Secret
names directly, moving secrets to static YAML files per overlay avoids renaming
churn while still scoping credentials to their overlay. The dev overlay retains
working dev defaults; the prod overlay ships clearly-annotated placeholder
values.

**Alternatives considered:** `secretGenerator` with
`disableNameSuffixHash: true` — functionally equivalent but adds a less common
Kustomize feature with no return. External Secrets Operator — correct long-term
direction but out of scope here.

### D3: Dedicated patch files per concern in prod overlay (resources, replicas, images) rather than one merged patch

Separating `prod-resources.yaml`, `prod-replicas.yaml`, and `prod-images.yaml`
keeps each patch single-purpose and independently reviewable. Kustomize applies
patches additively, so there is no technical obstacle. This also lets future
changes update one concern without touching others.

**Alternatives considered:** Single large strategic-merge patch — harder to
review and conflicts are more likely across patches to the same object.

### D4: Fail-fast validation in web components at module initialization rather than at render time

Checking `process.env.NEXT_PUBLIC_*` at the point the provider/component module
is evaluated (not inside a render cycle) surfaces configuration errors in
server-side startup logs immediately, before any user sees the app. This is
consistent with fail-fast principles and is safe because Next.js evaluates these
expressions at build time for static exports (the values must be present) and at
startup for server rendering.

**Alternatives considered:** Runtime error boundary — defers the error to
user-visible render, which is strictly worse.

### D5: Per-service path-filtered GitHub Actions jobs for image builds rather than a single matrix job

A matrix job runs all service builds when any service changes. Path filtering
per service means only changed services rebuild, reducing CI wall time and
unnecessary image pushes. Each service job uses `paths` to check
`services/<service-name>/**` and `build-logic/**`.

**Alternatives considered:** Single shared job with `docker/build-push-action` —
does not use Spring Boot's `bootBuildImage` (which is configured in the
build-logic convention plugins) and would require maintaining separate
Dockerfiles.

## Risks / Trade-offs

- **Prod secrets are still static YAML files in git** — they carry clear
  `MUST_CHANGE_IN_PRODUCTION` annotations and are scoped to the prod overlay,
  but operators must replace values before applying. Mitigation: tasks include a
  verification step that runs `kustomize build overlays/prod` and checks that no
  placeholder values remain in the rendered manifests.
- **Replica count of 2 for stateless services assumes the cluster has sufficient
  capacity** — the patch sets `replicas: 2` unconditionally. Mitigation:
  document in the patch file that this is a starting point and should be
  adjusted to cluster capacity.
- **MetalLB IP pool in prod overlay uses a local subnet range** — there is
  already a TODO comment requirement in the plan. Mitigation: the task adds a
  prominent comment in `metallb-pool.yaml` flagging the IP range as a required
  override.
- **CI image push requires a `GHCR_TOKEN` secret** — the workflow will fail
  silently if the secret is absent. Mitigation: the workflow explicitly fails
  with a meaningful error message if the secret is not set, and the task
  includes a setup note.
- **chat-management DEBUG → INFO log level change is a runtime behavior change**
  — in production this is correct and expected; in dev it may reduce visibility.
  Mitigation: the `${SPRING_LOG_LEVEL_ROOT:INFO}` default keeps production safe
  while allowing `SPRING_LOG_LEVEL_ROOT=DEBUG` override in dev environments
  where needed.

## Migration Plan

1. Apply Phase 1 (backend config) — no deployment restart required immediately;
   changes take effect on next pod restart or rollout.
2. Apply Phase 2 (K8s secrets relocation) — before applying, confirm overlay
   secrets files exist with correct values. Roll out by running
   `kustomize build overlays/dev | kubectl apply -f -` and verifying pods
   restart cleanly.
3. Apply Phase 3 (prod overlay patches) — review resource values against cluster
   node capacity before applying. Run `kustomize build overlays/prod` dry-run
   first.
4. Apply Phase 4 (frontend env safety) — requires all required env vars to be
   present in deployment config before the next web build; confirm
   `.env.local.example` is committed and communicated to the team.
5. Apply Phase 5 (CI image workflow) — add `GHCR_TOKEN` and related secrets to
   the GitHub repository before merging; first push to main after merge triggers
   the initial build.

**Rollback**: Each phase is independently revertable via git revert on the
relevant files. K8s changes require re-applying the previous `kustomize build`
output.

## Open Questions

- What are the exact CPU/memory resource targets for the prod overlay? The
  design uses reasonable defaults (2 CPU / 2 Gi for services, 1 CPU / 1 Gi for
  databases) but these should be validated against actual profiling data before
  production rollout.
- Should the prod MetalLB pool be removed from version control entirely in favor
  of a cluster-specific apply step? Currently it stays in the overlay with a
  clear TODO comment, but teams with strict GitOps practices may prefer it
  absent.
