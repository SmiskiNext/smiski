# ADDED Requirements

## Requirement: Kubernetes base manifests contain no Secret objects

The Kustomize base layer (`services/k8s/base/`) SHALL NOT contain any Kubernetes
`Secret` objects. All secrets SHALL be defined exclusively within
overlay-specific directories so that no plaintext credential values are
inherited across environments.

### Scenario: kustomize build of base layer renders no Secrets

- **WHEN** `kustomize build services/k8s/base` is executed
- **THEN** the rendered output SHALL contain zero objects with `kind: Secret`

### Scenario: Inline secrets removed from service base manifests

- **WHEN** any of the four service base manifests (user-management.yaml,
  meeting-management.yaml, chat-management.yaml, notification.yaml) is inspected
- **THEN** none SHALL contain a `kind: Secret` document

### Scenario: LiveKit and RustFS secrets are overlay-scoped

- **WHEN** `services/k8s/base/livekit/02-secrets.yaml` and
  `services/k8s/base/rustfs/01-secrets.yaml` are inspected
- **THEN** their Secret content SHALL have been removed and relocated to the
  appropriate overlay directories

## Requirement: Dev overlay provides working development secrets

The dev overlay (`services/k8s/overlays/dev/`) SHALL include a `secrets/`
subdirectory containing Secret YAML files appropriate for local/development use,
with the same connection values previously hardcoded in base manifests.

### Scenario: Dev overlay renders a complete set of secrets

- **WHEN** `kustomize build services/k8s/overlays/dev` is executed
- **THEN** the rendered output SHALL include Secret objects for all four
  services plus LiveKit and RustFS

### Scenario: Dev overlay kustomization references its secret files

- **WHEN** `services/k8s/overlays/dev/kustomization.yaml` is inspected
- **THEN** it SHALL list all files under `secrets/` in its resources or
  generators section

## Requirement: Prod overlay provides clearly-annotated placeholder secrets

The prod overlay (`services/k8s/overlays/prod/`) SHALL include a `secrets/`
subdirectory containing Secret YAML files where all sensitive values are
replaced with the literal string `MUST_CHANGE_IN_PRODUCTION`, making it obvious
that these values require operator replacement before any production apply.

### Scenario: Prod overlay secrets are annotated as requiring override

- **WHEN** any file under `services/k8s/overlays/prod/secrets/` is inspected
- **THEN** every sensitive field value SHALL be a clearly recognizable
  placeholder such as `MUST_CHANGE_IN_PRODUCTION`

### Scenario: Prod overlay kustomization references its secret files

- **WHEN** `services/k8s/overlays/prod/kustomization.yaml` is inspected
- **THEN** it SHALL list all files under `secrets/` in its resources or
  generators section

### Scenario: kustomize build of prod overlay succeeds with placeholder values

- **WHEN** `kustomize build services/k8s/overlays/prod` is executed (without
  substituting real values)
- **THEN** the command SHALL succeed and produce valid Kubernetes manifests
  containing the placeholder Secret values
