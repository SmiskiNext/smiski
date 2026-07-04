#!/usr/bin/env bash
set -euo pipefail

# Install Kong Gateway Operator via Helm
# Requires: helm, kubectl with access to k8s cluster

NAMESPACE="kong-system"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
VALUES_FILE="${SCRIPT_DIR}/../base/kong/02-gateway-operator-values.yaml"

echo "==> Installing Kong Gateway Operator into namespace: ${NAMESPACE}"

helm repo add kong https://charts.konghq.com 2>/dev/null || true
helm repo update kong

helm upgrade --install kong-operator kong/kong-operator \
    --namespace "${NAMESPACE}" \
    --create-namespace \
    -f "${VALUES_FILE}" \
    --wait --timeout 5m

echo "==> Kong Gateway Operator installed. Gateway and routes are managed by Kustomize."
