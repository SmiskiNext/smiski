#!/usr/bin/env bash
set -euo pipefail

# Staging environment setup script for Zero Meeting System on k3s
# Prerequisites: k3s installed with --disable traefik --disable metrics-server
# Install k3s:
#   curl -sfL https://get.k3s.io | INSTALL_K3S_EXEC="server --disable traefik --disable metrics-server" sh -

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
K8S_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
HELM_DIR="${K8S_DIR}/helm"
KAFKA_DIR="${K8S_DIR}/kafka"
OVERLAY_DIR="${K8S_DIR}/overlays/staging"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

info()  { echo -e "${GREEN}==>${NC} $*"; }
warn()  { echo -e "${YELLOW}==> WARNING:${NC} $*"; }
error() { echo -e "${RED}==> ERROR:${NC} $*" >&2; }

# ─── Pre-flight checks ───────────────────────────────────────────────────────

info "Checking prerequisites..."

if ! command -v kubectl &>/dev/null; then
    error "kubectl not found. Install k3s first:"
    echo "  curl -sfL https://get.k3s.io | INSTALL_K3S_EXEC=\"server --disable traefik --disable metrics-server\" sh -"
    exit 1
fi

if ! kubectl cluster-info &>/dev/null; then
    error "Cannot connect to Kubernetes cluster. Is k3s running?"
    echo "  sudo systemctl start k3s"
    exit 1
fi

if ! command -v helm &>/dev/null; then
    error "helm not found. Install Helm:"
    echo "  curl https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash"
    exit 1
fi

if ! command -v kustomize &>/dev/null; then
    warn "kustomize not found, will use 'kubectl kustomize' instead."
    USE_KUBECTL_KUSTOMIZE=true
else
    USE_KUBECTL_KUSTOMIZE=false
fi

info "All prerequisites met."

# ─── Step 1: Install Helm charts ─────────────────────────────────────────────

info "Step 1/4: Installing Helm charts..."

bash "${HELM_DIR}/install-strimzi.sh"
bash "${HELM_DIR}/install-kong.sh"
bash "${HELM_DIR}/install-livekit.sh"

# PLG is optional — skip in automated setup
info "Skipping PLG stack (optional). Run ${HELM_DIR}/install-plg.sh manually if needed."

# ─── Step 2: Apply Kafka cluster + topics ─────────────────────────────────────

info "Step 2/4: Applying Kafka cluster and topics..."

kubectl apply -f "${KAFKA_DIR}/kafka-cluster.yaml"

# Wait for Kafka to be ready before applying topics
info "Waiting for Kafka cluster to be ready (up to 5 minutes)..."
kubectl wait kafka/zms-kafka \
    --for=condition=Ready \
    --namespace=kafka \
    --timeout=300s 2>/dev/null || {
    warn "Kafka not ready yet. Topics will be applied anyway (operator will reconcile)."
}

kubectl apply -f "${KAFKA_DIR}/kafka-topics.yaml"

# ─── Step 3: Apply Kustomize dev overlay ──────────────────────────────────────

info "Step 3/4: Applying Kustomize staging overlay..."

if [[ "${USE_KUBECTL_KUSTOMIZE:-false}" == "true" ]]; then
    kubectl apply -k "${OVERLAY_DIR}"
else
    kustomize build "${OVERLAY_DIR}" | kubectl apply -f -
fi

# ─── Step 4: Wait for deployments ────────────────────────────────────────────

info "Step 4/4: Waiting for all deployments to be ready (up to 5 minutes)..."

DEPLOYMENTS=(
    "user-management"
    "meeting-management"
    "chat-management"
    "notification"
    "user-postgres"
    "meeting-postgres"
)

ALL_READY=true

for deploy in "${DEPLOYMENTS[@]}"; do
    info "  Waiting for ${deploy}..."
    if ! kubectl rollout status "deployment/${deploy}" \
        --namespace=default \
        --timeout=300s 2>/dev/null; then
        warn "${deploy} did not become ready in time."
        ALL_READY=false
    fi
done

# Wait for StatefulSets
info "  Waiting for chat-mongo..."
kubectl rollout status statefulset/chat-mongo --namespace=default --timeout=300s 2>/dev/null || {
    warn "chat-mongo did not become ready in time."
    ALL_READY=false
}

info "  Waiting for valkey..."
kubectl rollout status statefulset/valkey --namespace=default --timeout=300s 2>/dev/null || {
    warn "valkey did not become ready in time."
    ALL_READY=false
}

# ─── Done ─────────────────────────────────────────────────────────────────────

echo ""
if [[ "${ALL_READY}" == "true" ]]; then
    info "Dev environment is ready!"
else
    warn "Some components did not start in time. Check: kubectl get pods -A"
fi

echo ""
info "Access URLs:"
echo "  Kong proxy:      kubectl port-forward -n kong-system svc/kong-gateway-kong-proxy 8000:80"
echo "  LiveKit:          kubectl port-forward -n livekit-system svc/livekit-server 7880:7880"
echo "  Grafana (if PLG): kubectl port-forward -n monitoring svc/grafana 3000:80"
echo ""
info "Build service images:"
echo "  ./gradlew :services:user-management:bootBuildImage"
echo "  ./gradlew :services:meeting-management:bootBuildImage"
echo "  ./gradlew :services:chat-management:bootBuildImage"
echo "  ./gradlew :services:notification:bootBuildImage"
