# Zero Meeting System — Kubernetes Deployment Guide

This guide covers deploying the full Zero Meeting System (ZMS) stack on a k3s
cluster, from building service images through verifying a running deployment.

---

## Table of Contents

1. [Prerequisites](#1-prerequisites)
2. [Build Service Images](#2-build-service-images)
3. [Configure Secrets](#3-configure-secrets)
4. [Install Helm Charts](#4-install-helm-charts)
5. [Deploy Kafka Cluster and Topics](#5-deploy-kafka-cluster-and-topics)
6. [Apply Kustomize Overlay](#6-apply-kustomize-overlay)
7. [Verify Deployment](#7-verify-deployment)
8. [Access Services](#8-access-services)
9. [Architecture Overview](#9-architecture-overview)
10. [Troubleshooting](#10-troubleshooting)

---

## 1. Prerequisites

Install the following tools before proceeding.

| Tool                 | Notes                                                                  |
| -------------------- | ---------------------------------------------------------------------- |
| **k3s**              | Install with `--disable traefik --disable metrics-server`              |
| **helm**             | Required for Strimzi, Kong, LiveKit, and PLG charts                    |
| **kubectl**          | Bundled with k3s; also available standalone                            |
| **kustomize**        | Optional — `kubectl kustomize` / `kubectl apply -k` works as a drop-in |
| **Java 25 + Gradle** | Required only for building service images locally                      |

**Install k3s:**

```bash
curl -sfL https://get.k3s.io | INSTALL_K3S_EXEC="server --disable traefik --disable metrics-server" sh -
```

**Install Helm:**

```bash
curl https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash
```

---

## 2. Build Service Images

ZMS uses Spring Boot Buildpacks (via Gradle) to produce OCI images. Run each
command from the repository root.

```bash
./services/gradlew -p services/ user-management:bootBuildImage
./services/gradlew -p services/ meeting-management:bootBuildImage
./services/gradlew -p services/ chat-management:bootBuildImage
./services/gradlew -p services/ notification:bootBuildImage
```

By default, images are tagged as:

```text
ghcr.io/phunguy65/zms/<service>:0.0.1-SNAPSHOT
```

These tags match what the base Kustomize manifests reference. If you push images
to a private registry or change the tag, update the image references in the
relevant overlay patch before deploying.

---

## 3. Configure Secrets

Secrets are stored as plain Kubernetes `Secret` manifests under each overlay's
`secrets/` directory. **Never commit real credentials.** Replace every
placeholder value with a real secret before applying.

### Staging overlay — `overlays/staging/secrets/`

<!-- markdownlint-disable MD013 MD060 -->

| File                              | Secret Name                  | Namespace        | Keys                                                                                                                                         |
| --------------------------------- | ---------------------------- | ---------------- | -------------------------------------------------------------------------------------------------------------------------------------------- |
| `user-management-secrets.yaml`    | `user-management-secrets`    | `default`        | `jwt-secret`, `cursor-secret`, `db-username`, `db-password`                                                                                  |
| `meeting-management-secrets.yaml` | `meeting-management-secrets` | `default`        | `db-username`, `db-password`, `cursor-secret`, `livekit-api-key`, `livekit-api-secret`, `recording-s3-access-key`, `recording-s3-secret-key` |
| `chat-management-secrets.yaml`    | `chat-management-secrets`    | `default`        | `livekit-api-key`, `livekit-api-secret`, `chat-jwt-secret`                                                                                   |
| `notification-secrets.yaml`       | `notification-secrets`       | `default`        | `resend-api-key`                                                                                                                             |
| `livekit-secrets.yaml`            | `livekit-secrets`            | `livekit-system` | `api-key`, `api-secret`                                                                                                                      |
| `rustfs-secrets.yaml`             | `rustfs-credentials`         | `rustfs-system`  | `access-key`, `secret-key`                                                                                                                   |

<!-- markdownlint-enable MD013 MD060 -->

### Prod overlay — `overlays/prod/secrets/`

The prod overlay uses the same file names and secret names as dev. Supply
production-strength values for every key listed in the table above.

> The `livekit-api-key` / `livekit-api-secret` pair must match across
> `meeting-management-secrets`, `chat-management-secrets`, and `livekit-secrets`
> in the same overlay.

---

## 4. Install Helm Charts

Convenience scripts are provided in `services/k8s/helm/`. Run them from the
repository root.

### 4.1 Strimzi Kafka Operator (namespace: `kafka`)

```bash
bash services/k8s/helm/install-strimzi.sh
```

### 4.2 Kong Gateway Operator (namespace: `kong-system`)

```bash
bash services/k8s/helm/install-kong.sh
```

### 4.3 LiveKit Server and Egress (namespace: `livekit-system`)

```bash
bash services/k8s/helm/install-livekit.sh
```

### 4.4 PLG Stack — Prometheus, Loki, Grafana (namespace: `monitoring`) — optional

```bash
bash services/k8s/helm/install-plg.sh
```

---

## 5. Deploy Kafka Cluster and Topics

Apply the KRaft-mode Kafka cluster first, wait for it to become ready, then
apply the topic definitions.

```bash
kubectl apply -f services/k8s/kafka/kafka-cluster.yaml
kubectl wait kafka/zms-kafka --for=condition=Ready --namespace=kafka --timeout=300s
kubectl apply -f services/k8s/kafka/kafka-topics.yaml
```

The cluster runs as a single combined node (controller + broker). Topics span
the `user`, `meeting`, and `chat` domains (16 topics total).

---

## 6. Apply Kustomize Overlay

### Staging

```bash
kubectl apply -k services/k8s/overlays/staging
```

Alternative using standalone kustomize:

```bash
kustomize build services/k8s/overlays/staging | kubectl apply -f -
```

### Prod

```bash
kubectl apply -k services/k8s/overlays/prod
```

### Overlay differences

| Aspect            | Staging                     | Prod                          |
| ----------------- | --------------------------- | ----------------------------- |
| Resource budget   | ~4.5 Gi RAM total           | 512 Mi – 2 Gi per service     |
| Replicas          | 1 per service               | 2 per service                 |
| Storage class     | `local-path` (k3s built-in) | Default cluster storage class |
| Image pull policy | `IfNotPresent`              | `Always`                      |
| Kong LoadBalancer | NodePort / ClusterIP        | MetalLB IP pool               |

### Automated staging setup

The `dev-setup.sh` script runs steps 4 through 6 (Helm installs, Kafka
deployment, and staging overlay) in the correct order with readiness checks:

```bash
bash services/k8s/scripts/dev-setup.sh
```

---

## 7. Verify Deployment

Check that all pods are running:

```bash
kubectl get pods -A
```

Wait for each application deployment to finish rolling out:

```bash
kubectl rollout status deployment/user-management --timeout=300s
kubectl rollout status deployment/meeting-management --timeout=300s
kubectl rollout status deployment/chat-management --timeout=300s
kubectl rollout status deployment/notification --timeout=300s
```

Check StatefulSets:

```bash
kubectl rollout status statefulset/chat-mongo --timeout=300s
kubectl rollout status statefulset/valkey --timeout=300s
```

---

## 8. Access Services

Use port-forward to reach cluster-internal services from your local machine.

### Kong API Gateway (entry point for all service APIs)

```bash
kubectl port-forward -n kong-system svc/kong-gateway-kong-proxy 8000:80
```

APIs are then reachable at `http://localhost:8000/<route-prefix>`.

### LiveKit Server (WebRTC signalling)

```bash
kubectl port-forward -n livekit-system svc/livekit-server 7880:7880
```

### Grafana (if PLG stack is installed)

```bash
kubectl port-forward -n monitoring svc/grafana 3000:80
```

---

## 9. Architecture Overview

```text
┌──────────────────────────────────────────────────────────────────────┐
│ Cluster                                                              │
│                                                                      │
│  ┌─────────────────── namespace: kong-system ──────────────────┐    │
│  │  Kong Gateway  ←─── external traffic                        │    │
│  │  (LoadBalancer / port-forward)                              │    │
│  └──────────┬──────────────────────────────────────────────────┘    │
│             │ routes by path prefix                                  │
│             ▼                                                        │
│  ┌─────────────────── namespace: default ──────────────────────┐    │
│  │                                                             │    │
│  │  user-management ──gRPC──► meeting-management              │    │
│  │       │                         │                          │    │
│  │       │         Kafka (async)   │                          │    │
│  │       └──publish──┬─────────────┘                          │    │
│  │                   │                                        │    │
│  │  chat-management ◄┤◄─ consume                              │    │
│  │  notification    ◄┘◄─ consume                              │    │
│  │                                                             │    │
│  │  user-postgres     meeting-postgres     chat-mongo          │    │
│  │  (Deployment)      (Deployment)         (StatefulSet)       │    │
│  │                                                             │    │
│  │  valkey (StatefulSet) ◄── meeting-management (join state,   │    │
│  │                               SSE pub/sub)                  │    │
│  └─────────────────────────────────────────────────────────────┘    │
│                                                                      │
│  ┌──── namespace: livekit-system ────┐                              │
│  │  livekit-server   livekit-egress  │                              │
│  │  livekit-redis (internal pub/sub) │                              │
│  └───────────────────────────────────┘                              │
│                                                                      │
│  ┌──── namespace: rustfs-system ─────┐                              │
│  │  rustfs (S3-compatible storage)   │                              │
│  │  Used for meeting recordings      │                              │
│  └───────────────────────────────────┘                              │
│                                                                      │
│  ┌──── namespace: kafka ─────────────┐                              │
│  │  zms-kafka (KRaft, 1 node)        │                              │
│  │  Strimzi operator                 │                              │
│  └───────────────────────────────────┘                              │
└──────────────────────────────────────────────────────────────────────┘
```

**Communication patterns:**

- **External → Kong**: all client traffic enters through the Kong Gateway
- **user-management → meeting-management**: synchronous gRPC (user lookups)
- **user-management, meeting-management → Kafka**: publish domain events
- **chat-management, notification → Kafka**: consume domain events
- **meeting-management → Valkey**: join-request state and SSE fan-out
- **meeting-management, chat-management → LiveKit**: room management and
  real-time media signalling
- **meeting-management → RustFS**: recording object storage via S3 API

---

## 10. Troubleshooting

### Check pod status across all namespaces

```bash
kubectl get pods -A
```

### View logs for a failing pod

```bash
kubectl logs deployment/user-management
kubectl logs deployment/meeting-management
kubectl logs deployment/chat-management
kubectl logs deployment/notification
```

### Describe a pod to see events and resource issues

```bash
kubectl describe pod -l app=user-management
```

### Restart a deployment

```bash
kubectl rollout restart deployment/user-management
```

### Check resource consumption

```bash
kubectl top pods
kubectl top nodes
```

### Kafka connectivity check

Confirm the Kafka cluster is in `Ready` state before applying topics or starting
services:

```bash
kubectl get kafka -n kafka
kubectl describe kafka zms-kafka -n kafka
```

### Kong route not working

Verify the GatewayClass and Gateway are accepted, then check HTTPRoute status:

```bash
kubectl get gatewayclass
kubectl get gateway -n kong-system
kubectl get httproute -A
```
