#!/usr/bin/env bash
set -euo pipefail

# Install LiveKit Server + Egress via Helm
# Requires: helm, kubectl with access to k8s cluster
#
# Egress credentials (api_key, api_secret, s3.*) are intentionally empty in the
# values file. They must be supplied via --set overrides or environment variables.
# This script reads them from the meeting-management-secrets and rustfs-secrets
# Kubernetes Secrets (created by Kustomize base).

NAMESPACE="livekit-system"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SERVER_VALUES="${SCRIPT_DIR}/../base/livekit/03-livekit-values.yaml"
EGRESS_VALUES="${SCRIPT_DIR}/../base/livekit/04-egress-values.yaml"

echo "==> Installing LiveKit Server into namespace: ${NAMESPACE}"

helm repo add livekit https://helm.livekit.io 2>/dev/null || true
helm repo update livekit

helm upgrade --install livekit-server livekit/livekit-server \
    --namespace "${NAMESPACE}" \
    --create-namespace \
    -f "${SERVER_VALUES}" \
    --wait --timeout 5m

echo "==> LiveKit Server installed."

echo "==> Installing LiveKit Egress into namespace: ${NAMESPACE}"

# Read credentials from existing Secrets (must be applied before running this script)
LIVEKIT_API_KEY=$(kubectl get secret meeting-management-secrets -n default -o jsonpath='{.data.livekit-api-key}' 2>/dev/null | base64 -d || echo "zms-livekit-key")
LIVEKIT_API_SECRET=$(kubectl get secret meeting-management-secrets -n default -o jsonpath='{.data.livekit-api-secret}' 2>/dev/null | base64 -d || echo "change-me-livekit-secret-must-be-at-least-32-chars")
S3_ACCESS_KEY=$(kubectl get secret meeting-management-secrets -n default -o jsonpath='{.data.recording-s3-access-key}' 2>/dev/null | base64 -d || echo "rustfs-access-key")
S3_SECRET_KEY=$(kubectl get secret meeting-management-secrets -n default -o jsonpath='{.data.recording-s3-secret-key}' 2>/dev/null | base64 -d || echo "rustfs-secret-key-change-me")

helm upgrade --install livekit-egress livekit/egress \
    --namespace "${NAMESPACE}" \
    -f "${EGRESS_VALUES}" \
    --set "api_key=${LIVEKIT_API_KEY}" \
    --set "api_secret=${LIVEKIT_API_SECRET}" \
    --set "s3.access_key=${S3_ACCESS_KEY}" \
    --set "s3.secret=${S3_SECRET_KEY}" \
    --wait --timeout 5m

echo "==> LiveKit Egress installed."
