#!/usr/bin/env bash
set -euo pipefail

# Install PLG Stack (Promtail + Loki + Grafana) via Helm
# OPTIONAL — disabled by default in dev. Run manually if you want monitoring.

echo "==> PLG Stack (Promtail + Loki + Grafana)"
echo "    This is OPTIONAL and disabled by default in dev."
echo "    It consumes significant resources (~512Mi+ RAM)."
echo ""

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
NAMESPACE="monitoring"

read -rp "Install PLG stack? [y/N] " confirm
if [[ "${confirm}" != [yY] ]]; then
    echo "Skipped. Run this script again when you want monitoring."
    exit 0
fi

helm repo add grafana https://grafana.github.io/helm-charts 2>/dev/null || true
helm repo update grafana

echo "==> Installing Loki..."
helm upgrade --install loki grafana/loki \
    --namespace "${NAMESPACE}" \
    --create-namespace \
    -f "${SCRIPT_DIR}/../plg/loki-values.yaml" \
    --wait --timeout 5m

echo "==> Installing Promtail..."
helm upgrade --install promtail grafana/promtail \
    --namespace "${NAMESPACE}" \
    -f "${SCRIPT_DIR}/../plg/promtail-values.yaml" \
    --wait --timeout 5m

echo "==> Installing Grafana..."
helm upgrade --install grafana grafana/grafana \
    --namespace "${NAMESPACE}" \
    -f "${SCRIPT_DIR}/../plg/grafana-values.yaml" \
    --wait --timeout 5m

echo "==> PLG stack installed."
echo "    Access Grafana: kubectl port-forward -n ${NAMESPACE} svc/grafana 3000:80"
echo "    Default admin password: kubectl get secret -n ${NAMESPACE} grafana -o jsonpath='{.data.admin-password}' | base64 -d"
