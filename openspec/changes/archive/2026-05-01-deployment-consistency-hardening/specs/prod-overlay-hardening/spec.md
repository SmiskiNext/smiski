# ADDED Requirements

## Requirement: Prod overlay defines resource requests and limits for all services

The prod overlay SHALL include a strategic-merge patch (`prod-resources.yaml`)
that sets CPU and memory requests and limits for all four stateless service
Deployments and all stateful set pods (databases). Resources SHALL be higher
than dev defaults to reflect production workloads.

### Scenario: Prod resource patch applies to all four services

- **WHEN** `kustomize build services/k8s/overlays/prod` is executed
- **THEN** the rendered Deployment for each of user-management,
  meeting-management, chat-management, and notification SHALL include non-empty
  `resources.requests` and `resources.limits` for both CPU and memory

### Scenario: Prod overlay resource values exceed dev defaults

- **WHEN** the prod `prod-resources.yaml` patch is compared to any base manifest
  resource section
- **THEN** the prod CPU and memory limits SHALL be greater than or equal to the
  base/dev values

## Requirement: Prod overlay sets replicas for high availability on stateless services

The prod overlay SHALL include a strategic-merge patch (`prod-replicas.yaml`)
that sets `replicas: 2` for all four stateless service Deployments
(user-management, meeting-management, chat-management, notification). Stateful
services (databases) SHALL retain `replicas: 1`.

### Scenario: Stateless services run two replicas in prod

- **WHEN** `kustomize build services/k8s/overlays/prod` is executed
- **THEN** the rendered Deployment for each stateless service SHALL have
  `spec.replicas` set to `2`

### Scenario: Database StatefulSets are not affected by replica patch

- **WHEN** `kustomize build services/k8s/overlays/prod` is executed
- **THEN** StatefulSet objects for databases SHALL NOT have their replica count
  modified by `prod-replicas.yaml`

## Requirement: Prod overlay forces image pull policy to Always

The prod overlay SHALL include a strategic-merge patch (`prod-images.yaml`) that
sets `imagePullPolicy: Always` on all containers in all four service Deployments
so that new image pushes are reliably picked up during rolling updates.

### Scenario: imagePullPolicy is Always in prod rendered output

- **WHEN** `kustomize build services/k8s/overlays/prod` is executed
- **THEN** every container spec in all four service Deployments SHALL have
  `imagePullPolicy: Always`

## Requirement: MetalLB pool in prod overlay is annotated with IP override requirement

The `services/k8s/overlays/prod/metallb-pool.yaml` file SHALL contain a
prominent comment stating that the IP address range is a local subnet
placeholder that MUST be changed to match the actual production network range
before applying.

### Scenario: metallb-pool.yaml carries a clear override notice

- **WHEN** `services/k8s/overlays/prod/metallb-pool.yaml` is inspected
- **THEN** it SHALL contain a comment indicating the IP pool range is a
  placeholder requiring operator replacement
