# Why

Deployment consistency gaps between dev and production environments create
operational risk: hardcoded connection strings prevent secret rotation, inline
K8s Secrets expose plaintext credentials in version control, and absent
prod-overlay tuning means production would run with development defaults. These
issues compound as the system scales and must be addressed before any production
deployment.

## What Changes

- `services/chat-management/src/main/resources/application.yaml`: Replace
  hardcoded MongoDB URI, Kafka bootstrap servers, and DEBUG log level with
  `${ENV_VAR:default}` placeholders matching the pattern used by all other
  services.
- `services/meeting-management/src/main/resources/application.properties`: Make
  gRPC negotiation-type env-overridable so TLS can be enabled in prod without
  code changes.
- `services/k8s/base/services/*.yaml` (user-management, meeting-management,
  chat-management, notification): Extract inline Secret objects out of base
  manifests.
- `services/k8s/base/livekit/02-secrets.yaml` and
  `services/k8s/base/rustfs/01-secrets.yaml`: Relocate secrets into
  overlay-specific files.
- `services/k8s/overlays/dev/secrets/`: Introduce dev-appropriate secret files
  (same values, properly scoped).
- `services/k8s/overlays/prod/secrets/`: Introduce prod secret files with
  clearly-marked override-required placeholders.
- Both overlay `kustomization.yaml` files updated to include overlay-scoped
  secret files.
- `services/k8s/overlays/prod/patches/`: Add `prod-resources.yaml`,
  `prod-replicas.yaml`, and `prod-images.yaml` patches for resource tuning, HA
  replicas, and image pull policy.
- `services/k8s/overlays/prod/kustomization.yaml`: Wire in all prod patches.
- `frontends/web/.env.local.example`: Create documented example env file so
  developers know required variables.
- `frontends/web/src/components/api-client-provider.tsx`: Fail-fast on missing
  `NEXT_PUBLIC_API_BASE_URL` instead of silently using empty string.
- `frontends/web/src/components/meeting/index.tsx`: Fail-fast on missing
  `NEXT_PUBLIC_LIVEKIT_URL` instead of silently falling back to localhost.
- `frontends/android-app/app/build.gradle.kts`: Add clear TODO comments on
  release placeholder values.
- `.github/workflows/build-images.yml`: New CI workflow that builds and pushes
  container images on push to main when service sources change, using path
  filters per service.

## Capabilities

### New Capabilities

- `env-var-standardization`: Backend service configuration uses uniform
  `${ENV_VAR:default}` placeholders across all services, enabling
  per-environment overrides without code changes.
- `k8s-secret-isolation`: Kubernetes Secret objects are isolated to overlay
  directories, keeping base manifests credential-free and preventing plaintext
  secrets from being inherited by all environments.
- `prod-overlay-hardening`: Production K8s overlay gains resource
  requests/limits, replica counts for HA, and correct image pull policy for all
  stateless services.
- `frontend-env-safety`: Web app fails fast on missing required environment
  variables and ships a documented `.env.local.example`; Android release build
  config has explicit placeholder annotations.
- `container-image-ci`: GitHub Actions workflow builds and pushes versioned
  container images to `ghcr.io/phunguy65/zms/` on changes to service sources,
  eliminating manual image lifecycle management.

### Modified Capabilities

- `ci-workflow-maintenance`: The image build workflow is a new addition to the
  existing CI pipeline structure established in this capability area.

## Impact

- **Backend services**: `chat-management` and `meeting-management` config files
  changed; all services gain clean env-driven config with no functional behavior
  change.
- **Kubernetes**: All four service base manifests lose inline Secrets; both
  overlays gain new secret files and prod gains patches. `kustomize build`
  output changes for both overlays.
- **Web frontend**: Two components gain startup validation; a new example env
  file is introduced. No runtime behavior changes for correctly configured
  deployments.
- **Android**: Build file gains documentation comments only; no functional
  change.
- **CI/CD**: New workflow file; existing workflows unaffected.
- **Dependencies**: No new library dependencies. Requires `ghcr.io` write
  permissions in GitHub Actions secrets for the image build workflow.
